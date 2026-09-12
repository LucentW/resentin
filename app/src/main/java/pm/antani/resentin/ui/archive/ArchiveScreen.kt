package pm.antani.resentin.ui.archive

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import pm.antani.resentin.R
import pm.antani.resentin.net.dto.ArchiveEntryDto
import pm.antani.resentin.ui.common.ResentinHeaderAction
import pm.antani.resentin.ui.common.LocalDensityScale
import pm.antani.resentin.ui.theme.ResentinSpacing
import pm.antani.resentin.ui.common.ResentinEmptyState
import pm.antani.resentin.ui.common.ResentinErrorState
import pm.antani.resentin.ui.common.ResentinStateBanner
import pm.antani.resentin.ui.common.ResentinStateTone
import pm.antani.resentin.ui.common.ResentinLoadingState

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
                title = {
                    Text(
                        stringResource(R.string.archive_title, networkSlug),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    ResentinHeaderAction(
                        onClick = viewModel::load,
                        icon = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh),
                        enabled = !state.isLoading,
                        loading = state.isLoading,
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.entries.isEmpty() -> {
                    ResentinLoadingState(
                        title = stringResource(R.string.archive_loading_title),
                        description = stringResource(R.string.archive_loading_description),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                state.entries.isEmpty() && state.error != null -> {
                    ResentinErrorState(
                        icon = Icons.Outlined.WifiOff,
                        title = stringResource(R.string.home_connection_error_title),
                        description = stringResource(R.string.archive_error_description),
                        actionLabel = stringResource(R.string.home_connection_retry),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                state.entries.isEmpty() -> {
                    ResentinEmptyState(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = stringResource(R.string.archive_empty_title),
                        description = stringResource(R.string.archive_empty_description),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = ResentinSpacing.large, end = ResentinSpacing.large, top = ResentinSpacing.small, bottom = ResentinSpacing.xLarge),
                        verticalArrangement = Arrangement.spacedBy(ResentinSpacing.xSmall * LocalDensityScale.current),
                    ) {
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
            if (state.error != null && state.entries.isNotEmpty()) {
                ResentinStateBanner(
                    icon = Icons.Outlined.WifiOff,
                    title = stringResource(R.string.ui_error_title),
                    description = state.error.orEmpty(),
                    tone = ResentinStateTone.ERROR,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }

    state.pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(R.string.archive_delete_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = { Text(stringResource(R.string.archive_delete_confirm, entry.target)) },
            confirmButton = {
                TextButton(onClick = viewModel::deleteConfirmed) {
                    Text(
                        stringResource(R.string.archive_delete_action),
                        color = MaterialTheme.colorScheme.error,
                    )
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
    val isQuery = entry.kind == "query"
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = ResentinSpacing.medium, vertical = ResentinSpacing.medium * LocalDensityScale.current),
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
                        contentDescription = if (isQuery) {
                            stringResource(R.string.cd_direct_message)
                        } else {
                            null
                        },
                        modifier = Modifier.size(18.dp),
                        tint = if (isQuery) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.width(ResentinSpacing.medium))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.target,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.archive_row_meta, entry.rowCount, formatEpochMillis(entry.lastActivity)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove))
            }
        }
    }
}

private val ARCHIVE_TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

// `last_activity` is `max(m.server_time)` — `server_time` is epoch MILLISECONDS
// (see grappa's `Grappa.Scrollback.Message` schema doc), not seconds.
private fun formatEpochMillis(millis: Long): String =
    runCatching { ARCHIVE_TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(millis)) }.getOrDefault(millis.toString())
