package pm.antani.resentin.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.data.db.ChannelEntity
import pm.antani.resentin.data.db.NetworkEntity

private data class ChannelActionsTarget(val networkSlug: String, val channel: ChannelEntity)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    host: String,
    onChannelClick: (networkSlug: String, channelName: String) -> Unit,
    onNetworkSettingsClick: (networkSlug: String) -> Unit,
    onAppSettingsClick: () -> Unit,
    onBrowseDirectory: (networkSlug: String) -> Unit = {},
) {
    // "$server" (MOTD + service notices) is reached by tapping the network header
    // itself rather than listed as a channel row — it's network-level, not a channel.
    val onServerClick: (String) -> Unit = { networkSlug -> onChannelClick(networkSlug, "\$server") }

    val networks by viewModel.networks.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.error.collectAsState()

    var actionsTarget by remember { mutableStateOf<ChannelActionsTarget?>(null) }
    var leaveConfirmTarget by remember { mutableStateOf<ChannelActionsTarget?>(null) }
    var newChatNetwork by remember { mutableStateOf<String?>(null) }
    // A visitor's own "detach" is already a full teardown server-side (see
    // AuthRepository.detach), so the sign-out icon skips straight to it; a
    // registered user gets the cicchetto-parity detach/quit choice.
    var showSignOutChoice by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.navigateToChat.collect { (networkSlug, nick) -> onChannelClick(networkSlug, nick) }
    }

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
                    IconButton(onClick = { if (viewModel.isVisitor) viewModel.detach() else showSignOutChoice = true }) {
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
                                    onAddClick = { newChatNetwork = networkWithChannels.network.slug },
                                )
                            }
                            items(
                                networkWithChannels.channels
                                    .filter { it.source != "server" }
                                    .sortedBy { it.source == "query" },
                                key = { "${networkWithChannels.network.slug}-${it.name}" },
                            ) { channel ->
                                ChannelRow(
                                    channel = channel,
                                    onClick = { onChannelClick(networkWithChannels.network.slug, channel.name) },
                                    onLongClick = {
                                        actionsTarget = ChannelActionsTarget(networkWithChannels.network.slug, channel)
                                    },
                                )
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

    actionsTarget?.let { target ->
        ChannelActionsSheet(
            target = target,
            onDismiss = { actionsTarget = null },
            onMarkRead = {
                viewModel.markRead(target.networkSlug, target.channel)
                actionsTarget = null
            },
            onLeave = {
                actionsTarget = null
                leaveConfirmTarget = target
            },
        )
    }

    leaveConfirmTarget?.let { target ->
        val isQuery = target.channel.source == "query"
        AlertDialog(
            onDismissRequest = { leaveConfirmTarget = null },
            title = { Text(stringResource(if (isQuery) R.string.home_action_close_query else R.string.home_action_leave_channel)) },
            text = {
                Text(
                    stringResource(
                        if (isQuery) R.string.home_close_query_confirm else R.string.home_leave_channel_confirm,
                        target.channel.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.leaveChannel(target.networkSlug, target.channel)
                        leaveConfirmTarget = null
                    },
                ) {
                    Text(stringResource(if (isQuery) R.string.home_action_close_query else R.string.home_action_leave_channel))
                }
            },
            dismissButton = {
                TextButton(onClick = { leaveConfirmTarget = null }) {
                    Text(stringResource(R.string.home_dialog_cancel))
                }
            },
        )
    }

    if (showSignOutChoice) {
        AlertDialog(
            onDismissRequest = { showSignOutChoice = false },
            title = { Text(stringResource(R.string.home_sign_out_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.home_sign_out_detach_hint), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            showSignOutChoice = false
                            viewModel.detach()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.home_sign_out_detach))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.home_sign_out_quit_hint), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            showSignOutChoice = false
                            viewModel.quit()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.home_sign_out_quit))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSignOutChoice = false }) {
                    Text(stringResource(R.string.home_dialog_cancel))
                }
            },
        )
    }

    newChatNetwork?.let { networkSlug ->
        NewChatDialog(
            onDismiss = { newChatNetwork = null },
            onJoin = { name ->
                viewModel.joinChannel(networkSlug, name)
                newChatNetwork = null
            },
            onMessage = { nick ->
                viewModel.startDirectMessage(networkSlug, nick)
                newChatNetwork = null
            },
            onBrowseDirectory = {
                newChatNetwork = null
                onBrowseDirectory(networkSlug)
            },
        )
    }
}

@Composable
private fun NewChatDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit,
    onMessage: (String) -> Unit,
    onBrowseDirectory: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_new_chat_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.home_new_chat_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onBrowseDirectory, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_new_chat_browse))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onJoin(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.home_new_chat_join))
            }
        },
        dismissButton = {
            TextButton(onClick = { onMessage(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.home_new_chat_dm))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelActionsSheet(
    target: ChannelActionsTarget,
    onDismiss: () -> Unit,
    onMarkRead: () -> Unit,
    onLeave: () -> Unit,
) {
    val isQuery = target.channel.source == "query"
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        if (target.channel.unreadMessages > 0) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.home_action_mark_read)) },
                leadingContent = { Icon(Icons.Default.Done, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onMarkRead),
            )
        }
        ListItem(
            headlineContent = {
                Text(stringResource(if (isQuery) R.string.home_action_close_query else R.string.home_action_leave_channel))
            },
            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onLeave),
        )
    }
}

@Composable
private fun NetworkHeader(network: NetworkEntity, onClick: () -> Unit, onSettingsClick: () -> Unit, onAddClick: () -> Unit) {
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
        IconButton(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_chat))
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_network_settings))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(channel: ChannelEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    val hasUnread = channel.unreadMessages > 0
    val hasMention = channel.unreadMentions > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
