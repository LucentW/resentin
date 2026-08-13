package pm.antani.resentin

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.data.db.NetworkWithChannels
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.repository.AdminRepository
import pm.antani.resentin.domain.repository.AuthRepository
import pm.antani.resentin.domain.repository.ChatRepository
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.domain.repository.PushRepository
import pm.antani.resentin.domain.repository.UserSettingsRepository
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.domain.session.OpenChatTracker
import pm.antani.resentin.domain.session.PendingShareHolder
import pm.antani.resentin.net.auth.TokenStore
import pm.antani.resentin.net.ws.SocketState
import pm.antani.resentin.service.ConnectionForegroundService
import pm.antani.resentin.service.NotificationRouter

class AppContainer(private val context: Context) {
    val tokenStore = TokenStore(context.applicationContext)
    val database = AppDatabase.build(context)
    val appPreferences = AppPreferences(context.applicationContext)
    val authRepository = AuthRepository(tokenStore, database, appPreferences, context.applicationContext)
    val networksRepository = NetworksRepository(authRepository, database)
    val chatRepository = ChatRepository(authRepository, database, context.applicationContext)
    val connectionManager = ConnectionManager(tokenStore)
    val membersRepository = MembersRepository(connectionManager, database)
    val userSettingsRepository = UserSettingsRepository(authRepository)
    val pushRepository = PushRepository(authRepository, appPreferences)
    val adminRepository = AdminRepository(authRepository)
    val openChatTracker = OpenChatTracker()
    val pendingShareHolder = PendingShareHolder()
    val notificationRouter =
        NotificationRouter(context.applicationContext, connectionManager, database, openChatTracker, chatRepository, appPreferences, tokenStore)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        chatRepository.startListening(connectionManager, appScope)
        membersRepository.startListening(appScope)
        networksRepository.startListening(connectionManager, appScope)
        notificationRouter.startListening(appScope)

        // Battery-friendly sync: the WS stays open only while the app is actually
        // foreground (ProcessLifecycleOwner.currentStateFlow reaches STARTED on the
        // first Activity's onStart and drops below it once the last one stops — the
        // standard whole-app foreground/background signal) OR while the user has
        // explicitly opted into the always-on fallback via `stayConnected`. Backgrounded
        // without that opt-in, this client relies on UnifiedPush (if enabled in
        // Settings) for mention/DM delivery instead of a persistent socket; foreground
        // resume re-triggers the join+backfill effect below via the resulting state
        // transition to OPEN, so nothing is missed.
        appScope.launch {
            combine(
                tokenStore.session.filterNotNull(),
                ProcessLifecycleOwner.get().lifecycle.currentStateFlow,
                appPreferences.stayConnected,
            ) { _, lifecycleState, stayConnected ->
                lifecycleState.isAtLeast(Lifecycle.State.STARTED) || stayConnected
            }
                .distinctUntilChanged()
                .collect { shouldBeConnected ->
                    if (shouldBeConnected) {
                        runCatching { connectionManager.connect() }
                    } else {
                        connectionManager.disconnect()
                    }
                }
        }

        appScope.launch {
            combine(
                tokenStore.session.filterNotNull(),
                connectionManager.state,
                database.networkDao().observeNetworksWithChannels(),
            ) { session, state, networks -> Triple(session, state, networks) }
                // ChannelEntity now carries live-updating fields (unread counts, read
                // cursor, topic) that change on nearly every incoming message — without
                // this, THEIR mutation re-triggers this block, and it would rejoin +
                // fully re-backfill every channel on every network in a near-continuous
                // loop (hammering the server) instead of only when the socket transitions
                // to OPEN or the actual set of topics to join changes (channel/query
                // added or removed).
                .distinctUntilChangedBy { (session, state, networks) -> state to buildTopics(session.username, networks) }
                .collect { (session, state, networks) ->
                    if (state != SocketState.OPEN) return@collect
                    val topics = buildTopics(session.username, networks)
                    runCatching {
                        connectionManager.joinAll(topics) { topic, response ->
                            networksRepository.applyJoinResponse(topic, response)
                        }
                    }
                    networks.forEach { nwc ->
                        nwc.channels.filter { it.joined }.forEach { channel ->
                            runCatching { chatRepository.backfill(nwc.network.slug, channel.name) }
                        }
                    }
                }
        }

        appScope.launch {
            appPreferences.stayConnected.collect { enabled ->
                if (enabled) {
                    ConnectionForegroundService.start(context.applicationContext)
                } else {
                    ConnectionForegroundService.stop(context.applicationContext)
                }
            }
        }

        // Re-confirms the distributor link on every app start, per UnifiedPush's own
        // guidance: if the previously-saved distributor was uninstalled since our last
        // run, this transparently falls back to the OS default (when one is unambiguous)
        // instead of leaving the user silently unregistered. Uses the ack'd-distributor
        // fast path, which doesn't require an Activity context — only first-time setup
        // (AppSettingsScreen, no saved distributor yet) needs one.
        appScope.launch {
            if (appPreferences.unifiedPushEnabled.first()) {
                UnifiedPush.tryUseCurrentOrDefaultDistributor(context.applicationContext) { success ->
                    if (!success) return@tryUseCurrentOrDefaultDistributor
                    appScope.launch {
                        val vapid = pushRepository.fetchVapidPublicKey().getOrNull()
                        runCatching { UnifiedPush.register(context.applicationContext, vapid = vapid) }
                    }
                }
            }
        }

        // The server severs the web session (and revokes the bearer) after sustained
        // abuse — the IRC session itself is untouched, but this client must drop to
        // sign-in, not treat it as a transient network blip.
        appScope.launch {
            connectionManager.events.filterIsInstance<WsEvent.WebSessionSevered>().collect {
                connectionManager.disconnect()
                authRepository.signOut()
            }
        }
    }

    private fun buildTopics(username: String, networks: List<NetworkWithChannels>): List<String> = buildList {
        add("grappa:user:$username")
        networks.forEach { nwc ->
            add("grappa:user:$username/network:${nwc.network.slug}")
            // An incoming DM's `message` event is pushed on the topic keyed by OUR OWN
            // nick, not the sender's — the scrollback `channel` field for that row is
            // literally the raw PRIVMSG target, which for an inbound DM is us (see
            // ChatRepository.queryBucket for the read-side normalization this pairs
            // with). Query windows are joined below by the PARTNER's nick, which only
            // ever carries our own OUTGOING messages — without this own-nick topic, a
            // DM from anyone (new contact or existing query) never arrives live, only
            // via REST backfill, so it's joined unconditionally per network rather than
            // only when a query window happens to already be open.
            //
            // `.lowercase()`: the server casemap-folds a PRIVMSG target before using it
            // as a scrollback/topic key (confirmed live: nick "Lucy" produces
            // `channel: "lucy"` on an inbound DM), but `NetworkEntity.nick` keeps the
            // live display case. We don't track the network's ISUPPORT CASEMAPPING
            // token, so plain ASCII lowercasing is the pragmatic fold — it's what every
            // casemapping variant (ascii/rfc1459/strict-rfc1459) agrees on for ordinary
            // A-Z nicks, which is the case that actually occurs in practice.
            add("grappa:user:$username/network:${nwc.network.slug}/channel:${nwc.network.nick.lowercase()}")
            nwc.channels.filter { it.joined }.forEach { channel ->
                add("grappa:user:$username/network:${nwc.network.slug}/channel:${channel.name}")
            }
        }
    }
}
