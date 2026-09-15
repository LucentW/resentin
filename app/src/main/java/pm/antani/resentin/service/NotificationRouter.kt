package pm.antani.resentin.service

import android.Manifest
import android.graphics.BitmapFactory
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
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import pm.antani.resentin.MainActivity
import pm.antani.resentin.R
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.repository.AuthRepository
import pm.antani.resentin.domain.repository.ChatRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.domain.repository.PendingDccOffer
import pm.antani.resentin.domain.repository.PendingInvite
import pm.antani.resentin.domain.repository.UserSettingsRepository
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.domain.session.OpenChat
import pm.antani.resentin.domain.session.OpenChatTracker
import pm.antani.resentin.irc.containsMention
import pm.antani.resentin.irc.isQueryTarget
import pm.antani.resentin.irc.canonicalTarget
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
fun queryBucket(message: ScrollbackMessageDto, myNick: String): String = if (canonicalTarget(message.channel) == canonicalTarget(myNick)) canonicalTarget(message.sender) else canonicalTarget(message.channel)

fun shouldNotify(message: ScrollbackMessageDto, openChat: OpenChat?, myNick: String, bucket: String): Boolean {
    if (message.sender.equals(myNick, ignoreCase = true)) return false
    if (message.kind !in NOTIFIABLE_KINDS) return false
    val body = message.body ?: return false

    if (openChat != null && openChat.networkSlug == message.network && canonicalTarget(openChat.channelName) == bucket) {
        return false
    }

    // A DM is addressed to us in full regardless of its text — unlike a channel, there's
    // no "mention" concept to gate on, or every query message would be silently dropped
    // (the bug this fixes: a plain "ciao!" DM never contains the recipient's nick).
    if (isQueryTarget(bucket)) return true

    return containsMention(body, myNick)
}

