package pm.antani.resentin.domain.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
        db.isupportDao().upsert(IsupportEntity(slug, AppJson.encodeToString(dto.prefix)))
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
}
