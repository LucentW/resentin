package pm.antani.resentin.ui.channelsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import pm.antani.resentin.net.dto.BanlistEntryDto

private val LIST_MODE_FALLBACK = listOf("b", "e", "I", "q")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingsScreen(
    viewModel: ChannelSettingsViewModel,
    title: String,
    onBack: () -> Unit,
    onParted: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.parted) {
        if (state.parted) onParted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item {
                Text(stringResource(R.string.channel_settings_topic_label), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = state.topic,
                    onValueChange = viewModel::onTopicChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }
                if (state.saved) {
                    Text(stringResource(R.string.channel_settings_topic_updated), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick = viewModel::saveTopic, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.channel_settings_update_topic))
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.channel_settings_modes_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            items(SIMPLE_TOGGLE_MODES) { letter ->
                val checked = state.modes.modes.any { it.firstOrNull() == letter }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+$letter", modifier = Modifier.weight(1f))
                    Switch(
                        checked = checked,
                        onCheckedChange = { viewModel.toggleSimpleMode(letter) },
                        enabled = state.isPrivileged,
                    )
                }
            }
            if (state.isPrivileged) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.rawModeInput,
                            onValueChange = viewModel::onRawModeInputChange,
                            placeholder = { Text(stringResource(R.string.channel_settings_raw_mode_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = viewModel::applyRawMode, enabled = state.rawModeInput.isNotBlank()) {
                            Text(stringResource(R.string.channel_settings_apply_mode))
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.channel_settings_lists_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val letters = state.listModeLetters.ifEmpty { LIST_MODE_FALLBACK }
                LazyRow {
                    items(letters) { letter ->
                        FilterChip(
                            selected = state.activeListMode == letter,
                            onClick = { viewModel.selectListMode(letter) },
                            label = { Text(listModeLabel(letter)) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.banlistLoading) {
                    CircularProgressIndicator(Modifier.height(24.dp))
                } else if (state.banlistEntries.isEmpty()) {
                    Text(stringResource(R.string.channel_settings_list_empty), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(state.banlistEntries, key = { it.mask }) { entry ->
                BanlistRow(
                    entry = entry,
                    canRemove = state.isPrivileged,
                    onRemove = { viewModel.removeListModeEntry(entry.mask) },
                )
            }
            if (state.isPrivileged) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.newMaskInput,
                            onValueChange = viewModel::onNewMaskChange,
                            placeholder = { Text(stringResource(R.string.channel_settings_new_mask_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = viewModel::addListModeEntry, enabled = state.newMaskInput.isNotBlank()) {
                            Text(stringResource(R.string.channel_settings_add_mask))
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = viewModel::part,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.channel_settings_part))
                }
            }
        }
    }
}

@Composable
private fun BanlistRow(entry: BanlistEntryDto, canRemove: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.mask, style = MaterialTheme.typography.bodyLarge)
            val meta = listOfNotNull(
                entry.setter?.let { stringResource(R.string.channel_settings_list_set_by, it) },
                entry.setTs?.let { formatEpochSeconds(it) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove))
            }
        }
    }
}

private fun listModeLabel(letter: String): String = when (letter) {
    "b" -> "Ban (+b)"
    "e" -> "Exempt (+e)"
    "I" -> "Invite (+I)"
    "q" -> "Quiet (+q)"
    "z" -> "Restrict (+z)"
    else -> "+$letter"
}

private val LIST_ENTRY_TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private fun formatEpochSeconds(seconds: Long): String =
    runCatching { LIST_ENTRY_TIMESTAMP_FORMATTER.format(Instant.ofEpochSecond(seconds)) }.getOrDefault(seconds.toString())
