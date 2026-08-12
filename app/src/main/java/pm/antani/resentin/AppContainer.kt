package pm.antani.resentin

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.data.db.NetworkWithChannels
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.repository.AuthRepository
import pm.antani.resentin.domain.repository.ChatRepository
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
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
    val authRepository = AuthRepository(tokenStore, database, appPreferences)
    val networksRepository = NetworksRepository(authRepository, database)
    val chatRepository = ChatRepository(authRepository, database)
    val connectionManager = ConnectionManager(tokenStore)
    val membersRepository = MembersRepository(connectionManager, database)
    val userSettingsRepository = UserSettingsRepository(authRepository)
    val openChatTracker = OpenChatTracker()
    val pendingShareHolder = PendingShareHolder()
    private val notificationRouter = NotificationRouter(context.applicationContext, connectionManager, database, openChatTracker)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        chatRepository.startListening(connectionManager, appScope)
        membersRepository.startListening(appScope)
        networksRepository.startListening(connectionManager, appScope)
        notificationRouter.startListening(appScope)

        appScope.launch {
            tokenStore.session.filterNotNull().collect {
                runCatching { connectionManager.connect() }
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
