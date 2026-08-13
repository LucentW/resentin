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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import pm.antani.resentin.MainActivity
import pm.antani.resentin.R
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.repository.ChatRepository
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.domain.session.OpenChat
import pm.antani.resentin.domain.session.OpenChatTracker
import pm.antani.resentin.irc.isQueryTarget
import pm.antani.resentin.net.auth.TokenStore
import pm.antani.resentin.net.dto.ScrollbackMessageDto
import pm.antani.resentin.net.ws.SocketState

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
    private val chatRepository: ChatRepository,
    private val appPreferences: AppPreferences,
    private val tokenStore: TokenStore,
) {
    fun startListening(scope: CoroutineScope) {
        connectionManager.events
            .filterIsInstance<WsEvent.MessageReceived>()
            .onEach { handle(it.message) }
            .launchIn(scope)
    }

    /** Entry point for a UnifiedPush wake-up (see `UnifiedPushService.onMessage`): the
     * push payload only carries a title/body/tag/url summary — server-picked, unlocalized
     * strings per `Grappa.Push.Payload`'s moduledoc — with no message id to hang a
     * reply/mark-read action off of. This backfills the conversation via REST first and
     * notifies off the freshly-synced row instead, reusing the exact same action
     * machinery as a live WS-delivered notification. [channelName] is already the
     * bucket-normalized name (partner nick for a DM) — see `Grappa.Push.Payload`'s
     * `deep_link_target`, which mirrors `ChatRepository.queryBucket` on the server side. */
    suspend fun notifyFromPush(networkSlug: String, channelName: String) {
        chatRepository.backfill(networkSlug, channelName)
            .onFailure { Log.w(TAG, "backfill failed for push wake-up on $networkSlug/$channelName", it) }
        val nick = db.networkDao().observeNetwork(networkSlug).first()?.nick
        if (nick == null) {
            Log.d(TAG, "no cached nick for network=$networkSlug, dropping push wake-up")
            return
        }
        val latest = db.messageDao().latestMessage(networkSlug, channelName) ?: return
        val message = ScrollbackMessageDto(
            id = latest.id,
            network = networkSlug,
            channel = channelName,
            serverTime = latest.serverTime,
            kind = latest.kind,
            sender = latest.sender,
            body = latest.body,
        )
        val bucket = queryBucket(message, nick)
        if (!shouldNotify(message, openChatTracker.current.value, nick, bucket)) return
        postNotification(message, bucket)
    }

    /** Fallback for a push this client received but could not decrypt (see
     * `UnifiedPushService.onMessage`) — currently expected, since the server's
     * `web_push_elixir` dependency emits the pre-RFC8291 "aesgcm" draft, which no
     * spec-compliant UnifiedPush distributor can decode; tracked as a to-fix on the
     * grappa-irc side. Deliberately dirty: since the payload is unreadable we don't know
     * WHICH conversation woke us, so instead of staying blind until the app is next
     * foregrounded, this backfills every channel/query this client already knows about
     * (queries live in the same `channels` table, `source="query"`) and evaluates each
     * newly-arrived row against the ordinary [shouldNotify] gate — same notifications a
     * live WS delivery would have produced, just discovered a beat late. Records the
     * failure so Settings can show a quiet note; the note is why this exists at all
     * instead of a plain `return`.
     *
     * Live-observed failure mode this retry exists for: the push wakes the process
     * before the device's network stack has caught up from Doze/WLAN-suspend, so
     * EVERY channel in the first pass fails with UnknownHostException within
     * milliseconds — exactly the scenario this fallback is supposed to cover, defeated
     * by bad timing rather than a real outage. One retry after a short delay is cheap
     * and harmless either way: `backfill` is idempotent (cursor is `after: maxId`).
     *
     * Live-observed gap the WS discovery step exists for: a first-ever DM from a nick
     * this client has never seen — or a query window closed on another client (cic) —
     * has no row in the local `channels` table yet, so the REST sweep below skips
     * right past it; there's no REST endpoint for the query-windows list, only the WS
     * event `query_windows_list`. Rather than staying blind to that case, this briefly
     * joins the per-user topic to receive it before sweeping. */
    suspend fun notifyFromUndecryptablePush() {
        appPreferences.setPushDecryptionFailureAt(System.currentTimeMillis())
        discoverQueryWindows()
        if (sweepForNotifications() > 0) return
        Log.w(TAG, "catch-up sweep reached zero channels, retrying once after a delay")
        delay(5_000)
        sweepForNotifications()
    }

    /** Joins the per-user topic just long enough to receive `query_windows_list` —
     * `NetworksRepository` (subscribed since app start) persists it to the `channels`
     * table as a side effect, which is all [sweepForNotifications] needs. Leaves the
     * connection exactly as it found it: doesn't disconnect a socket that was already
     * open (foreground, or `stayConnected`), so it can't fight `AppContainer`'s own
     * foreground-based connect/disconnect logic. */
    private suspend fun discoverQueryWindows() {
        val session = tokenStore.session.value ?: return
        val wasOpen = connectionManager.state.value == SocketState.OPEN
        if (!wasOpen) {
            runCatching { connectionManager.connect() }
                .onFailure {
                    Log.w(TAG, "discovery connect failed, skipping query-window discovery", it)
                    return
                }
        }
        runCatching { connectionManager.joinChannel("grappa:user:${session.username}") }
            .onFailure { Log.w(TAG, "failed to join per-user topic for query-window discovery", it) }
        withTimeoutOrNull(6_000) {
            connectionManager.events.filterIsInstance<WsEvent.QueryWindowsListReceived>().first()
        }
        // NetworksRepository's own collector receives the same broadcast — give its DB
        // write a moment to land before the sweep below reads the channels table.
        delay(300)
        if (!wasOpen && !appPreferences.stayConnected.first()) {
            connectionManager.disconnect()
        }
    }

    /** One pass over every known channel/query; returns how many were actually
     * reachable (backfill didn't throw), regardless of whether any produced a
     * notification — the signal [notifyFromUndecryptablePush] retries on is "the
     * network wasn't up at all", not "nothing new happened". */
    private suspend fun sweepForNotifications(): Int {
        var reachable = 0
        val networks = db.networkDao().observeNetworksWithChannels().first()
        for (nwc in networks) {
            val nick = nwc.network.nick
            for (channel in nwc.channels.filter { it.joined }) {
                val beforeMaxId = db.messageDao().maxId(nwc.network.slug, channel.name) ?: 0
                val result = chatRepository.backfill(nwc.network.slug, channel.name)
                result.onFailure { Log.w(TAG, "catch-up backfill failed for ${nwc.network.slug}/${channel.name}", it) }
                if (result.isSuccess) reachable++
                val newRows = db.messageDao().messagesAfter(nwc.network.slug, channel.name, beforeMaxId)
                for (row in newRows) {
                    val message = ScrollbackMessageDto(
                        id = row.id,
                        network = nwc.network.slug,
                        channel = channel.name,
                        serverTime = row.serverTime,
                        kind = row.kind,
                        sender = row.sender,
                        body = row.body,
                    )
                    val bucket = queryBucket(message, nick)
                    if (shouldNotify(message, openChatTracker.current.value, nick, bucket)) {
                        postNotification(message, bucket)
                    }
                }
            }
        }
        return reachable
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
            .setContentTitle(context.getString(R.string.notif_content_title, message.sender, bucket))
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
        val replyLabel = context.getString(R.string.notif_action_reply)
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel(replyLabel)
            .build()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            message.id.toInt() * 10 + 1,
            actionIntent(NotificationActionReceiver.ACTION_REPLY, message, bucket),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, replyLabel, pendingIntent)
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
        val label = context.getString(R.string.notif_action_mark_read)
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, label, pendingIntent).build()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_messages),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
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
