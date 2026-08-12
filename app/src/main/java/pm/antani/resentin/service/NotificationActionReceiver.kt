package pm.antani.resentin.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pm.antani.resentin.AppApplication

/** Handles the two notification-shade quick actions `NotificationRouter` attaches to a
 * message notification — "Rispondi" (inline [RemoteInput] reply) and "Segna come letto"
 * (advances the server read-cursor to the notified message) — without ever launching
 * [pm.antani.resentin.MainActivity]. Registered `exported=false`: only this app's own
 * `PendingIntent`s can trigger it. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action} extras=${intent.extras}")
        val networkSlug = intent.getStringExtra(EXTRA_NETWORK_SLUG) ?: return
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: return
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L).takeIf { it > 0 }
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val container = (context.applicationContext as AppApplication).container

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_MARK_READ -> {
                        if (messageId != null) {
                            container.chatRepository.markRead(networkSlug, channelName, messageId)
                                .onFailure { Log.d(TAG, "mark-read failed", it) }
                        }
                    }
                    ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(KEY_REPLY_TEXT)?.toString()?.trim()
                        if (!text.isNullOrEmpty()) {
                            container.chatRepository.sendMessage(networkSlug, channelName, text)
                                .onFailure { Log.d(TAG, "quick reply failed", it) }
                        }
                        if (messageId != null) {
                            container.chatRepository.markRead(networkSlug, channelName, messageId)
                                .onFailure { Log.d(TAG, "mark-read after reply failed", it) }
                        }
                    }
                }
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_READ = "pm.antani.resentin.action.MARK_READ"
        const val ACTION_REPLY = "pm.antani.resentin.action.REPLY"
        const val EXTRA_NETWORK_SLUG = "network_slug"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val KEY_REPLY_TEXT = "reply_text"
        private const val TAG = "NotifActionReceiver"
    }
}
