package pm.antani.resentin.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.net.dto.AddressingSettingsAdminDto
import pm.antani.resentin.net.dto.CredentialAdminDto
import pm.antani.resentin.net.dto.NetworkAdminDto
import pm.antani.resentin.net.dto.ServerAdminDto
import pm.antani.resentin.net.dto.SessionAdminDto
import pm.antani.resentin.net.dto.SessionLogEntryDto
import pm.antani.resentin.net.dto.SettingsAdminDto
import pm.antani.resentin.net.dto.UploadAdminDto
import pm.antani.resentin.net.dto.UploadSettingsAdminDto
import pm.antani.resentin.net.dto.UserAdminDto
import pm.antani.resentin.net.dto.VhostAdminDto
import pm.antani.resentin.net.dto.VisitorAdminDto
import pm.antani.resentin.ui.common.ResentinHeaderAction
import pm.antani.resentin.ui.common.ResentinFilterChip
import pm.antani.resentin.ui.common.ResentinEmptyState
import pm.antani.resentin.ui.common.ResentinErrorState
import pm.antani.resentin.ui.common.ResentinLoadingState
import pm.antani.resentin.ui.common.ResentinStateBanner
import pm.antani.resentin.ui.common.ResentinStateTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val selectedTabHasContent = when (state.tab) {
        AdminTab.NETWORKS -> state.networks.isNotEmpty()
        AdminTab.VHOSTS -> state.vhosts.isNotEmpty()
        AdminTab.USERS -> state.users.isNotEmpty()
        AdminTab.SESSIONS -> state.sessions.isNotEmpty()
        AdminTab.VISITORS -> state.visitors.isNotEmpty()
        AdminTab.SETTINGS -> state.settings != null
        AdminTab.SESSION_LOG -> state.sessionLogLoaded
    }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Only NETWORKS/VHOSTS/USERS have a create dialog wired below — without this,
    // switching tabs while the dialog state was still true from a tab that never
    // rendered it (e.g. VISITORS, which has no `when` branch and silently no-ops)
    // let a stale `showCreateDialog = true` surface later on whichever tab the user
    // landed on next, popping up the wrong dialog.
    LaunchedEffect(state.tab) { showCreateDialog = false }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.admin_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        ResentinHeaderAction(
                            onClick = onBack,
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    },
                    actions = {
                        if (state.tab == AdminTab.NETWORKS || state.tab == AdminTab.VHOSTS || state.tab == AdminTab.USERS) {
                            ResentinHeaderAction(
                                onClick = { showCreateDialog = true },
                                icon = Icons.Outlined.Add,
                                contentDescription = stringResource(
                                    when (state.tab) {
                                        AdminTab.NETWORKS -> R.string.admin_action_create_network
                                        AdminTab.VHOSTS -> R.string.admin_action_create_vhost
                                        else -> R.string.admin_action_create_user
                                    },
                                ),
                            )
                        }
                        ResentinHeaderAction(
                            onClick = viewModel::refreshAll,
                            icon = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                            enabled = !state.isLoading,
                            loading = state.isLoading,
                        )
                    },
                )
                TabRow(selectedTabIndex = state.tab.ordinal) {
                    Tab(
                        selected = state.tab == AdminTab.NETWORKS,
                        onClick = { viewModel.selectTab(AdminTab.NETWORKS) },
                        text = { Text(stringResource(R.string.admin_tab_networks)) },
                    )
                    Tab(
                        selected = state.tab == AdminTab.VHOSTS,
                        onClick = { viewModel.selectTab(AdminTab.VHOSTS) },
                        text = { Text(stringResource(R.string.admin_tab_vhosts)) },
                    )
                    Tab(
                        selected = state.tab == AdminTab.USERS,
                        onClick = { viewModel.selectTab(AdminTab.USERS) },
                        text = { Text(stringResource(R.string.admin_tab_users)) },
                    )
                    Tab(
                        selected = state.tab == AdminTab.SESSIONS,
                        onClick = { viewModel.selectTab(AdminTab.SESSIONS) },
                        text = { Text(stringResource(R.string.admin_tab_sessions)) },
                    )
                    Tab(
                        selected = state.tab == AdminTab.VISITORS,
                        onClick = { viewModel.selectTab(AdminTab.VISITORS) },
                        text = { Text(stringResource(R.string.admin_tab_visitors)) },
                    )
                    Tab(
                        selected = state.tab == AdminTab.SETTINGS,
                        onClick = { viewModel.selectTab(AdminTab.SETTINGS) },
                        text = { Text(stringResource(R.string.admin_tab_settings)) },
                    )
                    Tab(
                        selected = state.tab == AdminTab.SESSION_LOG,
                        onClick = { viewModel.selectTab(AdminTab.SESSION_LOG) },
                        text = { Text(stringResource(R.string.admin_tab_session_log)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && !selectedTabHasContent -> {
                    ResentinLoadingState(
                        title = stringResource(R.string.admin_loading_title),
                        description = stringResource(R.string.admin_loading_description),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                !selectedTabHasContent && state.error != null -> {
                    ResentinErrorState(
                        icon = Icons.Outlined.WifiOff,
                        title = stringResource(R.string.admin_error_title),
                        description = state.error.orEmpty(),
                        actionLabel = stringResource(R.string.home_connection_retry),
                        onRetry = viewModel::refreshAll,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> when (state.tab) {
                    AdminTab.NETWORKS -> NetworksTab(state.networks, state.expandedNetworkIds, state.serversByNetworkId, viewModel)
                    AdminTab.VHOSTS -> VhostsTab(state.vhosts, viewModel)
                    AdminTab.USERS -> UsersTab(state.users, state.networks, state.credentials, state.managingNetworksForUser, viewModel)
                    AdminTab.SESSIONS -> SessionsTab(state.sessions, state.networks, viewModel)
                    AdminTab.VISITORS -> VisitorsTab(state.visitors, state.lastSweepCount, viewModel)
                    AdminTab.SETTINGS -> SettingsTab(state.settings, state.uploads, viewModel)
                    AdminTab.SESSION_LOG -> SessionLogTab(state.sessionLog)
                }
            }
            if (selectedTabHasContent && state.error != null) {
                ResentinStateBanner(
                    icon = Icons.Outlined.WifiOff,
                    title = stringResource(R.string.ui_error_title),
                    description = state.error.orEmpty(),
                    tone = ResentinStateTone.ERROR,
                    actionLabel = stringResource(R.string.ui_dismiss),
                    onAction = viewModel::consumeError,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }

    if (showCreateDialog) {
        when (state.tab) {
            AdminTab.NETWORKS -> NewNetworkDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { slug -> viewModel.createNetwork(slug); showCreateDialog = false },
            )
            AdminTab.VHOSTS -> NewVhostDialog(
                candidates = state.vhostHostCandidates,
                existing = state.vhosts,
                onDismiss = { showCreateDialog = false },
                onCreate = { address, inPool, generallyAvailable ->
                    viewModel.createVhost(address, inPool, generallyAvailable)
                    showCreateDialog = false
                },
            )
            AdminTab.USERS -> NewUserDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, password -> viewModel.createUser(name, password); showCreateDialog = false },
            )
            else -> Unit
        }
    }
}

@Composable
private fun NetworksTab(
    networks: List<NetworkAdminDto>,
    expandedNetworkIds: Set<Int>,
    serversByNetworkId: Map<Int, List<ServerAdminDto>>,
    viewModel: AdminViewModel,
) {
    var addServerFor by remember { mutableStateOf<NetworkAdminDto?>(null) }
    var pendingDelete by remember { mutableStateOf<NetworkAdminDto?>(null) }
    var editing by remember { mutableStateOf<NetworkAdminDto?>(null) }
    var pendingServerDelete by remember { mutableStateOf<Pair<Int, ServerAdminDto>?>(null) }

    if (networks.isEmpty()) {
        EmptyHint(stringResource(R.string.admin_networks_empty))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adminListPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(networks, key = { it.id }) { network ->
                val expanded = network.id in expandedNetworkIds
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(network.slug, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(
                                        R.string.admin_network_caps,
                                        network.maxConcurrentUserSessions ?: -1,
                                        network.maxConcurrentVisitorSessions ?: -1,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(stringResource(R.string.admin_network_visitors_label), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = network.visitorEnabled, onCheckedChange = { viewModel.toggleVisitorEnabled(network) })
                            IconButton(onClick = { editing = network }) {
                                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.admin_edit_network))
                            }
                            IconButton(onClick = { addServerFor = network }) {
                                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.admin_add_server))
                            }
                            IconButton(onClick = { pendingDelete = network }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove))
                            }
                            IconButton(onClick = { viewModel.toggleNetworkServers(network) }) {
                                Icon(
                                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.admin_toggle_servers),
                                )
                            }
                        }
                        if (expanded) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                            val servers = serversByNetworkId[network.id]
                            if (servers == null) {
                                Text(
                                    stringResource(R.string.cd_loading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (servers.isEmpty()) {
                                Text(
                                    stringResource(R.string.admin_servers_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                servers.forEach { server ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${server.host}:${server.port}" + if (server.tls) " (TLS)" else "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (!server.enabled) {
                                            Text(
                                                stringResource(R.string.admin_server_disabled),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = { pendingServerDelete = network.id to server }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    pendingServerDelete?.let { (networkId, server) ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_server_title),
            message = stringResource(R.string.admin_delete_server_confirm, "${server.host}:${server.port}"),
            onDismiss = { pendingServerDelete = null },
            onConfirm = { viewModel.removeServer(networkId, server); pendingServerDelete = null },
        )
    }

    addServerFor?.let { network ->
        AddServerDialog(
            networkSlug = network.slug,
            onDismiss = { addServerFor = null },
            onCreate = { host, port, tls -> viewModel.addServer(network.id, host, port, tls); addServerFor = null },
        )
    }
    pendingDelete?.let { network ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_network_title),
            message = stringResource(R.string.admin_delete_network_confirm, network.slug),
            onDismiss = { pendingDelete = null },
            onConfirm = { viewModel.deleteNetwork(network); pendingDelete = null },
        )
    }
    editing?.let { network ->
        EditNetworkDialog(
            network = network,
            onDismiss = { editing = null },
            onSave = { visitorEnabled, visitorAutoconnect, maxVisitor, maxUser, maxPerIp ->
                viewModel.updateNetwork(network.slug, visitorEnabled, visitorAutoconnect, maxVisitor, maxUser, maxPerIp)
                editing = null
            },
        )
    }
}

@Composable
private fun EditNetworkDialog(
    network: NetworkAdminDto,
    onDismiss: () -> Unit,
    onSave: (visitorEnabled: Boolean, visitorAutoconnect: Boolean, maxVisitor: Int?, maxUser: Int?, maxPerIp: Int?) -> Unit,
) {
    var visitorEnabled by remember { mutableStateOf(network.visitorEnabled) }
    var visitorAutoconnect by remember { mutableStateOf(network.visitorAutoconnect) }
    var maxVisitor by remember { mutableStateOf(network.maxConcurrentVisitorSessions?.toString().orEmpty()) }
    var maxUser by remember { mutableStateOf(network.maxConcurrentUserSessions?.toString().orEmpty()) }
    var maxPerIp by remember { mutableStateOf(network.maxPerIp?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(R.string.admin_edit_network_title, network.slug),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.admin_network_visitors_label), modifier = Modifier.weight(1f))
                    Switch(checked = visitorEnabled, onCheckedChange = { visitorEnabled = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.admin_network_autoconnect_label), modifier = Modifier.weight(1f))
                    Switch(checked = visitorAutoconnect, onCheckedChange = { visitorAutoconnect = it })
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.admin_caps_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = maxVisitor,
                    onValueChange = { maxVisitor = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.admin_cap_visitor_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxUser,
                    onValueChange = { maxUser = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.admin_cap_user_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxPerIp,
                    onValueChange = { maxPerIp = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.admin_cap_per_ip_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(visitorEnabled, visitorAutoconnect, maxVisitor.toIntOrNull(), maxUser.toIntOrNull(), maxPerIp.toIntOrNull())
                },
            ) {
                Text(stringResource(R.string.network_settings_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_cancel)) } },
    )
}

@Composable
private fun VhostsTab(vhosts: List<VhostAdminDto>, viewModel: AdminViewModel) {
    var pendingDelete by remember { mutableStateOf<VhostAdminDto?>(null) }
    if (vhosts.isEmpty()) {
        EmptyHint(stringResource(R.string.admin_vhosts_empty))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adminListPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vhosts, key = { it.id }) { vhost ->
                AdminRowCard {
                    Column(Modifier.weight(1f)) {
                        Text(vhost.address, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        val flags = listOfNotNull(
                            stringResource(R.string.admin_vhost_pool).takeIf { vhost.inPool },
                            stringResource(R.string.admin_vhost_available).takeIf { vhost.generallyAvailable },
                        ).joinToString(" · ")
                        if (flags.isNotEmpty()) {
                            Text(flags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { pendingDelete = vhost }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove))
                    }
                }
            }
        }
    }
    pendingDelete?.let { vhost ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_vhost_title),
            message = stringResource(R.string.admin_delete_vhost_confirm, vhost.address),
            onDismiss = { pendingDelete = null },
            onConfirm = { viewModel.deleteVhost(vhost); pendingDelete = null },
        )
    }
}

@Composable
private fun UsersTab(
    users: List<UserAdminDto>,
    networks: List<NetworkAdminDto>,
    credentials: List<CredentialAdminDto>,
    managingNetworksFor: UserAdminDto?,
    viewModel: AdminViewModel,
) {
    var pendingDelete by remember { mutableStateOf<UserAdminDto?>(null) }
    var rotatingPasswordFor by remember { mutableStateOf<UserAdminDto?>(null) }
    if (users.isEmpty()) {
        EmptyHint(stringResource(R.string.admin_users_empty))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adminListPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(users, key = { it.id }) { user ->
                AdminRowCard {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.admin_user_sessions, user.liveSessionCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.admin_user_admin_label), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = user.isAdmin, onCheckedChange = { viewModel.toggleUserAdmin(user) })
                    IconButton(onClick = { viewModel.openManageNetworks(user) }) {
                        Icon(Icons.Outlined.Public, contentDescription = stringResource(R.string.admin_manage_networks))
                    }
                    IconButton(onClick = { rotatingPasswordFor = user }) {
                        Icon(Icons.Outlined.Key, contentDescription = stringResource(R.string.admin_rotate_password))
                    }
                    IconButton(onClick = { pendingDelete = user }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove))
                    }
                }
            }
        }
    }
    pendingDelete?.let { user ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_user_title),
            message = stringResource(R.string.admin_delete_user_confirm, user.name),
            onDismiss = { pendingDelete = null },
            onConfirm = { viewModel.deleteUser(user); pendingDelete = null },
        )
    }
    rotatingPasswordFor?.let { user ->
        RotatePasswordDialog(
            userName = user.name,
            onDismiss = { rotatingPasswordFor = null },
            onConfirm = { password -> viewModel.rotateUserPassword(user, password); rotatingPasswordFor = null },
        )
    }
    managingNetworksFor?.let { user ->
        ManageUserNetworksDialog(
            user = user,
            networks = networks,
            credentials = credentials.filter { it.userId == user.id },
            onDismiss = viewModel::closeManageNetworks,
            onBind = { network, nick, authMethod, password -> viewModel.bindNetwork(user, network, nick, authMethod, password) },
            onUnbind = { network -> viewModel.unbindNetwork(user, network) },
        )
    }
}

