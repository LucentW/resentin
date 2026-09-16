package pm.antani.resentin.ui.mentions

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import pm.antani.resentin.net.dto.MentionsBundleDto
import pm.antani.resentin.ui.common.MircText

// Mentions-while-away pseudo-window — cicchetto MentionsWindow parity. Not a
// real channel (no topic bar, no compose box): header with count + away
// interval/reason, rows grouped by channel, tap jumps to the source message.
// A link tap inside a row opens the link (MircText's own handling wins over
// the row tap, same link-wins policy as cicchetto #220).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentionsScreen(
    viewModel: MentionsViewModel,
    networkSlug: String,
    onBack: () -> Unit,
    onMentionClick: (networkSlug: String, channel: String, serverTime: Long) -> Unit,
) {
    val bundle by viewModel.bundle.collectAsState()
    val current = bundle
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.chat_dialog_close))
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.mentions_title, networkSlug),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
    ) { padding ->
        if (current == null) {
            MentionsEmpty(
                modifier = Modifier.fillMaxSize().padding(padding),
                onBack = onBack,
            )
        } else {
            MentionsBody(
                bundle = current,
                modifier = Modifier.fillMaxSize().padding(padding),
                onMentionClick = { channel, serverTime -> onMentionClick(current.network, channel, serverTime) },
                onDismiss = { viewModel.dismiss(); onBack() },
            )
        }
    }
}

@Composable
private fun MentionsEmpty(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.MarkChatRead,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.mentions_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.chat_dialog_close))
        }
    }
}

@Composable
private fun MentionsBody(
    bundle: MentionsBundleDto,
    modifier: Modifier = Modifier,
    onMentionClick: (channel: String, serverTime: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = remember(bundle) { groupMentionsByChannel(bundle.messages) }
    Column(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.mentions_summary, bundle.messages.size, groups.size),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val interval = remember(bundle) {
                listOfNotNull(
                    bundle.awayStartedAt?.takeIf { it.isNotBlank() },
                    bundle.awayEndedAt?.takeIf { it.isNotBlank() },
                ).joinToString(" – ")
            }
            if (interval.isNotBlank() || !bundle.awayReason.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (interval.isNotBlank()) {
                        Text(
                            text = interval,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    bundle.awayReason?.takeIf { it.isNotBlank() }?.let { reason ->
                        if (interval.isNotBlank()) {
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MircText(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            enableLinks = false,
                        )
                    }
                }
            }
        }
        if (bundle.messages.isEmpty()) {
            Text(
                text = stringResource(R.string.mentions_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.channel }) { group ->
                    Column {
                        Text(
                            text = group.channel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        group.rows.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMentionClick(group.channel, row.serverTime) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = DateFormat.format("HH:mm:ss", row.serverTime).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp, top = 2.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "<${row.sender}>",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    MircText(
                                        text = row.body.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_mentions_dismiss))
            }
        }
    }
}
