package pm.antani.resentin.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.dto.WhoisBundleDto

/** Standard IRC channel-privilege sigil hierarchy, highest first: ~ owner, & admin
 * (protect), @ op, % half-op, + voice. Only shown for letters the network's own
 * ISUPPORT PREFIX actually advertises — not every ircd has owner/admin. */
val PRIVILEGE_MODES: List<Pair<Char, String>> = listOf(
    'q' to "Owner",
    'a' to "Protect",
    'o' to "Op",
    'h' to "Halfop",
    'v' to "Voice",
)

private val PRIVILEGE_SIGILS = mapOf('q' to '~', 'a' to '&', 'o' to '@', 'h' to '%', 'v' to '+')

fun sigilOfMode(letter: Char): Char? = PRIVILEGE_SIGILS[letter]

fun sigilsOf(member: MemberEntity): String = runCatching {
    AppJson.decodeFromString(ListSerializer(String.serializer()), member.modesJson).joinToString("")
}.getOrDefault("")

/** Whether [sigils] (as returned by [sigilsOf]) grants channel-privileged actions —
 * gates the op/deop/halfop/etc. toggle buttons in the user card. */
fun isPrivileged(sigils: String): Boolean = sigils.any { it in setOf('~', '&', '@') }

/**
 * Shared whois/kick/ban/privilege-toggle/contact-privately logic behind the user card.
 * Used identically from the member list and from long-pressing a chat message — both
 * are already scoped to a single (network, channel), so this centralizes the behavior
 * instead of duplicating it across MembersViewModel and ChatViewModel.
 */
class UserCardController(
    private val membersRepository: MembersRepository,
    private val networksRepository: NetworksRepository,
    private val networkSlug: String,
    private val channelName: String,
    private val username: String,
    private val scope: CoroutineScope,
) {
    val members: StateFlow<List<MemberEntity>> = membersRepository.observeMembers(networkSlug, channelName)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ownSigils: StateFlow<String> = members
        .map { list -> list.find { it.nick.equals(username, ignoreCase = true) }?.let(::sigilsOf) ?: "" }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), "")

    /** Mode letter -> sigil this network's ircd actually supports, e.g. `{"o":"@","v":"+"}`. */
    val privilegeModes: StateFlow<Map<String, String>> = membersRepository.observePrefixModes(networkSlug)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _selectedWhois = MutableStateFlow<WhoisBundleDto?>(null)
    val selectedWhois: StateFlow<WhoisBundleDto?> = _selectedWhois.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _navigateToQuery = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToQuery: SharedFlow<String> = _navigateToQuery.asSharedFlow()

    init {
        membersRepository.whoisEvents.onEach { _selectedWhois.value = it }.launchIn(scope)
    }

    fun sigilsFor(nick: String): String =
        members.value.find { it.nick.equals(nick, ignoreCase = true) }?.let(::sigilsOf) ?: ""

    fun onNickClick(nick: String) {
        scope.launch {
            runCatching {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.requestWhois(username, networkId, nick)
            }.onFailure { _error.value = it.message }
        }
    }

    fun dismissWhois() {
        _selectedWhois.value = null
    }

    fun kick(nick: String) = runVerb { networkId -> membersRepository.kick(username, networkId, channelName, nick) }

    fun ban(nick: String) =
        runVerb { networkId -> membersRepository.ban(username, networkId, channelName, "$nick!*@*") }

    fun contactPrivately(nick: String) {
        scope.launch {
            runCatching {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.openQueryWindow(username, networkId, nick)
            }.onSuccess { _navigateToQuery.tryEmit(nick) }
                .onFailure { _error.value = it.message }
        }
    }

    /** [letter] is the privilege mode letter (q/a/o/h/v); [grant] true to add it, false
     * to remove it. Uses the dedicated op/deop/voice/devoice verbs where they exist,
     * the generic raw-MODE verb otherwise (halfop/owner/admin have none). */
    fun setMode(nick: String, letter: Char, grant: Boolean) = runVerb { networkId ->
        when (letter) {
            'o' -> if (grant) {
                membersRepository.op(username, networkId, channelName, nick)
            } else {
                membersRepository.deop(username, networkId, channelName, nick)
            }
            'v' -> if (grant) {
                membersRepository.voice(username, networkId, channelName, nick)
            } else {
                membersRepository.devoice(username, networkId, channelName, nick)
            }
            else -> membersRepository.setMode(
                username,
                networkId,
                channelName,
                "${if (grant) "+" else "-"}$letter",
                listOf(nick),
            )
        }
    }

    private fun runVerb(action: suspend (networkId: Int) -> Unit) {
        scope.launch {
            runCatching {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                action(networkId)
            }.onFailure { _error.value = it.message }
        }
    }
}
