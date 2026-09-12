package pm.antani.resentin.ui.directory

import pm.antani.resentin.ui.theme.ResentinSpacing

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import pm.antani.resentin.ui.common.ResentinFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import pm.antani.resentin.R
import pm.antani.resentin.irc.canonicalTarget
import pm.antani.resentin.net.dto.DirectoryEntryDto
import pm.antani.resentin.net.dto.FeaturedChannelDto
import pm.antani.resentin.ui.common.MircText
import pm.antani.resentin.ui.common.LocalDensityScale
import pm.antani.resentin.ui.common.ResentinHeaderAction
import pm.antani.resentin.ui.common.ResentinEmptyState
import pm.antani.resentin.ui.common.ResentinErrorState
import pm.antani.resentin.ui.common.ResentinInlineLoadingState
import pm.antani.resentin.ui.common.ResentinLoadingState
import pm.antani.resentin.ui.common.ResentinStateBanner
import pm.antani.resentin.ui.common.ResentinStateTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(
    viewModel: DirectoryViewModel,
    networkSlug: String,
    onBack: () -> Unit,
    onJoined: (channelName: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(networkSlug) {
        viewModel.refresh()
    }

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
                    ResentinHeaderAction(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                },
                actions = {
                    ResentinHeaderAction(
                        onClick = viewModel::refresh,
                        icon = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh),
                        loading = state.isRefreshing,
                        iconTint = if (state.status == "stale") MaterialTheme.colorScheme.error else null,
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                ),
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.directory_search_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                    IconButton(onClick = viewModel::search, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.directory_search_hint))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ResentinFilterChip(
                    selected = state.sort == "users",
                    onClick = { viewModel.setSort("users") },
                    label = { Text(stringResource(R.string.directory_sort_users)) },
                )
                Spacer(Modifier.width(8.dp))
                ResentinFilterChip(
                    selected = state.sort == "name",
                    onClick = { viewModel.setSort("name") },
                    label = { Text(stringResource(R.string.directory_sort_name)) },
                )
            }
            val hasContent = state.entries.isNotEmpty() || state.featured.isNotEmpty()
            DirectoryStatusLine(
                status = state.status,
                capturedAt = state.capturedAt,
                error = if (hasContent) state.error ?: state.featuredError else null,
            )
            Box(Modifier.fillMaxSize()) {
                val loadError = state.error ?: state.featuredError
                when {
                    !hasContent && (state.isLoading || state.isFeaturedLoading || state.isRefreshing) -> {
                        ResentinLoadingState(
                            title = stringResource(R.string.directory_loading_title),
                            description = stringResource(R.string.directory_loading_description),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    !hasContent && loadError != null -> {
                        ResentinErrorState(
                            icon = Icons.Outlined.WifiOff,
                            title = stringResource(R.string.directory_error_title),
                            description = loadError,
                            actionLabel = stringResource(R.string.home_connection_retry),
                            onRetry = viewModel::refresh,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    !hasContent -> {
                        ResentinEmptyState(
                            icon = Icons.Outlined.Search,
                            title = stringResource(R.string.directory_empty),
                            description = stringResource(R.string.directory_empty_description),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        DirectoryContent(
                            state = state,
                            onChannelClick = viewModel::joinChannel,
                            onLoadMore = viewModel::loadMore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DirectoryContent(
    state: DirectoryUiState,
    onChannelClick: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val featuredNames = remember(state.featured) {
        state.featured.map { canonicalTarget(it.name) }.toSet()
    }
    val directoryEntriesByName = remember(state.entries) {
        state.entries.associateBy { canonicalTarget(it.name) }
    }
    val normalEntries = state.entries.filterNot { canonicalTarget(it.name) in featuredNames }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp * LocalDensityScale.current),
    ) {
        if (state.isFeaturedLoading && state.featured.isEmpty()) {
            item(key = "featured-loading") {
                ResentinInlineLoadingState(stringResource(R.string.directory_featured_loading))
            }
        }
        if (state.featured.isNotEmpty()) {
            item(key = "featured-header") {
                Text(
                    stringResource(R.string.directory_featured_title).uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("directory-featured-section")
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            items(state.featured, key = { "featured:" + it.name }) { featured ->
                FeaturedChannelRow(
                    featured = featured,
                    directoryEntry = directoryEntriesByName[canonicalTarget(featured.name)],
                    isJoined = isJoinedChannel(featured.name, state.joinedChannels),
                    onClick = { onChannelClick(featured.name) },
                )
            }
        }
        if (normalEntries.isNotEmpty()) {
            if (state.featured.isNotEmpty()) {
                item(key = "all-channels-header") {
                    Text(
                        stringResource(R.string.directory_all_channels).uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.8.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
            items(normalEntries, key = { it.name }) { entry ->
                DirectoryRow(entry, onClick = { onChannelClick(entry.name) })
            }
        }
        if (state.nextCursor != null) {
            item(key = "load-more") {
                LoadMoreRow(isLoading = state.isLoadingMore, onClick = onLoadMore)
            }
        }
    }
}

@Composable
private fun FeaturedChannelRow(
    featured: FeaturedChannelDto,
    directoryEntry: DirectoryEntryDto?,
    isJoined: Boolean,
    onClick: () -> Unit,
) {
    val description = directoryEntry?.topic?.takeIf { it.isNotBlank() }
        ?: featured.description?.takeIf { it.isNotBlank() }
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
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = stringResource(R.string.directory_featured),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(featured.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                description?.let {
                    MircText(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                directoryEntry?.let { entry ->
                    Text(
                        stringResource(R.string.directory_sort_users) + ": ${entry.userCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onClick,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.testTag("directory-featured-action"),
            ) {
                Text(stringResource(if (isJoined) R.string.directory_featured_open else R.string.directory_featured_join))
            }
        }
    }
}

@Composable
private fun DirectoryStatusLine(
    status: String,
    capturedAt: String?,
    error: String?,
) {
    when {
        error != null -> ResentinStateBanner(
            icon = Icons.Outlined.WifiOff,
            title = stringResource(R.string.ui_error_title),
            description = error,
            tone = ResentinStateTone.ERROR,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        status == "stale" -> ResentinStateBanner(
            icon = Icons.Outlined.WifiOff,
            title = stringResource(R.string.directory_stale_title),
            description = capturedAt?.let { stringResource(R.string.directory_stale_description, formatIsoTimestamp(it)) }
                ?: stringResource(R.string.ui_show_saved_content_description),
            tone = ResentinStateTone.OFFLINE,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        status == "refreshing" -> ResentinInlineLoadingState(
            title = stringResource(R.string.directory_refreshing),
        )
        capturedAt != null -> Text(
            stringResource(R.string.directory_captured_at_label, formatIsoTimestamp(capturedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .padding(horizontal = 4.dp, vertical = 6.dp * LocalDensityScale.current),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.featured) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.Star,
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
                    enableLinks = false,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                entry.userCount.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ResentinSpacing.small, vertical = ResentinSpacing.xSmall),
            )
        }
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
