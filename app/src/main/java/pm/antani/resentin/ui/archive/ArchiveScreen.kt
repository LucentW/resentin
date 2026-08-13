package pm.antani.resentin.ui.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import pm.antani.resentin.R
import pm.antani.resentin.net.dto.ArchiveEntryDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    networkSlug: String,
    onBack: () -> Unit,
    onRecovered: (target: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.recovered) {
        state.recovered?.let {
            onRecovered(it)
            viewModel.consumeRecovered()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archive_title, networkSlug)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.entries.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.entries.isEmpty() -> {
                    Text(stringResource(R.string.archive_empty), modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.entries, key = { it.target }) { entry ->
                            ArchiveRow(
                                entry = entry,
                                onClick = { viewModel.recover(entry) },
                                onDelete = { viewModel.confirmDelete(entry) },
                            )
                        }
                    }
                }
            }
            state.error?.let { message ->
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Text(message)
                }
            }
        }
    }

    state.pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.archive_delete_title)) },
            text = { Text(stringResource(R.string.archive_delete_confirm, entry.target)) },
            confirmButton = {
                TextButton(onClick = viewModel::deleteConfirmed) {
                    Text(stringResource(R.string.archive_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(stringResource(R.string.home_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun ArchiveRow(entry: ArchiveEntryDto, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.kind == "query") {
            Icon(
                Icons.Default.Person,
                contentDescription = stringResource(R.string.cd_direct_message),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(entry.target, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.archive_row_meta, entry.rowCount, formatEpochSeconds(entry.lastActivity)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove))
        }
    }
}

private val ARCHIVE_TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private fun formatEpochSeconds(seconds: Long): String =
    runCatching { ARCHIVE_TIMESTAMP_FORMATTER.format(Instant.ofEpochSecond(seconds)) }.getOrDefault(seconds.toString())
