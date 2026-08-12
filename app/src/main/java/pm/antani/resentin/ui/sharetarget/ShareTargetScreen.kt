package pm.antani.resentin.ui.sharetarget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pm.antani.resentin.ui.home.HomeViewModel

/** "Condividi in..." — the landing screen when another app shares a file/photo into
 * Resentin (ACTION_SEND/SEND_MULTIPLE) and we don't yet know which chat it's for. Reuses
 * HomeViewModel's network/channel data (no separate fetch), flattened into one pick list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTargetScreen(
    viewModel: HomeViewModel,
    onChatSelected: (networkSlug: String, channelName: String) -> Unit,
    onCancel: () -> Unit,
) {
    val networks by viewModel.networks.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Condividi in...") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Annulla")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isRefreshing && networks.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    networks.forEach { networkWithChannels ->
                        item(key = "network-${networkWithChannels.network.slug}") {
                            Text(
                                text = "${networkWithChannels.network.slug} — ${networkWithChannels.network.nick}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            networkWithChannels.channels.filter { it.source != "server" },
                            key = { "${networkWithChannels.network.slug}-${it.name}" },
                        ) { channel ->
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onChatSelected(networkWithChannels.network.slug, channel.name)
                                    }
                                    .padding(start = 40.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
