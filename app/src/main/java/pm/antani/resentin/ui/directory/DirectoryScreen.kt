package pm.antani.resentin.ui.directory

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
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
import pm.antani.resentin.net.dto.DirectoryEntryDto
import pm.antani.resentin.ui.common.MircText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(
    viewModel: DirectoryViewModel,
    networkSlug: String,
    onBack: () -> Unit,
    onJoined: (channelName: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.joined) {
        state.joined?.let {
            onJoined(it)
            viewModel.consumeJoined()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.directory_title, networkSlug)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh),
                                tint = if (state.status == "stale") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.directory_search_hint)) },
                    singleLine = true,
                )
                IconButton(onClick = viewModel::search) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.directory_search_hint))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                FilterChip(
                    selected = state.sort == "users",
                    onClick = { viewModel.setSort("users") },
                    label = { Text(stringResource(R.string.directory_sort_users)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = state.sort == "name",
                    onClick = { viewModel.setSort("name") },
                    label = { Text(stringResource(R.string.directory_sort_name)) },
                )
            }
            StatusLine(status = state.status, capturedAt = state.capturedAt)
            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.entries.isEmpty() -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    state.entries.isEmpty() -> {
                        Text(stringResource(R.string.directory_empty), modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(state.entries, key = { it.name }) { entry ->
                                DirectoryRow(entry, onClick = { viewModel.joinChannel(entry.name) })
                            }
                            if (state.nextCursor != null) {
                                item(key = "load-more") {
                                    LoadMoreRow(isLoading = state.isLoadingMore, onClick = viewModel::loadMore)
                                }
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
    }
}

@Composable
private fun StatusLine(status: String, capturedAt: String?) {
    val text = when {
        status == "refreshing" -> stringResource(R.string.directory_refreshing)
        capturedAt != null -> stringResource(R.string.directory_captured_at_label, formatIsoTimestamp(capturedAt))
        else -> null
    }
    text?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (status == "stale") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DirectoryRow(entry: DirectoryEntryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (entry.featured) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(R.string.directory_featured),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            entry.topic?.takeIf { it.isNotBlank() }?.let {
                MircText(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            entry.userCount.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadMoreRow(isLoading: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Button(onClick = onClick) {
                Text(stringResource(R.string.directory_load_more))
            }
        }
    }
}

private val DIRECTORY_TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private fun formatIsoTimestamp(iso: String): String =
    runCatching { DIRECTORY_TIMESTAMP_FORMATTER.format(Instant.parse(iso)) }.getOrDefault(iso)