@Composable
private fun RotatePasswordDialog(userName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(R.string.admin_rotate_password_title, userName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.admin_new_password_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotBlank()) {
                Text(stringResource(R.string.admin_rotate_password))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_cancel)) } },
    )
}

/** cicchetto's per-user page redesign (#1158), condensed into a dialog: every
 * network the server knows about, each with a checkbox for "does this user
 * have a credential here" — checking one reveals the minimal bind form
 * (nick + auth method + password), unchecking an existing one asks to confirm
 * before unbinding (it also kills any live session on that credential). */
@Composable
private fun ManageUserNetworksDialog(
    user: UserAdminDto,
    networks: List<NetworkAdminDto>,
    credentials: List<CredentialAdminDto>,
    onDismiss: () -> Unit,
    onBind: (NetworkAdminDto, nick: String, authMethod: String, password: String?) -> Unit,
    onUnbind: (NetworkAdminDto) -> Unit,
) {
    val boundNetworkIds = remember(credentials) { credentials.map { it.networkId }.toSet() }
    var addingFor by remember { mutableStateOf<NetworkAdminDto?>(null) }
    var pendingUnbind by remember { mutableStateOf<NetworkAdminDto?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(R.string.admin_manage_networks_title, user.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                networks.forEach { network ->
                    val credential = credentials.firstOrNull { it.networkId == network.id }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = network.id in boundNetworkIds,
                            onCheckedChange = { checked ->
                                if (checked) addingFor = network else pendingUnbind = network
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(network.slug)
                            credential?.let {
                                Text(
                                    "${it.nick} · ${it.authMethod}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (addingFor?.id == network.id) {
                        BindNetworkForm(
                            onCancel = { addingFor = null },
                            onSave = { nick, authMethod, password ->
                                onBind(network, nick, authMethod, password)
                                addingFor = null
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_dismiss)) }
        },
    )

    pendingUnbind?.let { network ->
        ConfirmDialog(
            title = stringResource(R.string.admin_unbind_network_title),
            message = stringResource(R.string.admin_unbind_network_confirm, network.slug),
            onDismiss = { pendingUnbind = null },
            onConfirm = { onUnbind(network); pendingUnbind = null },
        )
    }
}

private val AUTH_METHODS = listOf("auto", "sasl", "server_pass", "nickserv_identify", "none")

@Composable
private fun BindNetworkForm(onCancel: () -> Unit, onSave: (nick: String, authMethod: String, password: String?) -> Unit) {
    var nick by remember { mutableStateOf("") }
    var authMethod by remember { mutableStateOf(AUTH_METHODS.first()) }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 8.dp)) {
        OutlinedTextField(
            value = nick,
            onValueChange = { nick = it },
            label = { Text(stringResource(R.string.network_settings_nick_label)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AUTH_METHODS.forEach { method ->
                ResentinFilterChip(
                    selected = authMethod == method,
                    onClick = { authMethod = method },
                    label = { Text(method) },
                )
            }
        }
        if (authMethod != "none") {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.home_dialog_cancel)) }
            TextButton(
                onClick = { onSave(nick.trim(), authMethod, password.ifBlank { null }) },
                enabled = nick.isNotBlank(),
            ) {
                Text(stringResource(R.string.network_settings_save))
            }
        }
    }
}

