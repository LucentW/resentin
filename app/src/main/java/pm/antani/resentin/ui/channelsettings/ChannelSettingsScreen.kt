package pm.antani.resentin.ui.channelsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R

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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
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
