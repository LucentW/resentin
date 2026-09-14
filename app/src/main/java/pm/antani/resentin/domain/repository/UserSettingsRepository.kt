package pm.antani.resentin.domain.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.irc.resolveStoredIrcMessage
import pm.antani.resentin.net.dto.AliasesEnvelopeDto
import pm.antani.resentin.net.dto.DisplayPrefsDto
import pm.antani.resentin.net.dto.DisplayPrefsEnvelopeDto
import pm.antani.resentin.net.dto.IrcMessagesDto
import pm.antani.resentin.net.dto.MutedTargetDto
import pm.antani.resentin.net.dto.NotificationPrefsDto
import pm.antani.resentin.net.dto.ShowPeerProfilesDto
import pm.antani.resentin.net.dto.VhostSelectionUpdateDto
import pm.antani.resentin.net.dto.VhostSettingsDto
import pm.antani.resentin.net.dto.WatchlistDto
import pm.antani.resentin.net.rest.UserSettingsApi

/** Server muted_targets key: "<slug> <ascii-folded target>" (Identifier.channel_key/2)
 * — SPACE separator (RFC 2812 excludes it from chanstrings, so keys split
 * unambiguously); ASCII-only fold mirrors fold_ascii/1, leaving >=0x80 bytes
 * untouched. Deliberately NOT AppPreferences.channelKey (slash-separated, full
 * lowercase) — byte equality with the server is the contract. */
fun serverChannelKey(networkSlug: String, target: String): String {
    val folded = target.map { if (it in 'A'..'Z') it + 32 else it }.joinToString("")
    return "$networkSlug $folded"
}

/** Mute state for one chat: unknown until the prefs first load (fail-open, like the
 * server's own lenient reader — an unreadable list must never silence anyone). */
data class ServerMute(
    val loaded: Boolean,
    val muted: Boolean = false,
    /** Mute expiry unix seconds, null = permanent. Meaningful only when muted. */
    val until: Long? = null,
)

