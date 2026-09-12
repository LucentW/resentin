package pm.antani.resentin.ui.sharetarget

import pm.antani.resentin.ui.theme.ResentinSpacing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.data.db.ChannelEntity
import pm.antani.resentin.data.db.NetworkEntity
import pm.antani.resentin.ui.common.MircText
import pm.antani.resentin.ui.common.ResentinEmptyState
import pm.antani.resentin.ui.common.ResentinErrorState
import pm.antani.resentin.ui.common.ResentinHeaderAction
import pm.antani.resentin.ui.common.ResentinLoadingState
import pm.antani.resentin.ui.common.ResentinStateBanner
import pm.antani.resentin.ui.common.ResentinStateTone
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
    val error by viewModel.error.collectAsState()
    val shareableNetworks = remember(networks) {
        networks.filter { network -> network.channels.any { it.source != "server" } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_target_title)) },
                navigationIcon = {
                    ResentinHeaderAction(
                        onClick = onCancel,
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.cd_cancel),
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isRefreshing && shareableNetworks.isEmpty() -> {
                    ResentinLoadingState(
                        title = stringResource(R.string.share_target_loading_title),
                        description = stringResource(R.string.share_target_loading_description),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                error != null && shareableNetworks.isEmpty() -> {
                    ResentinErrorState(
                        icon = Icons.Outlined.WifiOff,
                        title = stringResource(R.string.share_target_error_title),
                        description = stringResource(R.string.share_target_error_description),
                        actionLabel = stringResource(R.string.home_connection_retry),
                        onRetry = viewModel::refresh,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                shareableNetworks.isEmpty() -> {
                    ResentinEmptyState(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = stringResource(R.string.share_target_empty_title),
                        description = stringResource(R.string.share_target_empty_description),
                        actionLabel = stringResource(R.string.cd_cancel),
                        onAction = onCancel,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shareableNetworks, key = { it.network.slug }) { networkWithChannels ->
                            ShareNetworkGroupCard(
                                network = networkWithChannels.network,
                                channels = networkWithChannels.channels.filter { it.source != "server" },
                                onChatSelected = { channel ->
                                    onChatSelected(networkWithChannels.network.slug, channel.name)
                                },
                            )
                        }
                    }
                }
            }
            if (error != null && shareableNetworks.isNotEmpty()) {
                ResentinStateBanner(
                    icon = Icons.Outlined.WifiOff,
                    title = stringResource(R.string.ui_error_title),
                    description = error.orEmpty(),
                    tone = ResentinStateTone.ERROR,
                    actionLabel = stringResource(R.string.home_connection_retry),
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }
}
@Composable
private fun ShareNetworkGroupCard(
    network: NetworkEntity,
    channels: List<ChannelEntity>,
    onChatSelected: (ChannelEntity) -> Unit,
) {
    // Stessa lingua delle card della home: una surfaceContainerHigh per rete,
    // stable avatar tint per slug, dividers between rows.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ShareNetworkHeader(network = network, channelCount = channels.size)
            channels.forEachIndexed { index, channel ->
                if (index == 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                ShareChannelRow(
                    channel = channel,
                    onClick = { onChatSelected(channel) },
                )
                if (index < channels.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareNetworkHeader(network: NetworkEntity, channelCount: Int) {
    val scheme = MaterialTheme.colorScheme
    val (avatarContainer, avatarContent) = remember(scheme, network.slug) {
        scheme.primaryContainer to scheme.onPrimaryContainer
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.medium,
            color = avatarContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = network.slug.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = avatarContent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = network.slug,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${network.nick} • $channelCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShareChannelRow(channel: ChannelEntity, onClick: () -> Unit) {
    val isQuery = channel.source == "query"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = ResentinSpacing.medium, end = ResentinSpacing.large, top = ResentinSpacing.small, bottom = ResentinSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = if (isQuery) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isQuery) Icons.Outlined.ChatBubbleOutline else Icons.Outlined.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isQuery) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val topic = channel.topic?.takeIf { it.isNotBlank() }
            if (isQuery || topic != null) {
                MircText(
                    text = if (isQuery) {
                        stringResource(R.string.channel_private_conversation)
                    } else {
                        topic.orEmpty()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    enableLinks = false,
                )
            }
        }
    }
}
