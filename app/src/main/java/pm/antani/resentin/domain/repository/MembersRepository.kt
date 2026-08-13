package pm.antani.resentin.domain.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.data.db.IsupportEntity
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.dto.BanlistBundleDto
import pm.antani.resentin.net.dto.IsupportChangedDto
import pm.antani.resentin.net.dto.MembersSeededDto
import pm.antani.resentin.net.dto.WhoisBundleDto

class MembersRepository(
    private val connectionManager: ConnectionManager,
    private val db: AppDatabase,
) {
    fun startListening(scope: CoroutineScope) {
        connectionManager.events.onEach { event ->
            when (event) {
                is WsEvent.IsupportChanged -> recordIsupport(event.isupport)
                is WsEvent.MembersSeeded -> recordMembers(event.seeded)
                else -> Unit
            }
        }.launchIn(scope)
    }

    private suspend fun recordIsupport(dto: IsupportChangedDto) {
        val slug = db.networkDao().slugForId(dto.networkId) ?: return
        db.isupportDao().upsert(
            IsupportEntity(slug, AppJson.encodeToString(dto.prefix), AppJson.encodeToString(dto.listModesQueryable)),
        )
    }

    private suspend fun recordMembers(dto: MembersSeededDto) {
        db.memberDao().clear(dto.network, dto.channel)
        db.memberDao().upsertAll(
            dto.members.map { MemberEntity(dto.network, dto.channel, it.nick, AppJson.encodeToString(it.modes)) },
        )
    }

    fun observeMembers(networkSlug: String, channelName: String): Flow<List<MemberEntity>> =
        db.memberDao().observeMembers(networkSlug, channelName)

    /** Mode letter -> sigil for this network (e.g. `{"h":"%","o":"@","v":"+"}`), used to
     * decide which privilege-toggle buttons the server's ircd actually supports. */
    fun observePrefixModes(networkSlug: String): Flow<Map<String, String>> =
        db.isupportDao().observeIsupport(networkSlug).map { entity ->
            entity?.let {
                runCatching {
                    AppJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it.prefixJson)
                }.getOrDefault(emptyMap())
            } ?: emptyMap()
        }

    /** Type-A (list) mode letters this network's ircd answers a `banlist` query for
     * (e.g. `["b","e","I"]`) — published on `isupport_changed`, so it's only known
     * once the network has connected at least once since this client last saw it. */
    fun observeListModesQueryable(networkSlug: String): Flow<List<String>> =
        db.isupportDao().observeIsupport(networkSlug).map { entity ->
            entity?.let {
                runCatching {
                    AppJson.decodeFromString(ListSerializer(String.serializer()), it.listModesQueryableJson)
                }.getOrDefault(emptyList())
            } ?: emptyList()
        }

    suspend fun requestWhois(username: String, networkId: Int, nick: String) {
        connectionManager.sendVerb(
            "grappa:user:$username",
            "whois",
            buildJsonObject {
                put("network_id", networkId)
                put("nick", nick)
            },
        )
    }

    val whoisEvents: Flow<WhoisBundleDto> = connectionManager.events
        .filterIsInstance<WsEvent.WhoisBundle>()
        .map { it.whois }

    /** Queries one of the channel's type-A list modes (`b` bans, `e` exempts, `I`
     * invex, `q`/`z` quiet/restrict) — the reply streams back as a [banlistEvents]
     * bundle, not a push ack. A letter this network doesn't support just never gets
     * a reply (see `NetworksApi` sibling doc); there is no distinct error to surface. */
    suspend fun requestBanlist(username: String, networkId: Int, channel: String, mode: String) {
        connectionManager.sendVerb(
            "grappa:user:$username",
            "banlist",
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
                put("mode", mode)
            },
        )
    }

    val banlistEvents: Flow<BanlistBundleDto> = connectionManager.events
        .filterIsInstance<WsEvent.BanlistBundle>()
        .map { it.bundle }

    suspend fun kick(username: String, networkId: Int, channel: String, nick: String) {
        connectionManager.sendVerb(
            "grappa:user:$username",
            "kick",
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
                put("nick", nick)
                put("reason", "")
            },
        )
    }

    suspend fun ban(username: String, networkId: Int, channel: String, mask: String) {
        connectionManager.sendVerb(
            "grappa:user:$username",
            "ban",
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
                put("mask", mask)
            },
        )
    }

    suspend fun unban(username: String, networkId: Int, channel: String, mask: String) {
        connectionManager.sendVerb(
            "grappa:user:$username",
            "unban",
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
                put("mask", mask)
            },
        )
    }

    suspend fun op(username: String, networkId: Int, channel: String, nick: String) =
        nickListVerb(username, "op", networkId, channel, nick)

    suspend fun deop(username: String, networkId: Int, channel: String, nick: String) =
        nickListVerb(username, "deop", networkId, channel, nick)

    suspend fun voice(username: String, networkId: Int, channel: String, nick: String) =
        nickListVerb(username, "voice", networkId, channel, nick)

    suspend fun devoice(username: String, networkId: Int, channel: String, nick: String) =
        nickListVerb(username, "devoice", networkId, channel, nick)

    private suspend fun nickListVerb(username: String, verb: String, networkId: Int, channel: String, nick: String) {
        connectionManager.sendVerb(
            "grappa:user:$username",
            verb,
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
                put("nicks", buildJsonArray { add(JsonPrimitive(nick)) })
            },
        )
    }

    /** Raw verbatim MODE line — used for privilege sigils that have no dedicated verb
     * (halfop, owner, admin/protect), unlike op/deop/voice/devoice above. */
    suspend fun setMode(username: String, networkId: Int, target: String, modes: String, params: List<String>) {
        connectionManager.sendVerb(
            "grappa:user:$username",
            "mode",
            buildJsonObject {
                put("network_id", networkId)
                put("target", target)
                put("modes", modes)
                put("params", buildJsonArray { params.forEach { add(JsonPrimitive(it)) } })
            },
        )
    }

    suspend fun openQueryWindow(username: String, networkId: Int, targetNick: String) {
        connectionManager.sendVerb(
            "grappa:user:$username",
            "open_query_window",
            buildJsonObject {
                put("network_id", networkId)
                put("target_nick", targetNick)
            },
        )
    }

    /** Closes a DM window server-side (deletes the `query_windows` row) — the server
     * then re-broadcasts `query_windows_list`, which [NetworksRepository] already
     * listens for to drop the local `channels` row (source="query"). Local removal is
     * NOT done optimistically here so a failed push (e.g. WS not connected) doesn't
     * desync the two. */
    suspend fun closeQueryWindow(username: String, networkId: Int, targetNick: String) {
        connectionManager.sendVerb(
            "grappa:user:$username",
            "close_query_window",
            buildJsonObject {
                put("network_id", networkId)
                put("target_nick", targetNick)
            },
        )
    }
}