class NotificationRouter(
    private val context: Context,
    private val connectionManager: ConnectionManager,
    private val db: AppDatabase,
    private val openChatTracker: OpenChatTracker,
    private val chatRepository: ChatRepository,
    private val appPreferences: AppPreferences,
    private val userSettingsRepository: UserSettingsRepository,
    private val tokenStore: TokenStore,
    private val authRepository: AuthRepository,
    private val networksRepository: NetworksRepository,
) {
    // Guards notifyFromUndecryptablePush against overlapping runs — live-observed: a
    // sequential 27-channel sweep took long enough (~10s) that the distributor (ntfy)
    // apparently timed out waiting for an ack and redelivered the same push, firing a
    // second full sweep concurrently with the first (every request in logcat appeared
    // in duplicate, back to back). A second pass finding the same conversations already
    // caught up is harmless on its own; the actual fix is sweepForNotifications now
    // running channels in parallel instead of one at a time, which this mutex backs up
    // by skipping the redundant second call outright rather than doing the work twice.
    private val sweepMutex = Mutex()

    fun startListening(scope: CoroutineScope) {
        connectionManager.events
            .filterIsInstance<WsEvent.MessageReceived>()
            .onEach { handle(it.message) }
            .launchIn(scope)

        // "avviso" for an inbound INVITE (#976 client parity) — see
        // NetworksRepository.newInvites for the reconnect-dedup this relies on.
        networksRepository.newInvites
            .onEach { postInviteNotification(it) }
            .launchIn(scope)

        // Same "nothing is silent" parity for a held DCC offer (issue 2089).
        networksRepository.newDccOffers
            .onEach { postDccOfferNotification(it) }
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
        runCatching { userSettingsRepository.refreshNotificationPrefs() }
        if (isMuted(networkSlug, channelName)) return
        chatRepository.backfill(networkSlug, channelName)
            .onFailure { Log.w(TAG, "backfill failed for push wake-up on $networkSlug/$channelName", it) }
        val nick = db.networkDao().observeNetwork(networkSlug).first()?.nick
        if (nick == null) {
            Log.d(TAG, "no cached nick for network=$networkSlug, dropping push wake-up")
            return
        }
        val latest = db.messageDao().latestMessage(networkSlug, canonicalTarget(channelName)) ?: return
        val message = ScrollbackMessageDto(
            id = latest.id,
            network = networkSlug,
            channel = canonicalTarget(channelName),
            serverTime = latest.serverTime,
            kind = latest.kind,
            sender = latest.sender,
            body = latest.body,
        )
        val bucket = queryBucket(message, nick)
        if (!shouldNotify(message, openChatTracker.current.value, nick, bucket)) return
        postNotification(message, bucket, nick)
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
        if (!sweepMutex.tryLock()) {
            Log.d(TAG, "a catch-up sweep is already running (likely a distributor redelivery), skipping")
            return
        }
        try {
            appPreferences.setPushDecryptionFailureAt(System.currentTimeMillis())
            discoverQueryWindows()
            if (sweepForNotifications() > 0) return
            Log.w(TAG, "catch-up sweep reached zero channels, retrying once after a delay")
            delay(5_000)
            sweepForNotifications()
        } finally {
            sweepMutex.unlock()
        }
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
        runCatching { connectionManager.joinChannel("grappa:user:${session.wsSubject}") }
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
     * network wasn't up at all", not "nothing new happened".
     *
     * Channels run in parallel, not sequentially: a 27-channel sequential sweep
     * live-measured at ~10 seconds, long enough that ntfy apparently gave up waiting
     * for an ack and redelivered the same push mid-sweep (see [sweepMutex]) — plausibly
     * also long enough to run into Android's background-execution time limits, which
     * would explain the live-observed `SocketException: Software caused connection
     * abort` mid-request. OkHttp's own dispatcher already caps concurrent requests per
     * host (5 by default), so firing every channel's backfill at once just lets OkHttp
     * pipeline them instead of this loop serializing what didn't need to be serial. */
    private suspend fun sweepForNotifications(): Int = coroutineScope {
        runCatching { userSettingsRepository.refreshNotificationPrefs() }
        val networks = db.networkDao().observeNetworksWithChannels().first()
        networks.flatMap { nwc -> nwc.channels.filter { it.joined }.map { nwc.network to it } }
            .map { (network, channel) -> async { sweepChannel(network.slug, channel.name, network.nick) } }
            .awaitAll()
            .count { it }
    }

    /** Backfills one channel/query and notifies for whatever's new; returns whether the
     * backfill itself was reachable (see [sweepForNotifications]). Muted chats are
     * skipped without backfilling at all — nothing to surface means nothing to fetch,
     * which also saves the wake-up's battery budget. */
    private suspend fun sweepChannel(networkSlug: String, channelName: String, nick: String): Boolean {
        if (isMuted(networkSlug, channelName)) return true
        val canonicalChannelName = canonicalTarget(channelName)
        val beforeMaxId = db.messageDao().maxId(networkSlug, canonicalChannelName) ?: 0
        val result = chatRepository.backfill(networkSlug, channelName)
        result.onFailure { Log.w(TAG, "catch-up backfill failed for $networkSlug/$channelName", it) }
        val newRows = db.messageDao().messagesAfter(networkSlug, canonicalChannelName, beforeMaxId)
        for (row in newRows) {
            val message = ScrollbackMessageDto(
                id = row.id,
                network = networkSlug,
                channel = canonicalChannelName,
                serverTime = row.serverTime,
                kind = row.kind,
                sender = row.sender,
                body = row.body,
            )
            val bucket = queryBucket(message, nick)
            if (shouldNotify(message, openChatTracker.current.value, nick, bucket)) {
                postNotification(message, bucket, nick)
            }
        }
        return result.isSuccess
    }

    private suspend fun handle(message: ScrollbackMessageDto) {
        val nick = db.networkDao().observeNetwork(message.network).first()?.nick
        if (nick == null) {
            Log.d(TAG, "no cached nick for network=${message.network}, dropping ${message.id}")
            return
        }
        val bucket = queryBucket(message, nick)
        if (isMuted(message.network, bucket)) return
        val notify = shouldNotify(message, openChatTracker.current.value, nick, bucket)
        Log.d(
            TAG,
            "message #${message.id} kind=${message.kind} from=${message.sender} channel=${message.channel} " +
                "bucket=$bucket myNick=$nick openChat=${openChatTracker.current.value} -> notify=$notify",
        )
        if (!notify) return
        postNotification(message, bucket, nick)
    }

    /** DM partner avatar (M3b), if this app already learned one for [bucket] on
     * [networkSlug] — see `NetworksRepository`'s `whois_bundle`/`whois_avatar_ready`
     * listeners, which populate the same query [pm.antani.resentin.data.db.ChannelEntity]
     * row this reads. Best-effort: any failure (no cached URL, fetch error, decode
     * error) just leaves the notification without a sender icon, never blocks it. */
    private suspend fun senderIcon(networkSlug: String, bucket: String): IconCompat? {
        val url = db.channelDao().getAvatarUrl(networkSlug, bucket) ?: return null
        val bytes = authRepository.fetchBytes(url) ?: return null
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return null
        return IconCompat.createWithBitmap(bitmap)
    }

    /** One notification per conversation (not per message): reuses the same
     * [NotificationCompat.MessagingStyle], appending each new message, so several
     * messages from the same person show as a single growing thread — like every other
     * messenger — instead of stacking a separate notification per message under
     * `setGroup`'s collapsed header. */
    private suspend fun postNotification(message: ScrollbackMessageDto, bucket: String, myNick: String) {
        ensureChannel()
        val conversationId = conversationNotificationId(message.network, bucket)
        val intent = Intent(context, MainActivity::class.java).apply {
            // Redundant with the constructor above, but CodeQL's Kotlin extractor doesn't
            // track the (Context, Class) constructor's second argument, so it can't prove
            // this Intent is explicit from that alone and flags it as a spoofable implicit
            // PendingIntent target (java/android/implicit-pendingintents) — a known false
            // positive (github/codeql#20153). setClass() IS recognised by its dataflow.
            setClass(context, MainActivity::class.java)
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_NETWORK_SLUG, message.network)
            putExtra(EXTRA_CHANNEL_NAME, bucket)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            conversationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = existingMessagingStyle(conversationId)
            ?: NotificationCompat.MessagingStyle(Person.Builder().setName(myNick).build())
        style.conversationTitle = bucket
        // A DM's bucket IS the partner's nick (queryBucket), which doubles as the key
        // NetworksRepository caches their avatar under; a channel message's bucket is
        // the channel name, which was never avatar-tracked, so this simply misses.
        val isDm = canonicalTarget(message.channel) == canonicalTarget(myNick)
        val senderIcon = if (isDm) senderIcon(message.network, bucket) else null
        val sender = Person.Builder().setName(message.sender).apply { senderIcon?.let(::setIcon) }.build()
        style.addMessage(message.body, message.serverTime, sender)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setStyle(style)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(bucket)
            .addAction(replyAction(message, bucket, conversationId))
            .addAction(markReadAction(message, bucket, conversationId))
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "POST_NOTIFICATIONS not granted, dropping notification for #${message.id}")
            return
        }
        Log.d(TAG, "posting notification for #${message.id}")
        NotificationManagerCompat.from(context).notify(conversationId, notification)
    }

    /** Plain (non-conversation) notification for an inbound INVITE — tapping just opens
     * the app, where the Home banner (Join/Decline) is waiting; no inline actions here,
     * unlike [postNotification], since accepting is a full JOIN round-trip better done
     * from the app's own loading/error affordances than a fire-and-forget broadcast. */
    private fun postInviteNotification(invite: PendingInvite) {
        ensureInviteChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            // Redundant with the constructor above, but CodeQL's Kotlin extractor doesn't
            // track the (Context, Class) constructor's second argument, so it can't prove
            // this Intent is explicit from that alone and flags it as a spoofable implicit
            // PendingIntent target (java/android/implicit-pendingintents) — a known false
            // positive (github/codeql#20153). setClass() IS recognised by its dataflow.
            setClass(context, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val notificationId = "invite/${invite.networkSlug}/${invite.channel}".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, INVITE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_invite_title))
            .setContentText(context.getString(R.string.notif_invite_body, invite.inviter, invite.channel))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /** Deep-links into the offer's own channel (unlike [postInviteNotification]'s plain
     * app-open, since here there's already an open, joined window to land on — even a
     * stranger's offer routes to `$server`, which is always a valid destination). */
    private fun postDccOfferNotification(offer: PendingDccOffer) {
        ensureInviteChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            // Redundant with the constructor above, but CodeQL's Kotlin extractor doesn't
            // track the (Context, Class) constructor's second argument, so it can't prove
            // this Intent is explicit from that alone and flags it as a spoofable implicit
            // PendingIntent target (java/android/implicit-pendingintents) — a known false
            // positive (github/codeql#20153). setClass() IS recognised by its dataflow.
            setClass(context, MainActivity::class.java)
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_NETWORK_SLUG, offer.networkSlug)
            putExtra(EXTRA_CHANNEL_NAME, offer.channel)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val notificationId = "dcc_offer/${offer.offerId}".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, INVITE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_dcc_offer_title))
            .setContentText(context.getString(R.string.notif_dcc_offer_body, offer.from, offer.filename))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun conversationNotificationId(networkSlug: String, bucket: String): Int =
        "$networkSlug/$bucket".hashCode()

    /** Recovers prior messages from the currently-posted notification for this
     * conversation (if any) so a new message is appended to the existing thread instead
     * of starting a fresh one-message style each time. */
    private fun existingMessagingStyle(conversationId: Int): NotificationCompat.MessagingStyle? {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = manager.activeNotifications.firstOrNull { it.id == conversationId } ?: return null
        return NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(active.notification)
    }

    private fun actionIntent(action: String, message: ScrollbackMessageDto, bucket: String, notificationId: Int): Intent =
        Intent(context, NotificationActionReceiver::class.java).apply {
            // See the same-reasoned setClass() call in postNotification() above.
            setClass(context, NotificationActionReceiver::class.java)
            setAction(action)
            putExtra(NotificationActionReceiver.EXTRA_NETWORK_SLUG, message.network)
            putExtra(NotificationActionReceiver.EXTRA_CHANNEL_NAME, bucket)
            putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, message.id)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

    /** Inline quick reply — the `PendingIntent` MUST be mutable, or the system has
     * nowhere to attach the [RemoteInput] result before firing it. */
    private fun replyAction(message: ScrollbackMessageDto, bucket: String, notificationId: Int): NotificationCompat.Action {
        val replyLabel = context.getString(R.string.notif_action_reply)
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel(replyLabel)
            .build()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 2,
            actionIntent(NotificationActionReceiver.ACTION_REPLY, message, bucket, notificationId),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, replyLabel, pendingIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun markReadAction(message: ScrollbackMessageDto, bucket: String, notificationId: Int): NotificationCompat.Action {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 2 + 1,
            actionIntent(NotificationActionReceiver.ACTION_MARK_READ, message, bucket, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = context.getString(R.string.notif_action_mark_read)
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, label, pendingIntent).build()
    }

    /** Server muted_targets gate — same mute the channel-settings switch writes, so it
     * holds on every device. Fail-open when never loaded (mirrors the server's own
     * lenient reader). Push paths refresh first (a cold-woken process has a stale
     * cache); the live WS path rides the app-start refresh. */
    private suspend fun isMuted(networkSlug: String, bucket: String): Boolean =
        userSettingsRepository.isMutedNow(networkSlug, bucket)

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_messages),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun ensureInviteChannel() {
        val channel = NotificationChannel(
            INVITE_CHANNEL_ID,
            context.getString(R.string.notif_invite_title),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "messages"
        const val INVITE_CHANNEL_ID = "invites"
        const val EXTRA_NETWORK_SLUG = "network_slug"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        private const val TAG = "NotificationRouter"
    }
}
