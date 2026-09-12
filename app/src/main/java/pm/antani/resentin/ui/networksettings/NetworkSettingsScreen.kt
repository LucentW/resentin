package pm.antani.resentin.ui.networksettings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pm.antani.resentin.R
import pm.antani.resentin.ui.common.ResentinFilterChip
import pm.antani.resentin.ui.common.ResentinErrorState
import pm.antani.resentin.ui.common.ResentinHeaderAction
import pm.antani.resentin.ui.common.ResentinLoadingState
import pm.antani.resentin.ui.common.ResentinStateBanner
import pm.antani.resentin.ui.common.ResentinStateTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(viewModel: NetworkSettingsViewModel, onBack: () -> Unit, onArchiveClick: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::uploadAvatar)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.slug) },
                navigationIcon = {
                    ResentinHeaderAction(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> ResentinLoadingState(
                    title = stringResource(R.string.network_settings_loading_title),
                    description = stringResource(R.string.network_settings_loading_description),
                    modifier = Modifier.align(Alignment.Center),
                )
                !state.networkFound -> ResentinErrorState(
                    icon = Icons.Outlined.WifiOff,
                    title = stringResource(R.string.network_settings_missing_title),
                    description = stringResource(R.string.network_settings_missing_description),
                    actionLabel = stringResource(R.string.cd_back),
                    onRetry = onBack,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ResentinSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.network_settings_connected),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = state.connected, onCheckedChange = { viewModel.toggleConnection() })
                        }
                    }
                    ResentinSectionCard {
                        OutlinedTextField(
                            value = state.nick,
                            onValueChange = viewModel::onNickChange,
                            label = { Text(stringResource(R.string.network_settings_nick_label)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.ident,
                            onValueChange = viewModel::onIdentChange,
                            label = { Text(stringResource(R.string.network_settings_ident_label)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.realname,
                            onValueChange = viewModel::onRealnameChange,
                            label = { Text(stringResource(R.string.network_settings_realname_label)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ResentinSectionCard {
                        Text(
                            stringResource(R.string.network_settings_profile_label).uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.profileAge,
                            onValueChange = viewModel::onProfileAgeChange,
                            label = { Text(stringResource(R.string.network_settings_profile_age_label)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.network_settings_profile_gender_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val genderOptions = listOf(
                                "" to R.string.network_settings_profile_gender_unset,
                                "male" to R.string.network_settings_profile_gender_male,
                                "female" to R.string.network_settings_profile_gender_female,
                                "nonbinary" to R.string.network_settings_profile_gender_nonbinary,
                            )
                            genderOptions.forEach { (value, labelRes) ->
                                ResentinFilterChip(
                                    selected = state.profileGender == value,
                                    onClick = { viewModel.onProfileGenderChange(value) },
                                    label = { Text(stringResource(labelRes)) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.profileLocation,
                            onValueChange = viewModel::onProfileLocationChange,
                            label = { Text(stringResource(R.string.network_settings_profile_location_label)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.profileLanguages,
                            onValueChange = viewModel::onProfileLanguagesChange,
                            label = { Text(stringResource(R.string.network_settings_profile_languages_label)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.profileCustom,
                            onValueChange = viewModel::onProfileCustomChange,
                            label = { Text(stringResource(R.string.network_settings_profile_custom_label)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.network_settings_profile_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ResentinSectionCard {
                        Text(
                            stringResource(R.string.network_settings_avatar_label).uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val avatarBitmap = state.avatarBitmap
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.medium),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                OutlinedButton(
                                    onClick = { avatarPicker.launch("image/*") },
                                    enabled = !state.avatarUploading,
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text(stringResource(R.string.network_settings_avatar_pick))
                                }
                                if (state.avatarUrl != null) {
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = viewModel::deleteAvatar,
                                        enabled = !state.avatarUploading,
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Text(stringResource(R.string.network_settings_avatar_remove))
                                    }
                                }
                            }
                            if (state.avatarUploading) {
                                Spacer(Modifier.width(12.dp))
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.network_settings_avatar_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.avatarError?.let { error ->
                            ResentinStateBanner(
                                icon = Icons.Outlined.WifiOff,
                                title = stringResource(R.string.ui_error_title),
                                description = error,
                                tone = ResentinStateTone.ERROR,
                            )
                        }
                    }
                    ResentinSectionCard {
                        Text(
                            stringResource(R.string.network_settings_perform_label).uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.performList,
                            onValueChange = viewModel::onPerformChange,
                            placeholder = { Text(stringResource(R.string.network_settings_perform_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            minLines = 4,
                        )
                    }
                    state.error?.let { error ->
                        ResentinStateBanner(
                            icon = Icons.Outlined.WifiOff,
                            title = stringResource(R.string.ui_error_title),
                            description = error,
                            tone = ResentinStateTone.ERROR,
                        )
                    }
                    if (state.saved) {
                        Text(stringResource(R.string.network_settings_saved), color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isSaving,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.network_settings_save))
                    }
                    OutlinedButton(
                        onClick = onArchiveClick,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.network_settings_archive))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResentinSectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            content()
        }
    }
}
