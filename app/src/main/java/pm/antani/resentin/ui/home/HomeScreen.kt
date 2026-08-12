package pm.antani.resentin.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.data.db.ChannelEntity
import pm.antani.resentin.data.db.NetworkEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    host: String,
    onSignOut: () -> Unit,
    onChannelClick: (networkSlug: String, channelName: String) -> Unit,
    onNetworkSettingsClick: (networkSlug: String) -> Unit,
    onAppSettingsClick: () -> Unit,
) {
    // "$server" (MOTD + service notices) is reached by tapping the network header
    // itself rather than listed as a channel row — it's network-level, not a channel.
    val onServerClick: (String) -> Unit = { networkSlug -> onChannelClick(networkSlug, "\$server") }

    val networks by viewModel.networks.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(host) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                    }
                    IconButton(onClick = onAppSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_app_settings))
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.cd_sign_out))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isRefreshing && networks.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                networks.isEmpty() -> {
                    Text(stringResource(R.string.home_no_networks), modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        networks.forEach { networkWithChannels ->
                            item(key = "network-${networkWithChannels.network.slug}") {
                                NetworkHeader(
                                    network = networkWithChannels.network,
                                    onClick = { onServerClick(networkWithChannels.network.slug) },
                                    onSettingsClick = { onNetworkSettingsClick(networkWithChannels.network.slug) },
                                )
                            }
                            items(
                                networkWithChannels.channels
                                    .filter { it.source != "server" }
                                    .sortedBy { it.source == "query" },
                                key = { "${networkWithChannels.network.slug}-${it.name}" },
                            ) { channel ->
                                ChannelRow(channel) {
                                    onChannelClick(networkWithChannels.network.slug, channel.name)
                                }
                            }
                        }
                    }
                }
            }
            error?.let { message ->
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Text(message)
                }
            }
        }
    }
}

@Composable
private fun NetworkHeader(network: NetworkEntity, onClick: () -> Unit, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectionStateDot(network.connectionState)
        Text(
            text = "${network.slug} — ${network.nick}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_network_settings))
        }
    }
}

@Composable
private fun ChannelRow(channel: ChannelEntity, onClick: () -> Unit) {
    val hasUnread = channel.unreadMessages > 0
    val hasMention = channel.unreadMentions > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 40.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel.source == "query") {
            Icon(
                Icons.Default.Person,
                contentDescription = stringResource(R.string.cd_direct_message),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (hasMention) MaterialTheme.colorScheme.error else Color.Unspecified,
            modifier = Modifier.weight(1f),
        )
        if (hasUnread) {
            Spacer(Modifier.width(8.dp))
            UnreadBadge(count = channel.unreadMessages, isMention = hasMention)
        }
    }
}

@Composable
private fun UnreadBadge(count: Int, isMention: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = if (isMention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isMention) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun ConnectionStateDot(connectionState: String) {
    val color = when (connectionState) {
        "connected" -> Color(0xFF4CAF50)
        "parked" -> Color(0xFF9E9E9E)
        "failed" -> MaterialTheme.colorScheme.error
        else -> Color(0xFF9E9E9E)
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}
