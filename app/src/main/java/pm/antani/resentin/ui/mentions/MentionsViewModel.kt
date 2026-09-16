package pm.antani.resentin.ui.mentions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.net.dto.MentionsBundleDto
import pm.antani.resentin.net.dto.MentionsMessageDto

data class MentionGroup(val channel: String, val rows: List<MentionsMessageDto>)

// Cluster mention rows under their channel, preserving first-seen order —
// cicchetto groupByChannel parity (the server already sends server_time ASC,
// so the first channel to appear leads). Pure, unit-tested.
fun groupMentionsByChannel(messages: List<MentionsMessageDto>): List<MentionGroup> {
    val order = mutableListOf<String>()
    val byChannel = mutableMapOf<String, MutableList<MentionsMessageDto>>()
    for (row in messages) {
        val bucket = byChannel.getOrPut(row.channel) {
            order += row.channel
            mutableListOf()
        }
        bucket += row
    }
    return order.map { channel -> MentionGroup(channel, byChannel.getValue(channel)) }
}

class MentionsViewModel(
    private val membersRepository: MembersRepository,
    private val networkSlug: String,
) : ViewModel() {
    val bundle: StateFlow<MentionsBundleDto?> = membersRepository.mentionsByNetwork
        .map { byNetwork -> byNetwork.entries.firstOrNull { it.key.equals(networkSlug, ignoreCase = true) }?.value }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismiss() = membersRepository.clearMentions(networkSlug)

    companion object {
        fun factory(membersRepository: MembersRepository, networkSlug: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return MentionsViewModel(membersRepository, networkSlug) as T
                }
            }
    }
}