class UserSettingsRepository(
    private val authRepository: AuthRepository,
    private val connectionManager: ConnectionManager,
    private val appPreferences: AppPreferences,
) {

    // #348 on grappa-irc — cached auto-away preference. `null` covers both "not loaded
    // yet" and "no preference, server default applies": the two render identically
    // ("use site default"), same conflation cic's autoAway.ts signal makes. Lives here
    // (not per-ViewModel) because the live `auto_away_debounce_changed` push — fired for
    // every write on the subject's account, including from another device — needs
    // somewhere with app-wide lifetime to land, same as the WS-fed state other
    // repositories (e.g. MembersRepository) hold.
    private val _autoAwayDebounceSeconds = MutableStateFlow<Int?>(null)
    val autoAwayDebounceSeconds: StateFlow<Int?> = _autoAwayDebounceSeconds.asStateFlow()

    fun startListening(scope: CoroutineScope) {
        connectionManager.events
            .filterIsInstance<WsEvent.AutoAwayDebounceChanged>()
            .onEach { _autoAwayDebounceSeconds.value = it.debounce.autoAwayDebounceSeconds }
            .launchIn(scope)
    }

    suspend fun getDisplayPrefs(): Result<DisplayPrefsDto> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getDisplayPrefs().displayPrefs
    }.onSuccess { _displayPrefs.value = it }

    suspend fun updateDisplayPrefs(prefs: DisplayPrefsDto): Result<DisplayPrefsDto> = runCatching {
        authRepository.api(UserSettingsApi::class.java)
            .updateDisplayPrefs(DisplayPrefsEnvelopeDto(prefs)).displayPrefs
    }.onSuccess { _displayPrefs.value = it }

    private val _displayPrefs = MutableStateFlow<DisplayPrefsDto?>(null)

    /** Last fetched server display prefs (null = never loaded). The presence-pin UI
     * below and the Settings nicklist toggle share this cache. */
    val displayPrefs: StateFlow<DisplayPrefsDto?> = _displayPrefs.asStateFlow()

    suspend fun refreshDisplayPrefs(): Result<DisplayPrefsDto> = getDisplayPrefs()

    /** Presence pin for one chat: "show"/"hide"/null (absent = server default).
     * Same ChannelKey shape as muted_targets; the server hides the matching
     * join/part/quit/nick/mode rows itself, so this client filters nothing. */
    fun presencePinFlowFor(networkSlug: String, target: String): Flow<String?> =
        displayPrefs.map { it?.presenceFilter?.get(serverChannelKey(networkSlug, target)) }

    suspend fun setPresencePin(networkSlug: String, target: String, pin: String?): Result<DisplayPrefsDto> =
        runCatching {
            val current = _displayPrefs.value ?: refreshDisplayPrefs().getOrThrow()
            val key = serverChannelKey(networkSlug, target)
            val pins = if (pin == null) current.presenceFilter - key else current.presenceFilter + (key to pin)
            updateDisplayPrefs(current.copy(presenceFilter = pins)).getOrThrow()
        }

    suspend fun getAliases(): Result<Map<String, String>> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getAliases().aliases
    }

    suspend fun updateAliases(aliases: Map<String, String>): Result<Map<String, String>> = runCatching {
        authRepository.api(UserSettingsApi::class.java).updateAliases(AliasesEnvelopeDto(aliases)).aliases
    }

    suspend fun getVhostSettings(): Result<VhostSettingsDto> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getVhostSettings()
    }

    suspend fun updateVhostSelection(selection: List<String>): Result<VhostSettingsDto> = runCatching {
        authRepository.api(UserSettingsApi::class.java).updateVhostSelection(VhostSelectionUpdateDto(selection))
    }

    suspend fun getAutoAwayDebounce(): Result<Int?> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getAutoAwayDebounce().autoAwayDebounceSeconds
    }.onSuccess { _autoAwayDebounceSeconds.value = it }

    /** M2 — the peer-avatar/gender-badge opt-in. Off by default; without it grappa never
     * queries another user's CTCP USERINFO/AVATAR at all, so a DM partner's avatar (query
     * rows, notifications) or a WHOIS card's gender badge simply never populates. */
    suspend fun getShowPeerProfiles(): Result<Boolean> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getShowPeerProfiles().showPeerProfiles
    }

    suspend fun updateShowPeerProfiles(enabled: Boolean): Result<Boolean> = runCatching {
        authRepository.api(UserSettingsApi::class.java).updateShowPeerProfiles(ShowPeerProfilesDto(enabled)).showPeerProfiles
    }

    private val _ircMessages = MutableStateFlow<IrcMessagesDto?>(null)

    /** Ultimi messaggi IRC PART/QUIT caricati (`null` = mai caricati).
     * `IrcMessagesDto.partMessage/quitMessage == null` = mai personalizzato
     * (il mittente usa il predefinito con versione, senza scriverlo sul server
     * per non congelare la versione né sovrascrivere altri device). */
    val ircMessages: StateFlow<IrcMessagesDto?> = _ircMessages.asStateFlow()

    /**
     * Legge i messaggi IRC con fallback locale. Il server non espone ancora
     * `GET /me/settings/irc-messages`: le route non registrate cadono sul catch-all
     * SPA (HTML), che parsato come JSON dà "Unexpected JSON token at offset 0".
     * Per non mostrare quell'errore tecnico in Impostazioni e non restare
     * senza motivo, ogni fallimento server (endpoint assente, offline, HTML) cade sul
     * DataStore locale — che distingue comunque `null` (predefinito) da `""` (nessun
     * motivo). Quando il server supporterà l'endpoint, i suoi non-null vincono e il
     * locale lo segue, così i valori restano cross-device.
     */
    suspend fun getIrcMessages(): Result<IrcMessagesDto> = runCatching {
        val serverDto = runCatching {
            authRepository.api(UserSettingsApi::class.java).getIrcMessages()
        }.getOrNull()
        if (serverDto != null) {
            val localPart = appPreferences.ircPartMessage.first()
            val localQuit = appPreferences.ircQuitMessage.first()
            val effective = IrcMessagesDto(
                partMessage = serverDto.partMessage ?: localPart,
                quitMessage = serverDto.quitMessage ?: localQuit,
            )
            if (effective.partMessage != localPart) appPreferences.setIrcPartMessage(effective.partMessage)
            if (effective.quitMessage != localQuit) appPreferences.setIrcQuitMessage(effective.quitMessage)
            effective
        } else {
            IrcMessagesDto(
                partMessage = appPreferences.ircPartMessage.first(),
                quitMessage = appPreferences.ircQuitMessage.first(),
            )
        }
    }.onSuccess { _ircMessages.value = it }

    /**
     * Salva entrambi i messaggi in un colpo solo (stesso endpoint, come
     * display-prefs: full-map PUT, non PATCH). `null` = torna al predefinito,
     * `""`/blank = nessun motivo, altro = personalizzato. Il body è hand-built
     * come JsonObject affinché un `null` esplicito sopravviva ad AppJson con
     * `explicitNulls = false` (stessa trappola di updateAutoAwayDebounce).
     * Non scrive mai il predefinito risolto: il server conserva grezzo/`null`.
     *
     * Se il server non accetta ancora l'endpoint (o offline), persiste comunque in
     * locale così la funzione resta usabile su questo device senza mostrare
     * "Unexpected JSON token at offset 0"; al prossimo round-trip server riuscito i
     * valori locali verranno spinti su (il save invia sempre entrambi i draft).
     */
    suspend fun updateIrcMessages(partMessage: String?, quitMessage: String?): Result<IrcMessagesDto> =
        runCatching {
            val serverDto = runCatching {
                val body = buildJsonObject {
                    put("part_message", partMessage?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("quit_message", quitMessage?.let { JsonPrimitive(it) } ?: JsonNull)
                }
                authRepository.api(UserSettingsApi::class.java).updateIrcMessages(body)
            }.getOrNull()
            if (serverDto != null) {
                appPreferences.setIrcPartMessage(serverDto.partMessage)
                appPreferences.setIrcQuitMessage(serverDto.quitMessage)
                serverDto
            } else {
                appPreferences.setIrcPartMessage(partMessage)
                appPreferences.setIrcQuitMessage(quitMessage)
                IrcMessagesDto(partMessage, quitMessage)
            }
        }.onSuccess { _ircMessages.value = it }

    /** Motivo PART risolto per l'invio (sincrono, fail verso predefinito se mai
     * caricato — copre anche chi aggiorna senza aver mai personalizzato). */
    fun partMessageForSend(): String? =
        resolveStoredIrcMessage(_ircMessages.value?.partMessage)

    /** Motivo QUIT risolto per l'invio (stessa disciplina del PART). */
    fun quitMessageForSend(): String? =
        resolveStoredIrcMessage(_ircMessages.value?.quitMessage)

    private val _notificationPrefs = MutableStateFlow<NotificationPrefsDto?>(null)

    /** Last fetched server notification prefs (null = never loaded). Warmed at app
     * start and on every channel-settings open; mutations update it from the PUT
     * response, so no extra GET is needed to stay current. */
    val notificationPrefs: StateFlow<NotificationPrefsDto?> = _notificationPrefs.asStateFlow()

    suspend fun refreshNotificationPrefs(): Result<NotificationPrefsDto> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getNotificationPrefs().notificationPrefs
    }.onSuccess { _notificationPrefs.value = it }

    /** Mute state for one chat, honouring server-side snooze expiry client-side too
     * (the server prunes elapsed entries on read; a stale-enough cache could
     * otherwise outlive them here). */
    fun muteFor(networkSlug: String, target: String): ServerMute {
        val prefs = _notificationPrefs.value ?: return ServerMute(loaded = false)
        return muteIn(prefs, networkSlug, target, loaded = true)
    }

    fun muteFlowFor(networkSlug: String, target: String): Flow<ServerMute> =
        notificationPrefs.map { prefs ->
            if (prefs == null) ServerMute(loaded = false)
            else muteIn(prefs, networkSlug, target, loaded = true)
        }

    private fun muteIn(prefs: NotificationPrefsDto, networkSlug: String, target: String, loaded: Boolean): ServerMute {
        val entry = prefs.mutedTargets[serverChannelKey(networkSlug, target)] ?: return ServerMute(loaded = loaded)
        val until = entry.until
        if (until != null && until * 1000 <= System.currentTimeMillis()) return ServerMute(loaded = loaded)
        return ServerMute(loaded = loaded, muted = true, until = until)
    }

    /** Synchronous gate for the notification paths — fail-open when never loaded. */
    fun isMutedNow(networkSlug: String, target: String): Boolean {
        val mute = muteFor(networkSlug, target)
        return mute.loaded && mute.muted
    }

    /** Mute until unix seconds, null = permanent. Read-modify-write of the FULL prefs
     * map (the server full-replaces every key but muted_targets) built by hand so a
     * permanent mute's explicit null survives AppJson's explicitNulls=false. */
    suspend fun setMute(networkSlug: String, target: String, until: Long?): Result<NotificationPrefsDto> =
        updateMutedTargets { it + (serverChannelKey(networkSlug, target) to MutedTargetDto(until)) }

    suspend fun clearMute(networkSlug: String, target: String): Result<NotificationPrefsDto> =
        updateMutedTargets { it - serverChannelKey(networkSlug, target) }

    private suspend fun updateMutedTargets(
        transform: (Map<String, MutedTargetDto>) -> Map<String, MutedTargetDto>,
    ): Result<NotificationPrefsDto> = runCatching {
        val current = _notificationPrefs.value ?: refreshNotificationPrefs().getOrThrow()
        val body = buildJsonObject {
            put("channel_messages_all", current.channelMessagesAll)
            put("channel_messages_only", buildJsonArray { current.channelMessagesOnly.forEach { add(JsonPrimitive(it)) } })
            put("channel_mentions", current.channelMentions)
            put("private_messages_all", current.privateMessagesAll)
            put("private_messages_only", buildJsonArray { current.privateMessagesOnly.forEach { add(JsonPrimitive(it)) } })
            put("presence_online", current.presenceOnline)
            put("presence_offline", current.presenceOffline)
            put(
                "muted_targets",
                buildJsonObject {
                    transform(current.mutedTargets).forEach { (key, value) ->
                        put(key, buildJsonObject { put("until", value.until?.let { JsonPrimitive(it) } ?: JsonNull) })
                    }
                },
            )
        }
        authRepository.api(UserSettingsApi::class.java).updateNotificationPrefs(body).notificationPrefs
    }.onSuccess { _notificationPrefs.value = it }

    /** [seconds]: `null` clears the preference (site default applies), `0` switches
     * auto-away off, any other value is the grace period in seconds — the accepted
     * range is the server's to enforce, surfaced verbatim on rejection. */
    suspend fun updateAutoAwayDebounce(seconds: Int?): Result<Int?> = runCatching {
        val body = buildJsonObject {
            put("auto_away_debounce_seconds", seconds?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        authRepository.api(UserSettingsApi::class.java).updateAutoAwayDebounce(body).autoAwayDebounceSeconds
    }.onSuccess { _autoAwayDebounceSeconds.value = it }

    // `/hilight` watchlist — highlight keyword patterns stored SERVER-side in
    // `user_settings.highlight_patterns` (NOT the /me/settings/* REST family: the
    // highlight list has no REST arm, only the `watchlist` verb). `null` = never
    // loaded; callers fall back to nick-only matching until the first `list` lands.
    // Same no-broadcast mirror discipline as cicchetto's highlightList signal: the
    // server never pushes changes, every mutation returns the full list in the reply.
    private val _highlightPatterns = MutableStateFlow<List<String>?>(null)
    val highlightPatterns: StateFlow<List<String>?> = _highlightPatterns.asStateFlow()

    suspend fun refreshWatchlist(subject: String): Result<List<String>> =
        updateWatchlist(subject, "list", null)

    suspend fun addHighlight(subject: String, pattern: String): Result<List<String>> =
        updateWatchlist(subject, "add", pattern)

    suspend fun removeHighlight(subject: String, pattern: String): Result<List<String>> =
        updateWatchlist(subject, "del", pattern)

    private suspend fun updateWatchlist(subject: String, action: String, pattern: String?): Result<List<String>> =
        runCatching {
            val reply = connectionManager.pushVerb(
                "grappa:user:$subject",
                "watchlist",
                buildJsonObject {
                    put("action", action)
                    pattern?.let { put("pattern", it) }
                },
            )
            AppJson.decodeFromJsonElement(WatchlistDto.serializer(), reply).patterns
        }.onSuccess { _highlightPatterns.value = it }
}
