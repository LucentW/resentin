package pm.antani.resentin.ui.appsettings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import pm.antani.resentin.data.prefs.ChatDisplayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(viewModel: AppSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val stayConnected by viewModel.stayConnected.collectAsState()
    val chatDisplayMode by viewModel.chatDisplayMode.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setStayConnected(true)
    }

    fun onStayConnectedChange(enabled: Boolean) {
        if (!enabled) {
            viewModel.setStayConnected(false)
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setStayConnected(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Resta connesso in background")
                        Text(
                            "Mantiene la connessione attiva e mostra notifiche per i messaggi privati e le menzioni",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = stayConnected, onCheckedChange = ::onStayConnectedChange)
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Nicklist colorata", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.displayPrefs.coloredNicklist,
                        onCheckedChange = { viewModel.toggleColoredNicklist() },
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text("Visualizzazione chat", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = chatDisplayMode == ChatDisplayMode.BUBBLES,
                        onClick = { viewModel.setChatDisplayMode(ChatDisplayMode.BUBBLES) },
                        label = { Text("Bolle") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = chatDisplayMode == ChatDisplayMode.IRC_LINE,
                        onClick = { viewModel.setChatDisplayMode(ChatDisplayMode.IRC_LINE) },
                        label = { Text("Monoriga IRC") },
                    )
                }
                Text(
                    "Monoriga IRC: [HH:mm] <nick> messaggio, come un client IRC classico",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Secondi nei timestamp")
                        Text(
                            "Mostra [HH:mm:ss] invece di [HH:mm], in entrambe le visualizzazioni",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = showSeconds, onCheckedChange = viewModel::setShowSeconds)
                }
                Spacer(Modifier.height(24.dp))
                Text("Alias", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            items(state.aliases.entries.toList(), key = { it.key }) { (name, expansion) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(expansion, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { viewModel.removeAlias(name) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Rimuovi")
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.newAliasName,
                    onValueChange = viewModel::onNewAliasNameChange,
                    label = { Text("Nome alias") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.newAliasExpansion,
                    onValueChange = viewModel::onNewAliasExpansionChange,
                    label = { Text("Espansione") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::addAlias, modifier = Modifier.fillMaxWidth()) {
                    Text("Aggiungi alias")
                }
                state.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
