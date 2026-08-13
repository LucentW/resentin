package pm.antani.resentin.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.net.dto.NetworkAdminDto
import pm.antani.resentin.net.dto.SessionAdminDto
import pm.antani.resentin.net.dto.UserAdminDto
import pm.antani.resentin.net.dto.VhostAdminDto
import pm.antani.resentin.net.dto.VisitorAdminDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.admin_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    actions = {
                        if (state.tab != AdminTab.SESSIONS) {
                            IconButton(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_chat))
                            }
                        }
                        IconButton(onClick = viewModel::refreshAll) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                        }
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
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                when (state.tab) {
                    AdminTab.NETWORKS -> NetworksTab(state.networks, viewModel)
                    AdminTab.VHOSTS -> VhostsTab(state.vhosts, viewModel)
                    AdminTab.USERS -> UsersTab(state.users, viewModel)
                    AdminTab.SESSIONS -> SessionsTab(state.sessions, viewModel)
                    AdminTab.VISITORS -> VisitorsTab(state.visitors, state.lastSweepCount, viewModel)
                }
            }
            state.error?.let { message ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = viewModel::consumeError) { Text(stringResource(R.string.home_dialog_cancel)) } },
                ) {
                    Text(message)
                }
            }
        }
    }

    if (showCreateDialog) {
        when (state.tab) {
            AdminTab.NETWORKS -> NewNetworkDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { slug -> viewModel.createNetwork(slug); showCreateDialog = false },
            )
            AdminTab.VHOSTS -> SingleFieldDialog(
                title = stringResource(R.string.admin_new_vhost_title),
                hint = stringResource(R.string.admin_new_vhost_hint),
                onDismiss = { showCreateDialog = false },
                onCreate = { address -> viewModel.createVhost(address); showCreateDialog = false },
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
private fun NetworksTab(networks: List<NetworkAdminDto>, viewModel: AdminViewModel) {
    var addServerFor by remember { mutableStateOf<NetworkAdminDto?>(null) }
    var pendingDelete by remember { mutableStateOf<NetworkAdminDto?>(null) }

    if (networks.isEmpty()) {
        EmptyHint(stringResource(R.string.admin_networks_empty))
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(networks, key = { it.id }) { network ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(network.slug, style = MaterialTheme.typography.bodyLarge)
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
                    IconButton(onClick = { addServerFor = network }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.admin_add_server))
                    }
                    IconButton(onClick = { pendingDelete = network }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove))
                    }
                }
            }
        }
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
}

@Composable
private fun VhostsTab(vhosts: List<VhostAdminDto>, viewModel: AdminViewModel) {
    var pendingDelete by remember { mutableStateOf<VhostAdminDto?>(null) }
    if (vhosts.isEmpty()) {
        EmptyHint(stringResource(R.string.admin_vhosts_empty))
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(vhosts, key = { it.id }) { vhost ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(vhost.address, style = MaterialTheme.typography.bodyLarge)
                        val flags = listOfNotNull(
                            stringResource(R.string.admin_vhost_pool).takeIf { vhost.inPool },
                            stringResource(R.string.admin_vhost_available).takeIf { vhost.generallyAvailable },
                        ).joinToString(" · ")
                        if (flags.isNotEmpty()) {
                            Text(flags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { pendingDelete = vhost }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove))
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
private fun UsersTab(users: List<UserAdminDto>, viewModel: AdminViewModel) {
    var pendingDelete by remember { mutableStateOf<UserAdminDto?>(null) }
    if (users.isEmpty()) {
        EmptyHint(stringResource(R.string.admin_users_empty))
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(users, key = { it.id }) { user ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.admin_user_sessions, user.liveSessionCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.admin_user_admin_label), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = user.isAdmin, onCheckedChange = { viewModel.toggleUserAdmin(user) })
                    IconButton(onClick = { pendingDelete = user }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove))
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
}

@Composable
private fun SessionsTab(sessions: List<SessionAdminDto>, viewModel: AdminViewModel) {
    if (sessions.isEmpty()) {
        EmptyHint(stringResource(R.string.admin_sessions_empty))
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(sessions, key = { it.compositeId }) { session ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(session.subjectLabel ?: session.subjectId, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.admin_session_meta, session.subjectKind, session.liveState?.nick ?: "—"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.disconnectSession(session) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.admin_session_disconnect))
                    }
                    IconButton(onClick = { viewModel.killSession(session) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.admin_session_kill))
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
            Button(onClick = viewModel::sweepVisitors) {
                Text(stringResource(R.string.admin_sweep_now))
            }
        }
        if (visitors.isEmpty()) {
            EmptyHint(stringResource(R.string.admin_visitors_empty))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(visitors, key = { it.id }) { visitor ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(visitor.ip ?: visitor.id, style = MaterialTheme.typography.bodyLarge)
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
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove))
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

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize()) {
        Text(text, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.archive_delete_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_cancel)) } },
    )
}

@Composable
private fun SingleFieldDialog(title: String, hint: String, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, placeholder = { Text(hint) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onCreate(value.trim()) }, enabled = value.isNotBlank()) {
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
        title = { Text(stringResource(R.string.admin_new_network_title)) },
        text = {
            OutlinedTextField(
                value = slug,
                onValueChange = { slug = it },
                placeholder = { Text(stringResource(R.string.admin_new_network_hint)) },
                singleLine = true,
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
        title = { Text(stringResource(R.string.admin_add_server_title, networkSlug)) },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    placeholder = { Text(stringResource(R.string.admin_server_host_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    placeholder = { Text(stringResource(R.string.admin_server_port_hint)) },
                    singleLine = true,
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
        title = { Text(stringResource(R.string.admin_new_user_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.admin_user_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text(stringResource(R.string.admin_user_password_hint)) },
                    singleLine = true,
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
