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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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
import pm.antani.resentin.net.dto.ScrollbackMessageDto
import pm.antani.resentin.net.dto.WhoisBundleDto

/** Per-user channel-privilege mode letter -> sigil, matching the wire's own
 * (`Grappa.Session.EventRouter.@user_mode_prefixes`) table — kept in sync with
 * [pm.antani.resentin.ui.common.PRIVILEGE_MODES] rather than shared directly, since
 * this is the domain layer and that lives in ui.common. */
private val PRIVILEGE_SIGILS = mapOf('q' to '~', 'a' to '&', 'o' to '@', 'h' to '%', 'v' to '+')

private const val EMPTY_MODES_JSON = "[]"

class MembersRepository(
    private val connectionManager: ConnectionManager,
    private val db: AppDatabase,
) {
    fun startListening(scope: CoroutineScope) {
        connectionManager.events.onEach { event ->
            when (event) {
                is WsEvent.IsupportChanged -> recordIsupport(event.isupport)
                is WsEvent.MembersSeeded -> recordMembers(event.seeded)
                is WsEvent.MessageReceived -> applyPresenceEvent(event.message)
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

    /** Keeps the member table live between `members_seeded` snapshots — without this,
     * the list only ever reflects who was in the channel at join time, going stale as
     * people join/part/quit/get kicked/change nick. The server fans QUIT and NICK
     * out to one scrollback row per channel the nick was in (see grappa's
     * EventRouter), so every kind here is already scoped to a single (network,
     * channel) pair — no cross-channel fan-out needed on this side. Mirrors
     * cicchetto's `applyPresenceEvent` (lib/members.ts). */
    private suspend fun applyPresenceEvent(dto: ScrollbackMessageDto) {
        when (dto.kind) {
            "join" -> db.memberDao().insertIfAbsent(MemberEntity(dto.network, dto.channel, dto.sender, EMPTY_MODES_JSON))
            "part", "quit" -> db.memberDao().delete(dto.network, dto.channel, dto.sender)
            "kick" -> dto.meta.stringOrNull("target")?.let { target ->
                db.memberDao().delete(dto.network, dto.channel, target)
            }
            "nick_change" -> dto.meta.stringOrNull("new_nick")?.let { newNick ->
                db.memberDao().rename(dto.network, dto.channel, dto.sender, newNick)
            }
            "mode" -> applyModeString(dto)
            else -> Unit
        }
    }

    /** Mirrors cicchetto's `applyModeString` (lib/modeApply.ts): walks the mode string
     * with a sign cursor + an args index, only (qahov) letters consume an arg — channel
     * modes (n/t/m/k/l/b/...) are skipped without advancing the index, same simplification
     * the reference client documents as fine in practice. */
    private suspend fun applyModeString(dto: ScrollbackMessageDto) {
        val modes = dto.meta.stringOrNull("modes") ?: return
        val args = dto.meta["args"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull.orEmpty() } ?: return
        var sign = '+'
        var argIndex = 0
        for (ch in modes) {
            when (ch) {
                '+', '-' -> sign = ch
                else -> {
                    val sigil = PRIVILEGE_SIGILS[ch] ?: continue
                    val target = args.getOrNull(argIndex) ?: continue
                    argIndex++
                    val member = db.memberDao().find(dto.network, dto.channel, target) ?: continue
                    val current = runCatching {
                        AppJson.decodeFromString(ListSerializer(String.serializer()), member.modesJson)
                    }.getOrDefault(emptyList())
                    val sigilStr = sigil.toString()
                    val has = sigilStr in current
                    if (sign == '+' && has) continue
                    if (sign == '-' && !has) continue
                    val updated = if (sign == '+') current + sigilStr else current - sigilStr
                    db.memberDao().updateModes(
                        dto.network,
                        dto.channel,
                        target,
                        AppJson.encodeToString(ListSerializer(String.serializer()), updated),
                    )
                }
            }
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

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

    suspend fun requestWhois(subject: String, networkId: Int, nick: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
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
    suspend fun requestBanlist(subject: String, networkId: Int, channel: String, mode: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
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

    suspend fun kick(subject: String, networkId: Int, channel: String, nick: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "kick",
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
                put("nick", nick)
                put("reason", "")
            },
        )
    }

    suspend fun ban(subject: String, networkId: Int, channel: String, mask: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "ban",
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
                put("mask", mask)
            },
        )
    }

    suspend fun unban(subject: String, networkId: Int, channel: String, mask: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "unban",
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
                put("mask", mask)
            },
        )
    }

    suspend fun op(subject: String, networkId: Int, channel: String, nick: String) =
        nickListVerb(subject, "op", networkId, channel, nick)

    suspend fun deop(subject: String, networkId: Int, channel: String, nick: String) =
        nickListVerb(subject, "deop", networkId, channel, nick)

    suspend fun voice(subject: String, networkId: Int, channel: String, nick: String) =
        nickListVerb(subject, "voice", networkId, channel, nick)

    suspend fun devoice(subject: String, networkId: Int, channel: String, nick: String) =
        nickListVerb(subject, "devoice", networkId, channel, nick)

    private suspend fun nickListVerb(subject: String, verb: String, networkId: Int, channel: String, nick: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
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
    suspend fun setMode(subject: String, networkId: Int, target: String, modes: String, params: List<String>) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "mode",
            buildJsonObject {
                put("network_id", networkId)
                put("target", target)
                put("modes", modes)
                put("params", buildJsonArray { params.forEach { add(JsonPrimitive(it)) } })
            },
        )
    }

    suspend fun openQueryWindow(subject: String, networkId: Int, targetNick: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
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
    suspend fun closeQueryWindow(subject: String, networkId: Int, targetNick: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "close_query_window",
            buildJsonObject {
                put("network_id", networkId)
                put("target_nick", targetNick)
            },
        )
    }
}
