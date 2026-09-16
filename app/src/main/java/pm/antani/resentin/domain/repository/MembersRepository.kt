package pm.antani.resentin.domain.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import pm.antani.resentin.domain.session.VerbException
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.dto.AwayConfirmedDto
import pm.antani.resentin.net.dto.BanlistBundleDto
import pm.antani.resentin.net.dto.AvatarReadyDto
import pm.antani.resentin.net.dto.IsupportChangedDto
import pm.antani.resentin.net.dto.LinksBundleDto
import pm.antani.resentin.net.dto.LusersBundleDto
import pm.antani.resentin.net.dto.RecoverProgressDto
import pm.antani.resentin.net.dto.RecoverResultDto
import pm.antani.resentin.net.dto.ServerReplyDto
import pm.antani.resentin.net.dto.SupportedUmodesChangedDto
import pm.antani.resentin.net.dto.UmodeChangedDto
import pm.antani.resentin.net.dto.MembersSeededDto
import pm.antani.resentin.net.dto.ScrollbackMessageDto
import pm.antani.resentin.net.dto.UserhostDto
import pm.antani.resentin.net.dto.WhoReplyDto
import pm.antani.resentin.net.dto.WhoisBundleDto
import pm.antani.resentin.net.dto.WhowasBundleDto

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
                is WsEvent.AwayConfirmed -> recordAway(event.away)
                is WsEvent.UmodeChanged -> _umodesByNetworkId.value += (event.umodes.networkId to event.umodes.modes)
                is WsEvent.SupportedUmodesChanged -> _supportedUmodesByNetworkId.value += (event.supported.networkId to event.supported.modes)
                is WsEvent.RecoverProgress -> applyRecoverProgress(event.progress)
                is WsEvent.RecoverResult -> applyRecoverResult(event.result)
                else -> Unit
            }
        }.launchIn(scope)
    }

    // Explicit away state per network slug ("away" or absent) — mirrors cicchetto's
    // `awayByNetwork` signal, fed by `away_confirmed`. Lives here (not per-ViewModel)
    // because the push fires for writes from any device on the subject's account and
    // needs an app-wide landing spot, same as the other WS-fed state in this repo.
    private val _awayByNetwork = MutableStateFlow<Map<String, String>>(emptyMap())
    val awayByNetwork: StateFlow<Map<String, String>> = _awayByNetwork.asStateFlow()

    private fun recordAway(dto: AwayConfirmedDto) {
        _awayByNetwork.value = if (dto.state == "away") {
            _awayByNetwork.value + (dto.network to dto.state)
        } else {
            _awayByNetwork.value - dto.network
        }
    }

    /** Manual `/away <reason>` — same `away` verb cicchetto pushes (note: network SLUG,
     * not id, unlike every sibling verb). The authoritative state comes back as
     * `away_confirmed`; unsetting when not explicitly away fails server-side with
     * `not_explicit`, surfaced as [VerbException]. */
    suspend fun setAway(subject: String, networkSlug: String, reason: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "away",
            buildJsonObject {
                put("action", "set")
                put("network", networkSlug)
                put("reason", reason)
            },
        )
    }

    suspend fun unsetAway(subject: String, networkSlug: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "away",
            buildJsonObject {
                put("action", "unset")
                put("network", networkSlug)
            },
        )
    }

    /** `/whowas <nick>` — the reply streams back as a [whowasEvents] bundle,
     * not a push ack. */
    suspend fun requestWhowas(subject: String, networkId: Int, nick: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "whowas",
            buildJsonObject {
                put("network_id", networkId)
                put("nick", nick)
            },
        )
    }

    val whowasEvents: Flow<WhowasBundleDto> = connectionManager.events
        .filterIsInstance<WsEvent.WhowasBundle>()
        .map { it.whowas }

    /** `/who <#chan|mask>` — the server folds the 352 burst and the reply arrives
     * as a [whoEvents] roster, not a push ack. */
    suspend fun requestWho(subject: String, networkId: Int, target: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "who",
            buildJsonObject {
                put("network_id", networkId)
                put("channel", target)
            },
        )
    }

    val whoEvents: Flow<WhoReplyDto> = connectionManager.events
        .filterIsInstance<WsEvent.WhoReply>()
        .map { it.who }

    /** `/lusers [mask [server]]` — the reply arrives as an [lusersEvents] bundle,
     * not a push ack. The server also auto-emits one on connect welcome, so callers
     * must gate on having asked (same consume-once discipline as cicchetto). */
    suspend fun requestLusers(subject: String, networkId: Int, mask: String? = null, server: String? = null) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "lusers",
            buildJsonObject {
                put("network_id", networkId)
                mask?.let { put("mask", it) }
                server?.let { put("server", it) }
            },
        )
    }

    val lusersEvents: Flow<LusersBundleDto> = connectionManager.events
        .filterIsInstance<WsEvent.LusersBundle>()
        .map { it.lusers }

    /** `/links [mask]` — the server folds the 364 burst and the reply arrives
     * as a [linksEvents] bundle, not a push ack. An empty bundle is still a
     * snapshot (restricted topology vs mask-matched-nothing). */
    suspend fun requestLinks(subject: String, networkId: Int, mask: String? = null) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "links",
            buildJsonObject {
                put("network_id", networkId)
                mask?.let { put("mask", it) }
            },
        )
    }

    val linksEvents: Flow<LinksBundleDto> = connectionManager.events
        .filterIsInstance<WsEvent.LinksBundle>()
        .map { it.links }

    // Operator's own umodes per network id (221 + self-MODE echoes) — the
    // UmodeModal's ACTIVE set. Harmlessly overwritten on the next seed.
    private val _umodesByNetworkId = MutableStateFlow<Map<Int, List<String>>>(emptyMap())
    val umodesByNetworkId: StateFlow<Map<Int, List<String>>> = _umodesByNetworkId.asStateFlow()

    // Server-advertised supported umodes per network id (004) — the modal's
    // AVAILABILITY set. Empty = unseeded -> static fallback table.
    private val _supportedUmodesByNetworkId = MutableStateFlow<Map<Int, List<String>>>(emptyMap())
    val supportedUmodesByNetworkId: StateFlow<Map<Int, List<String>>> = _supportedUmodesByNetworkId.asStateFlow()

    /** `/umode <modes>` — user-mode on own nick, same `umode` WS verb cicchetto
     * pushes (one feature, one code path as the modal toggles). */
    suspend fun setUmode(subject: String, networkId: Int, modes: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "umode",
            buildJsonObject {
                put("network_id", networkId)
                put("modes", modes)
            },
        )
    }

    /** No-arg server-text queries — each primes a server accumulator and the
     * reply drains as a `serverReplyEvents` bundle (one modal surface). */
    suspend fun requestInfo(subject: String, networkId: Int) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "info",
            buildJsonObject { put("network_id", networkId) },
        )
    }

    suspend fun requestVersion(subject: String, networkId: Int) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "version",
            buildJsonObject { put("network_id", networkId) },
        )
    }

    suspend fun requestMotd(subject: String, networkId: Int, target: String? = null) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "motd",
            buildJsonObject {
                put("network_id", networkId)
                target?.let { put("target", it) }
            },
        )
    }

    suspend fun requestAdmin(subject: String, networkId: Int, target: String? = null) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "admin",
            buildJsonObject {
                put("network_id", networkId)
                target?.let { put("target", it) }
            },
        )
    }

    val serverReplyEvents: Flow<ServerReplyDto> = connectionManager.events
        .filterIsInstance<WsEvent.ServerReply>()
        .map { it.reply }

    // "Recover my identity" progress — cicchetto recoverProgress.ts parity.
    // Server-driven only: the first progress OPENS the state, later events
    // upsert steps, the terminal result concludes it. A result arriving while
    // closed is dropped (no resurrect after dismiss).
    private val _recoverState = MutableStateFlow<RecoverState?>(null)
    val recoverState: StateFlow<RecoverState?> = _recoverState.asStateFlow()

    fun dismissRecover() { _recoverState.value = null }

    private fun applyRecoverProgress(dto: RecoverProgressDto) {
        val current = _recoverState.value
        if (current == null) {
            _recoverState.value = RecoverState(
                networkSlug = dto.network,
                steps = listOf(RecoverStepEntry(dto.step, dto.status)),
            )
            return
        }
        // Per-network isolation: another network's event never mixes into the
        // open modal's steps.
        if (!dto.network.equals(current.networkSlug, ignoreCase = true)) return
        _recoverState.value = current.copy(steps = upsertRecoverStep(current.steps, dto.step, dto.status))
    }

    private fun applyRecoverResult(dto: RecoverResultDto) {
        val current = _recoverState.value ?: return
        if (!dto.network.equals(current.networkSlug, ignoreCase = true)) return
        _recoverState.value = current.copy(outcome = dto.outcome, reason = dto.reason)
    }

    /** `/recover` — the server runs the NickServ recovery sequence and streams
     * progress/result events (which open/conclude the modal above). Never
     * opened optimistically here. Rejections (nothing_to_recover /
     * already_identified / recovery_in_progress / forbidden) throw. */
    suspend fun requestRecover(subject: String, networkId: Int) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "recover",
            buildJsonObject { put("network_id", networkId) },
        )
    }

    /** `/kb` step one — resolves a nick to `user@host` from the server's userhost
     * cache via an awaited push reply. Returns null on `not_cached` (cicchetto's
     * "kicking anyway" path); any other refusal throws [VerbException]. */
    suspend fun resolveUserhost(subject: String, networkId: Int, nick: String): UserhostDto? {
        val reply = try {
            connectionManager.pushVerb(
                "grappa:user:$subject",
                "resolve_userhost",
                buildJsonObject {
                    put("network_id", networkId)
                    put("nick", nick)
                },
            )
        } catch (e: VerbException) {
            if (e.code == "not_cached") return null
            throw e
        }
        return AppJson.decodeFromJsonElement(UserhostDto.serializer(), reply)
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

    /** Incremental avatar patches for open WHOIS cards (M3b) — same shape as
     * [whoisEvents], consumed by whoever currently shows that nick's card. */
    val avatarEvents: Flow<AvatarReadyDto> = connectionManager.events
        .filterIsInstance<WsEvent.AvatarReady>()
        .map { it.avatar }

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

    suspend fun kick(subject: String, networkId: Int, channel: String, nick: String, reason: String? = null) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "kick",
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
                put("nick", nick)
                put("reason", reason.orEmpty())
            },
        )
    }

    suspend fun invite(subject: String, networkId: Int, channel: String, nick: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "invite",
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
                put("nick", nick)
            },
        )
    }

    suspend fun requestNames(subject: String, networkId: Int, channel: String) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            "names",
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
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

    suspend fun op(subject: String, networkId: Int, channel: String, nick: String) = setNickModes(subject, networkId, channel, "op", listOf(nick))
    suspend fun deop(subject: String, networkId: Int, channel: String, nick: String) = setNickModes(subject, networkId, channel, "deop", listOf(nick))
    suspend fun voice(subject: String, networkId: Int, channel: String, nick: String) = setNickModes(subject, networkId, channel, "voice", listOf(nick))
    suspend fun devoice(subject: String, networkId: Int, channel: String, nick: String) = setNickModes(subject, networkId, channel, "devoice", listOf(nick))

    suspend fun setNickModes(subject: String, networkId: Int, channel: String, verb: String, nicks: List<String>) {
        connectionManager.sendVerb(
            "grappa:user:$subject",
            verb,
            buildJsonObject {
                put("network_id", networkId)
                put("channel", channel)
                put("nicks", buildJsonArray { nicks.forEach { add(JsonPrimitive(it)) } })
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

    /** Closes a DM window server-side (deletes the `query_windows` row). The caller is
     * expected to drop the local `channels` row optimistically on success (see
     * [NetworksRepository.closeLocalQuery]) — the server does not reliably
     * re-broadcast `query_windows_list` on close, so waiting for it leaves dead rows. */
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
