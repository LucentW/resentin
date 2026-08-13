package pm.antani.resentin.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.ui.common.UserCardController

class MembersViewModel(
    membersRepository: MembersRepository,
    networksRepository: NetworksRepository,
    appPreferences: AppPreferences,
    networkSlug: String,
    channelName: String,
    username: String,
    subject: String,
) : ViewModel() {

    private val controller =
        UserCardController(membersRepository, networksRepository, networkSlug, channelName, username, subject, viewModelScope)

    val members: StateFlow<List<MemberEntity>> = controller.members
    val ownSigils = controller.ownSigils
    val privilegeModes = controller.privilegeModes
    val selectedWhois = controller.selectedWhois
    val error = controller.error
    val navigateToQuery = controller.navigateToQuery

    val coloredNicklist: StateFlow<Boolean> = appPreferences.coloredNicklist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onMemberClick(nick: String) = controller.onNickClick(nick)
    fun dismissWhois() = controller.dismissWhois()
    fun kick(nick: String) = controller.kick(nick)
    fun ban(nick: String) = controller.ban(nick)
    fun contactPrivately(nick: String) = controller.contactPrivately(nick)
    fun setMode(nick: String, letter: Char, grant: Boolean) = controller.setMode(nick, letter, grant)
    fun sigilsFor(nick: String) = controller.sigilsFor(nick)

    companion object {
        fun factory(
            membersRepository: MembersRepository,
            networksRepository: NetworksRepository,
            appPreferences: AppPreferences,
            networkSlug: String,
            channelName: String,
            username: String,
            subject: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return MembersViewModel(membersRepository, networksRepository, appPreferences, networkSlug, channelName, username, subject) as T
            }
        }
    }
}