@Composable
private fun SessionsTab(sessions: List<SessionAdminDto>, networks: List<NetworkAdminDto>, viewModel: AdminViewModel) {
    val networkSlugs = remember(networks) { networks.associate { it.id to it.slug } }
    if (sessions.isEmpty()) {
        EmptyHint(stringResource(R.string.admin_sessions_empty))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adminListPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sessions, key = { it.compositeId }) { session ->
                AdminRowCard {
                    Column(Modifier.weight(1f)) {
                        Text(
                            session.subjectLabel ?: session.subjectId,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            stringResource(
                                R.string.admin_session_meta,
                                networkSlugs[session.networkId] ?: session.networkId.toString(),
                                session.subjectKind,
                                session.liveState?.nick ?: "—",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.disconnectSession(session) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.admin_session_disconnect))
                    }
                    IconButton(onClick = { viewModel.killSession(session) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.admin_session_kill))
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitorsTab(visitors: List<VisitorAdminDto>, lastSweepCount: Int?, viewModel: AdminViewModel) {
    var pendingDelete by remember { mutableStateOf<VisitorAdminDto?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                lastSweepCount?.let {
                    Text(
                        stringResource(R.string.admin_sweep_result, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Button(
                onClick = viewModel::sweepVisitors,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.admin_sweep_now))
            }
        }
        if (visitors.isEmpty()) {
            EmptyHint(stringResource(R.string.admin_visitors_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = adminListPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visitors, key = { it.id }) { visitor ->
                    AdminRowCard {
                        Column(Modifier.weight(1f)) {
                            Text(
                                visitor.ip ?: visitor.id,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (visitor.identified) {
                                    stringResource(R.string.admin_visitor_identified)
                                } else {
                                    stringResource(R.string.admin_visitor_anonymous)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { pendingDelete = visitor }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove))
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { visitor ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_visitor_title),
            message = stringResource(R.string.admin_delete_visitor_confirm, visitor.ip ?: visitor.id),
            onDismiss = { pendingDelete = null },
            onConfirm = { viewModel.deleteVisitor(visitor); pendingDelete = null },
        )
    }
}

private val UPLOAD_HOSTS = listOf("embedded", "litterbox")
private val ADDRESSING_MODES = listOf("pool_with_reservations", "static_mapping_with_reservations")

@Composable
private fun SettingsTab(settings: SettingsAdminDto?, uploads: List<UploadAdminDto>, viewModel: AdminViewModel) {
    if (settings == null) {
        EmptyHint(stringResource(R.string.admin_settings_empty))
        return
    }
    var activeHost by remember(settings) { mutableStateOf(settings.upload.activeHost) }
    var imageCap by remember(settings) { mutableStateOf(settings.upload.imagePerFileCapBytes?.toString().orEmpty()) }
    var videoCap by remember(settings) { mutableStateOf(settings.upload.videoPerFileCapBytes?.toString().orEmpty()) }
    var documentCap by remember(settings) { mutableStateOf(settings.upload.documentPerFileCapBytes?.toString().orEmpty()) }
    var audioCap by remember(settings) { mutableStateOf(settings.upload.audioPerFileCapBytes?.toString().orEmpty()) }
    var globalCap by remember(settings) { mutableStateOf(settings.upload.globalCapBytes?.toString().orEmpty()) }
    var videoMaxDuration by remember(settings) { mutableStateOf(settings.upload.videoMaxDurationSeconds?.toString().orEmpty()) }
    var addressingMode by remember(settings) { mutableStateOf(settings.addressing.mode) }
    var staticMappingPrefix by remember(settings) { mutableStateOf(settings.addressing.staticMappingPrefix.orEmpty()) }
    var pendingUploadDelete by remember { mutableStateOf<UploadAdminDto?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = adminListPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AdminSectionCard {
                Text(stringResource(R.string.admin_settings_upload_title).uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.admin_settings_active_host_label), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UPLOAD_HOSTS.forEach { host ->
                        ResentinFilterChip(selected = activeHost == host, onClick = { activeHost = host }, label = { Text(host) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                ByteCapField(stringResource(R.string.admin_settings_image_cap_label), imageCap) { imageCap = it }
                ByteCapField(stringResource(R.string.admin_settings_video_cap_label), videoCap) { videoCap = it }
                ByteCapField(stringResource(R.string.admin_settings_document_cap_label), documentCap) { documentCap = it }
                ByteCapField(stringResource(R.string.admin_settings_audio_cap_label), audioCap) { audioCap = it }
                ByteCapField(stringResource(R.string.admin_settings_global_cap_label), globalCap) { globalCap = it }
                OutlinedTextField(
                    value = videoMaxDuration,
                    onValueChange = { videoMaxDuration = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.admin_settings_video_duration_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            AdminSectionCard {
                Text(stringResource(R.string.admin_settings_addressing_title).uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ADDRESSING_MODES.forEach { mode ->
                        ResentinFilterChip(selected = addressingMode == mode, onClick = { addressingMode = mode }, label = { Text(mode) })
                    }
                }
                if (addressingMode == "static_mapping_with_reservations") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = staticMappingPrefix,
                        onValueChange = { staticMappingPrefix = it },
                        label = { Text(stringResource(R.string.admin_settings_static_prefix_label)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.updateSettings(
                        UploadSettingsAdminDto(
                            activeHost, imageCap.toLongOrNull(), videoCap.toLongOrNull(), documentCap.toLongOrNull(),
                            audioCap.toLongOrNull(), globalCap.toLongOrNull(), videoMaxDuration.toIntOrNull(),
                        ),
                        AddressingSettingsAdminDto(addressingMode, staticMappingPrefix.ifBlank { null }),
                    )
                },
            ) {
                Text(stringResource(R.string.network_settings_save))
            }
        }
        item {
            Text(stringResource(R.string.admin_uploads_title).uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        if (uploads.isEmpty()) {
            item { Text(stringResource(R.string.admin_uploads_empty), style = MaterialTheme.typography.bodySmall) }
        } else {
            items(uploads, key = { it.id }) { upload ->
                AdminRowCard {
                    Column(Modifier.weight(1f)) {
                        Text(upload.originalFilename ?: upload.slug, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatBytes(upload.bytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { pendingUploadDelete = upload }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove))
                    }
                }
            }
        }
    }
    pendingUploadDelete?.let { upload ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_upload_title),
            message = stringResource(R.string.admin_delete_upload_confirm, upload.originalFilename ?: upload.slug),
            onDismiss = { pendingUploadDelete = null },
            onConfirm = { viewModel.deleteUpload(upload); pendingUploadDelete = null },
        )
    }
}

@Composable
private fun ByteCapField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) "$bytes ${units[0]}" else "%.1f %s".format(value, units[unitIndex])
}

@Composable
private fun AdminSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), content = content)
    }
}

@Composable
private fun SessionLogTab(entries: List<SessionLogEntryDto>) {
    if (entries.isEmpty()) {
        EmptyHint(stringResource(R.string.admin_session_log_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = adminListPadding(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(entries, key = { "${it.sessionId}-${it.at}-${it.event}" }) { entry ->
            AdminRowCard {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${entry.event}" + (entry.networkSlug?.let { " · $it" } ?: "") + (entry.nick?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val reason = entry.reason?.let { r -> "$r" + (entry.clean?.let { if (it) " (clean)" else " (unclean)" } ?: "") }
                    Text(
                        reason ?: entry.at,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(entry.at, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    ResentinEmptyState(
        icon = Icons.Outlined.Inbox,
        title = text,
        modifier = Modifier.fillMaxSize(),
        compact = true,
    )
}

@Composable
private fun AdminRowCard(content: @Composable RowScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

private fun adminListPadding() = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

@Composable
private fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.archive_delete_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_cancel)) } },
    )
}

/** cicchetto parity: the address field is a picker over the host's actual
 * egressable addresses (already-configured ones excluded), not free text — a
 * typo here only ever surfaced as an opaque bind failure once something tried
 * to use it. Falls back to a plain text field when the server hasn't measured
 * any candidates (older server, or a host with nothing egressable to offer). */
@Composable
private fun NewVhostDialog(
    candidates: List<String>,
    existing: List<VhostAdminDto>,
    onDismiss: () -> Unit,
    onCreate: (address: String, inPool: Boolean, generallyAvailable: Boolean) -> Unit,
) {
    val existingAddresses = remember(existing) { existing.map { it.address }.toSet() }
    val available = remember(candidates, existingAddresses) { candidates.filterNot { it in existingAddresses } }
    var address by remember { mutableStateOf(available.firstOrNull().orEmpty()) }
    var inPool by remember { mutableStateOf(false) }
    var generallyAvailable by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(R.string.admin_new_vhost_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                if (available.isEmpty()) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        placeholder = { Text(stringResource(R.string.admin_new_vhost_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(stringResource(R.string.admin_vhost_pick_address), style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        available.forEach { candidate ->
                            ResentinFilterChip(
                                selected = address == candidate,
                                onClick = { address = candidate },
                                label = { Text(candidate) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = inPool,
                        onCheckedChange = { checked ->
                            inPool = checked
                            if (checked) generallyAvailable = true
                        },
                    )
                    Text(stringResource(R.string.admin_vhost_in_pool_label))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = generallyAvailable,
                        onCheckedChange = { generallyAvailable = it },
                        enabled = !inPool,
                    )
                    Text(stringResource(R.string.admin_vhost_generally_available_label))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(address.trim(), inPool, generallyAvailable) }, enabled = address.isNotBlank()) {
                Text(stringResource(R.string.admin_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_cancel)) } },
    )
}

@Composable
private fun NewNetworkDialog(onDismiss: () -> Unit, onCreate: (slug: String) -> Unit) {
    var slug by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(R.string.admin_new_network_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            OutlinedTextField(
                value = slug,
                onValueChange = { slug = it },
                placeholder = { Text(stringResource(R.string.admin_new_network_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(slug.trim()) }, enabled = slug.isNotBlank()) {
                Text(stringResource(R.string.admin_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_cancel)) } },
    )
}

@Composable
private fun AddServerDialog(networkSlug: String, onDismiss: () -> Unit, onCreate: (host: String, port: Int, tls: Boolean) -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("6697") }
    var tls by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(R.string.admin_add_server_title, networkSlug),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    placeholder = { Text(stringResource(R.string.admin_server_host_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    placeholder = { Text(stringResource(R.string.admin_server_port_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.admin_server_tls), modifier = Modifier.weight(1f))
                    Switch(checked = tls, onCheckedChange = { tls = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(host.trim(), port.toIntOrNull() ?: 6697, tls) },
                enabled = host.isNotBlank() && port.isNotBlank(),
            ) {
                Text(stringResource(R.string.admin_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_cancel)) } },
    )
}

@Composable
private fun NewUserDialog(onDismiss: () -> Unit, onCreate: (name: String, password: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(R.string.admin_new_user_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.admin_user_name_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text(stringResource(R.string.admin_user_password_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), password) }, enabled = name.isNotBlank() && password.isNotBlank()) {
                Text(stringResource(R.string.admin_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_cancel)) } },
    )
}
