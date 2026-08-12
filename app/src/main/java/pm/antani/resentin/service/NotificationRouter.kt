package pm.antani.resentin.service

import android.Manifest
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import pm.antani.resentin.MainActivity
import pm.antani.resentin.R
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.domain.session.OpenChat
import pm.antani.resentin.domain.session.OpenChatTracker
import pm.antani.resentin.irc.isQueryTarget
import pm.antani.resentin.net.dto.ScrollbackMessageDto

private val NOTIFIABLE_KINDS = setOf("privmsg", "action", "notice")

/** For a query, `channel` is the raw PRIVMSG target — our own nick when the OTHER party
 * sent it — so it must be normalized to the partner's nick (mirrors
 * ChatRepository.queryBucket) before it's used as a conversation key anywhere: matching
 * the open chat, classifying "is this a DM", the notification title/group, and the
 * tap-through deep link all need the SAME stable key, or e.g. tapping a DM notification
 * would deep-link to a chat named after your own nick. */
fun queryBucket(message: ScrollbackMessageDto, myNick: String): String =
    if (message.channel.equals(myNick, ignoreCase = true)) message.sender else message.channel

fun shouldNotify(message: ScrollbackMessageDto, openChat: OpenChat?, myNick: String, bucket: String): Boolean {
    if (message.sender.equals(myNick, ignoreCase = true)) return false
    if (message.kind !in NOTIFIABLE_KINDS) return false
    val body = message.body ?: return false

    if (openChat != null && openChat.networkSlug == message.network && openChat.channelName == bucket) {
        return false
    }

    // A DM is addressed to us in full regardless of its text — unlike a channel, there's
    // no "mention" concept to gate on, or every query message would be silently dropped
    // (the bug this fixes: a plain "ciao!" DM never contains the recipient's nick).
    if (isQueryTarget(bucket)) return true

    return body.contains(myNick, ignoreCase = true)
}

class NotificationRouter(
    private val context: Context,
    private val connectionManager: ConnectionManager,
    private val db: AppDatabase,
    private val openChatTracker: OpenChatTracker,
) {
    fun startListening(scope: CoroutineScope) {
        connectionManager.events
            .filterIsInstance<WsEvent.MessageReceived>()
            .onEach { handle(it.message) }
            .launchIn(scope)
    }

    private suspend fun handle(message: ScrollbackMessageDto) {
        val nick = db.networkDao().observeNetwork(message.network).first()?.nick
        if (nick == null) {
            Log.d(TAG, "no cached nick for network=${message.network}, dropping ${message.id}")
            return
        }
        val bucket = queryBucket(message, nick)
        val notify = shouldNotify(message, openChatTracker.current.value, nick, bucket)
        Log.d(
            TAG,
            "message #${message.id} kind=${message.kind} from=${message.sender} channel=${message.channel} " +
                "bucket=$bucket myNick=$nick openChat=${openChatTracker.current.value} -> notify=$notify",
        )
        if (!notify) return
        postNotification(message, bucket)
    }

    private fun postNotification(message: ScrollbackMessageDto, bucket: String) {
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_NETWORK_SLUG, message.network)
            putExtra(EXTRA_CHANNEL_NAME, bucket)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            message.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("${message.sender} in $bucket")
            .setContentText(message.body)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(bucket)
            .addAction(replyAction(message, bucket))
            .addAction(markReadAction(message, bucket))
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "POST_NOTIFICATIONS not granted, dropping notification for #${message.id}")
            return
        }
        Log.d(TAG, "posting notification for #${message.id}")
        NotificationManagerCompat.from(context).notify(message.id.toInt(), notification)
    }

    private fun actionIntent(action: String, message: ScrollbackMessageDto, bucket: String): Intent =
        Intent(context, NotificationActionReceiver::class.java).apply {
            setAction(action)
            putExtra(NotificationActionReceiver.EXTRA_NETWORK_SLUG, message.network)
            putExtra(NotificationActionReceiver.EXTRA_CHANNEL_NAME, bucket)
            putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, message.id)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, message.id.toInt())
        }

    /** Inline quick reply — the `PendingIntent` MUST be mutable, or the system has
     * nowhere to attach the [RemoteInput] result before firing it. */
    private fun replyAction(message: ScrollbackMessageDto, bucket: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel("Rispondi")
            .build()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            message.id.toInt() * 10 + 1,
            actionIntent(NotificationActionReceiver.ACTION_REPLY, message, bucket),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, "Rispondi", pendingIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun markReadAction(message: ScrollbackMessageDto, bucket: String): NotificationCompat.Action {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            message.id.toInt() * 10 + 2,
            actionIntent(NotificationActionReceiver.ACTION_MARK_READ, message, bucket),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, "Segna come letto", pendingIntent).build()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Messaggi", NotificationManager.IMPORTANCE_DEFAULT)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "messages"
        const val EXTRA_NETWORK_SLUG = "network_slug"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        private const val TAG = "NotificationRouter"
    }
}
