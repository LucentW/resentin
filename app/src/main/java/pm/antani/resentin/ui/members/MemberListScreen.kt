package pm.antani.resentin.ui.members

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.irc.SIGIL_PRIORITY
import pm.antani.resentin.irc.highestSigil
import pm.antani.resentin.ui.common.UserCardSheet
import pm.antani.resentin.ui.common.sigilsOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberListScreen(
    viewModel: MembersViewModel,
    title: String,
    networkSlug: String,
    viewerUsername: String,
    onBack: () -> Unit,
    onOpenQuery: (networkSlug: String, nick: String) -> Unit,
) {
    val members by viewModel.members.collectAsState()
    val ownSigils by viewModel.ownSigils.collectAsState()
    val privilegeModes by viewModel.privilegeModes.collectAsState()
    val whois by viewModel.selectedWhois.collectAsState()
    val sorted = remember(members) { members.sortedWith(memberOrdering) }

    LaunchedEffect(Unit) {
        viewModel.navigateToQuery.collect { nick -> onOpenQuery(networkSlug, nick) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
            items(sorted, key = { it.nick }) { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onMemberClick(member.nick) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("${sigilsOf(member)}${member.nick}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    val whoisValue = whois
    if (whoisValue != null) {
        UserCardSheet(
            whois = whoisValue,
            viewerUsername = viewerUsername,
            ownSigils = ownSigils,
            targetSigils = viewModel.sigilsFor(whoisValue.target),
            availableModes = privilegeModes,
            onDismiss = viewModel::dismissWhois,
            onContactPrivately = viewModel::contactPrivately,
            onKick = viewModel::kick,
            onBan = viewModel::ban,
            onSetMode = viewModel::setMode,
        )
    }
}

// A member can hold several sigils at once (e.g. "@+"); rank by the highest one they
// have (SIGIL_PRIORITY), not by presence/absence of any single one.
private fun highestRank(member: MemberEntity): Int {
    val sigil = highestSigil(sigilsOf(member))
    return sigil?.let { SIGIL_PRIORITY.indexOf(it) } ?: SIGIL_PRIORITY.size
}

private val memberOrdering = compareBy<MemberEntity> { highestRank(it) }
    .thenBy { it.nick.lowercase() }
