package pm.antani.resentin.ui.appsettings

import pm.antani.resentin.ui.theme.ResentinSpacing

import android.Manifest
import android.app.LocaleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import pm.antani.resentin.ui.common.ResentinFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import org.unifiedpush.android.connector.UnifiedPush
import pm.antani.resentin.R
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.data.prefs.AppFontFamily
import pm.antani.resentin.data.prefs.MessageDensity
import pm.antani.resentin.data.prefs.ReplyStyle
import pm.antani.resentin.data.prefs.ThemeMode
import pm.antani.resentin.net.dto.PushSubscriptionSummaryDto
import pm.antani.resentin.net.dto.VhostOptionDto
import pm.antani.resentin.ui.chat.CreditsScreen
import pm.antani.resentin.ui.common.LocalDensityScale
import pm.antani.resentin.ui.common.ResentinHeaderAction
import pm.antani.resentin.ui.theme.toComposeFontFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// #348 on grappa-irc — the auto-away ladder the control offers. Deliberately coarse:
// these are the answers to "how long before my friends see me as away", anything
// between the rungs is what the custom entry is for. Mirrors cic's SettingsDrawer.
private val AUTO_AWAY_PRESETS = listOf(
    60 to R.string.settings_auto_away_1min,
    300 to R.string.settings_auto_away_5min,
    600 to R.string.settings_auto_away_10min,
    1800 to R.string.settings_auto_away_30min,
    3600 to R.string.settings_auto_away_1h,
)
private val AUTO_AWAY_PRESET_SECONDS = AUTO_AWAY_PRESETS.map { it.first }.toSet()

// Text-size ladder for the slider below — labels need no translation (XXS–XXL are
// universal), the scale applies app-wide through ResentinTheme.
private val FONT_SCALES = listOf(0.7f, 0.8f, 0.9f, 1f, 1.15f, 1.22f, 1.3f)
private val FONT_SCALE_LABELS = listOf("XXS", "XS", "S", "M", "L", "XL", "XXL")

// Line-height ladder (multiplier on top of the font size) — down to 0.7 for a
// truly compact reading, up to 1.5 for a relaxed one. 1.0 is previous behavior.
private val LINE_HEIGHT_SCALES = listOf(0.7f, 0.8f, 0.9f, 1f, 1.15f, 1.3f, 1.5f)

/** Hub-and-spoke navigation inside Settings: the hub lists the groups, a tap
 * opens that group's own sub-screen. Local state (not a NavHost route) — the
 * system back button returns to the hub via BackHandler below. */
private enum class SettingsSection {
    APPEARANCE,
    CHAT,
    NOTIFICATIONS,
    PRESENCE,
    IDENTITY,
    COMMANDS,
    DATA,
}

@Composable
private fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.APPEARANCE -> Icons.Outlined.Palette
    SettingsSection.CHAT -> Icons.Outlined.ChatBubbleOutline
    SettingsSection.NOTIFICATIONS -> Icons.Outlined.Notifications
    SettingsSection.PRESENCE -> Icons.Outlined.Schedule
    SettingsSection.IDENTITY -> Icons.Outlined.Person
    SettingsSection.COMMANDS -> Icons.Outlined.Terminal
    SettingsSection.DATA -> Icons.Outlined.Storage
}

@Composable
private fun SettingsSection.title(): String = when (this) {
    SettingsSection.APPEARANCE -> stringResource(R.string.settings_group_appearance)
    SettingsSection.CHAT -> stringResource(R.string.settings_group_chat)
    SettingsSection.NOTIFICATIONS -> stringResource(R.string.settings_group_notifications)
    SettingsSection.PRESENCE -> stringResource(R.string.settings_group_presence)
    SettingsSection.IDENTITY -> stringResource(R.string.settings_group_identity)
    SettingsSection.COMMANDS -> stringResource(R.string.settings_group_commands)
    SettingsSection.DATA -> stringResource(R.string.settings_group_data)
}

@Composable
private fun SettingsSection.description(): String = when (this) {
    SettingsSection.APPEARANCE -> stringResource(R.string.settings_group_appearance_desc)
    SettingsSection.CHAT -> stringResource(R.string.settings_group_chat_desc)
    SettingsSection.NOTIFICATIONS -> stringResource(R.string.settings_group_notifications_desc)
    SettingsSection.PRESENCE -> stringResource(R.string.settings_group_presence_desc)
    SettingsSection.IDENTITY -> stringResource(R.string.settings_group_identity_desc)
    SettingsSection.COMMANDS -> stringResource(R.string.settings_group_commands_desc)
    SettingsSection.DATA -> stringResource(R.string.settings_group_data_desc)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(viewModel: AppSettingsViewModel, onBack: () -> Unit, onAdminClick: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    val stayConnected by viewModel.stayConnected.collectAsState()
    val autoAwayDebounceSeconds by viewModel.autoAwayDebounceSeconds.collectAsState()
    val pushEnabled by viewModel.pushEnabled.collectAsState()
    val pushDecryptionFailureAt by viewModel.pushDecryptionFailureAt.collectAsState()
    val chatDisplayMode by viewModel.chatDisplayMode.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val showHostmaskInEvents by viewModel.showHostmaskInEvents.collectAsState()
    val smartPresenceFilter by viewModel.smartPresenceFilter.collectAsState()
    val unreadFirst by viewModel.unreadFirst.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val fontFamily by viewModel.fontFamily.collectAsState()
    val chatFontFamily by viewModel.chatFontFamily.collectAsState()
    val fontFamilyOptions = appFontFamilyOptions()
    val messageDensity by viewModel.messageDensity.collectAsState()
    val lineHeightScale by viewModel.lineHeightScale.collectAsState()
    val replyStyle by viewModel.replyStyle.collectAsState()
    val messageDbSizeBytes by viewModel.messageDbSizeBytes.collectAsState()
    var showClearMessagesConfirm by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
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

    val noDistributorMessage = stringResource(R.string.settings_push_no_distributor)
    val autoAwayInvalidInputMessage = stringResource(R.string.settings_auto_away_invalid_input)

    fun linkDistributorAndEnable() {
        // tryUseCurrentOrDefaultDistributor reuses the already-saved distributor without
        // needing an Activity; it only falls into the Activity-requiring deeplink path
        // (`context is Activity`, true here since LocalContext.current is the hosting
        // MainActivity) the first time, or after the saved distributor was uninstalled.
        UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { success ->
            if (success) {
                viewModel.enablePushAfterDistributorLinked()
            } else {
                viewModel.reportPushLinkFailed(noDistributorMessage)
            }
        }
    }

    val pushPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) linkDistributorAndEnable() }

    fun onPushEnabledChange(enabled: Boolean) {
        if (!enabled) {
            viewModel.disablePush()
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pushPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            linkDistributorAndEnable()
        }
    }

    // On API 33+ the platform's own LocaleManager is the source of truth for the
    // per-app language (it's what drives the system Settings > Apps > Resentin >
    // Language picker too, via android:localeConfig in the manifest) — going through
    // AppCompatDelegate.setApplicationLocales() instead silently no-ops here: its
    // API 33+ bridge to LocaleManager apparently needs an AppCompatActivity to actually
    // persist the change, which MainActivity (a plain ComponentActivity, for Compose)
    // isn't. AppCompatDelegate is kept only as the API <33 fallback, where it's the
    // sole mechanism available (no platform LocaleManager to call directly).
    var currentLanguageTag by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)?.language
            } else {
                AppCompatDelegate.getApplicationLocales().get(0)?.language
            },
        )
    }

    fun setLanguage(languageTag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (languageTag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(languageTag)
        } else {
            AppCompatDelegate.setApplicationLocales(
                if (languageTag == null) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(languageTag)
                },
            )
        }
        currentLanguageTag = languageTag
    }

    var selectedSection by remember { mutableStateOf<SettingsSection?>(null) }
    BackHandler(enabled = selectedSection != null) { selectedSection = null }
    val section = selectedSection
    val showIdentity = state.vhostOptions.isNotEmpty() || state.vhostError != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (section == null) {
                        Text(
                            stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        // Detail header carries the group's icon + description (home
                        // top-bar style), so the card below renders content only.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = section.icon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Spacer(Modifier.width(ResentinSpacing.medium))
                            Column {
                                Text(
                                    section.title(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    section.description(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    ResentinHeaderAction(
                        onClick = { if (section == null) onBack() else selectedSection = null },
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = ResentinSpacing.large,
                end = ResentinSpacing.large,
                top = ResentinSpacing.small,
                bottom = ResentinSpacing.xLarge,
            ),
            verticalArrangement = Arrangement.spacedBy(ResentinSpacing.medium * LocalDensityScale.current),
        ) {
            if (section == null) {
                item {
                    SettingsHubCard(
                        showIdentity = showIdentity,
                        isAdmin = state.isAdmin,
                        onSelect = { selectedSection = it },
                        onAdminClick = onAdminClick,
                        onCreditsClick = { showCredits = true },
                    )
                }
            }
            if (section == SettingsSection.APPEARANCE) {
            item {
                SettingsGroupCard(
                    showHeader = false,
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_group_appearance),
                    description = stringResource(R.string.settings_group_appearance_desc),
                ) {
                    SettingsBlockLabel(
                        text = stringResource(R.string.settings_theme),
                        icon = Icons.Outlined.Palette,
                    )
                    Spacer(Modifier.height(ResentinSpacing.small))
                    SettingsDropdown(
                        selected = themeMode,
                        options = listOf(
                            SettingsDropdownOption(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system)),
                            SettingsDropdownOption(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light)),
                            SettingsDropdownOption(ThemeMode.DARK, stringResource(R.string.settings_theme_dark)),
                        ),
                        onSelected = viewModel::setThemeMode,
                    )
                    SettingsRowDivider()
                    SettingsBlockLabel(
                        text = stringResource(R.string.settings_language),
                        icon = Icons.Outlined.Language,
                    )
                    Spacer(Modifier.height(ResentinSpacing.small))
                    SettingsDropdown(
                        selected = currentLanguageTag,
                        options = listOf(
                            SettingsDropdownOption(null, stringResource(R.string.settings_language_system)),
                            SettingsDropdownOption("it", stringResource(R.string.settings_language_italian)),
                            SettingsDropdownOption("en", stringResource(R.string.settings_language_english)),
                        ),
                        onSelected = ::setLanguage,
                    )
                    SettingsRowDivider()
                    SettingsBlockLabel(
                        text = stringResource(R.string.settings_font_family),
                        icon = Icons.Outlined.TextFields,
                    )
                    Text(
                        text = stringResource(R.string.settings_font_family_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(ResentinSpacing.small))
                    SettingsDropdown(
                        selected = fontFamily,
                        options = fontFamilyOptions,
                        onSelected = viewModel::setFontFamily,
                    )
                    SettingsRowDivider()
                    SettingsBlockLabel(
                        text = stringResource(R.string.settings_chat_font_family),
                        icon = Icons.Outlined.ChatBubbleOutline,
                    )
                    Text(
                        text = stringResource(R.string.settings_chat_font_family_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(ResentinSpacing.small))
                    SettingsDropdown(
                        selected = chatFontFamily,
                        options = fontFamilyOptions,
                        onSelected = viewModel::setChatFontFamily,
                    )
                    SettingsRowDivider()

                    // Fixed seven-stop slider (XXS–XXL): discrete writes, live theme preview.
                    // The live value sits in the header and only the two end labels are
                    // shown below: a full 7-label strip can never align with the stops,
                    // which sit inset by the thumb radius on both sides.
                    val scaleIndex = FONT_SCALES.indices.minByOrNull { kotlin.math.abs(FONT_SCALES[it] - fontScale) } ?: 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsBlockLabel(
                            text = stringResource(R.string.settings_font_size),
                            icon = Icons.Outlined.TextFields,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = FONT_SCALE_LABELS[scaleIndex],
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Slider(
                        value = scaleIndex.toFloat(),
                        onValueChange = { viewModel.setFontScale(FONT_SCALES[it.roundToInt()]) },
                        valueRange = 0f..(FONT_SCALES.size - 1).toFloat(),
                        steps = FONT_SCALES.size - 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("XXS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("XXL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(ResentinSpacing.xSmall))
                    Text("AaBbCc 123", style = MaterialTheme.typography.displaySmall)
                    Text(
                        stringResource(R.string.settings_font_size_preview),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingsRowDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsBlockLabel(
                            text = stringResource(R.string.settings_line_spacing),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_line_spacing_value,
                                (lineHeightScale * 100).roundToInt(),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(ResentinSpacing.small))
                    // Discrete seven-stop slider (70%–150%): same interaction as
                    // the font-size slider above, live theme preview below.
                    val lineIndex = LINE_HEIGHT_SCALES.indices.minByOrNull {
                        kotlin.math.abs(LINE_HEIGHT_SCALES[it] - lineHeightScale)
                    } ?: 3
                    Slider(
                        value = lineIndex.toFloat(),
                        onValueChange = { viewModel.setLineHeightScale(LINE_HEIGHT_SCALES[it.roundToInt()]) },
                        valueRange = 0f..(LINE_HEIGHT_SCALES.size - 1).toFloat(),
                        steps = LINE_HEIGHT_SCALES.size - 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("70%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("150%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(ResentinSpacing.xSmall))
                    Text(
                        stringResource(R.string.settings_line_spacing_preview),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingsRowDivider()
                    SettingsBlockLabel(text = stringResource(R.string.settings_density))
                    Spacer(Modifier.height(ResentinSpacing.small))
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        ResentinFilterChip(
                            selected = messageDensity == MessageDensity.COMPACT,
                            onClick = { viewModel.setMessageDensity(MessageDensity.COMPACT) },
                            label = { Text(stringResource(R.string.settings_density_compact)) },
                            modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                        )
                        ResentinFilterChip(
                            selected = messageDensity == MessageDensity.NORMAL,
                            onClick = { viewModel.setMessageDensity(MessageDensity.NORMAL) },
                            label = { Text(stringResource(R.string.settings_density_normal)) },
                            modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                        )
                        ResentinFilterChip(
                            selected = messageDensity == MessageDensity.COMFORTABLE,
                            onClick = { viewModel.setMessageDensity(MessageDensity.COMFORTABLE) },
                            label = { Text(stringResource(R.string.settings_density_comfortable)) },
                            modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                        )
                    }
                }
            }
            }
            if (section == SettingsSection.CHAT) {
            item {
                SettingsGroupCard(
                    showHeader = false,
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = stringResource(R.string.settings_group_chat),
                    description = stringResource(R.string.settings_group_chat_desc),
                ) {
                    SettingsBlockLabel(text = stringResource(R.string.settings_chat_display))
                    Spacer(Modifier.height(ResentinSpacing.small))
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        ResentinFilterChip(
                            selected = chatDisplayMode == ChatDisplayMode.BUBBLES,
                            onClick = { viewModel.setChatDisplayMode(ChatDisplayMode.BUBBLES) },
                            label = { Text(stringResource(R.string.settings_display_bubbles)) },
                            modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                        )
                        ResentinFilterChip(
                            selected = chatDisplayMode == ChatDisplayMode.IRC_LINE,
                            onClick = { viewModel.setChatDisplayMode(ChatDisplayMode.IRC_LINE) },
                            label = { Text(stringResource(R.string.settings_display_irc_line)) },
                            modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                        )
                    }
                    Text(
                        stringResource(R.string.settings_display_irc_line_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingsRowDivider()
                    SettingsBlockLabel(text = stringResource(R.string.settings_reply_style_title))
                    Spacer(Modifier.height(ResentinSpacing.small))
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        ResentinFilterChip(
                            selected = replyStyle == ReplyStyle.NICK,
                            onClick = { viewModel.setReplyStyle(ReplyStyle.NICK) },
                            label = { Text(stringResource(R.string.settings_reply_style_nick)) },
                            modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                        )
                        ResentinFilterChip(
                            selected = replyStyle == ReplyStyle.QUOTE,
                            onClick = { viewModel.setReplyStyle(ReplyStyle.QUOTE) },
                            label = { Text(stringResource(R.string.settings_reply_style_quote)) },
                            modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                        )
                        ResentinFilterChip(
                            selected = replyStyle == ReplyStyle.CUSTOM,
                            onClick = { viewModel.setReplyStyle(ReplyStyle.CUSTOM) },
                            label = { Text(stringResource(R.string.settings_reply_style_custom)) },
                            modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                        )
                    }
                    if (replyStyle == ReplyStyle.CUSTOM) {
                        Spacer(Modifier.height(ResentinSpacing.small))
                        OutlinedTextField(
                            value = state.replyCustomTemplate,
                            onValueChange = viewModel::onReplyCustomTemplateChange,
                            placeholder = { Text(stringResource(R.string.settings_reply_custom_hint)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.settings_reply_custom_placeholders_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(ResentinSpacing.small))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = viewModel::saveReplyCustomTemplate,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(stringResource(R.string.network_settings_save))
                            }
                            if (state.replyCustomTemplateSaved) {
                                Spacer(Modifier.width(ResentinSpacing.small))
                                Text(stringResource(R.string.settings_reply_custom_saved), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_show_seconds),
                        description = stringResource(R.string.settings_show_seconds_desc),
                        checked = showSeconds,
                        onCheckedChange = viewModel::setShowSeconds,
                    )
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_show_hostmask),
                        description = stringResource(R.string.settings_show_hostmask_desc),
                        checked = showHostmaskInEvents,
                        onCheckedChange = viewModel::setShowHostmaskInEvents,
                    )
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_smart_presence_filter),
                        description = stringResource(R.string.settings_smart_presence_filter_desc),
                        checked = smartPresenceFilter,
                        onCheckedChange = viewModel::setSmartPresenceFilter,
                    )
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_colored_nicklist),
                        description = null,
                        checked = state.displayPrefs.coloredNicklist,
                        onCheckedChange = { viewModel.toggleColoredNicklist() },
                    )
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_unread_first),
                        description = stringResource(R.string.settings_unread_first_desc),
                        checked = unreadFirst,
                        onCheckedChange = viewModel::setUnreadFirst,
                    )
                }
            }
            }
            if (section == SettingsSection.NOTIFICATIONS) {
            item {
                SettingsGroupCard(
                    showHeader = false,
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(R.string.settings_group_notifications),
                    description = stringResource(R.string.settings_group_notifications_desc),
                ) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_stay_connected),
                        description = stringResource(R.string.settings_stay_connected_desc),
                        checked = stayConnected,
                        onCheckedChange = ::onStayConnectedChange,
                    )
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_push_enabled),
                        description = stringResource(R.string.settings_push_enabled_desc),
                        checked = pushEnabled,
                        onCheckedChange = ::onPushEnabledChange,
                    )
                    state.pushError?.let { error ->
                        Spacer(Modifier.height(ResentinSpacing.xSmall))
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    pushDecryptionFailureAt?.let { epochMillis ->
                        Spacer(Modifier.height(ResentinSpacing.xSmall))
                        Text(
                            stringResource(R.string.settings_push_decryption_failure, formatEpochMillis(epochMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.pushSubscriptions.isNotEmpty()) {
                        Spacer(Modifier.height(ResentinSpacing.large))
                        Text(
                            stringResource(R.string.settings_push_devices),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        Spacer(Modifier.height(ResentinSpacing.xSmall))
                    }
                }
            }
            }
            if (section == SettingsSection.NOTIFICATIONS) {
            items(state.pushSubscriptions, key = { it.id }) { subscription ->
                PushSubscriptionRow(
                    subscription = subscription,
                    isThisDevice = subscription.id == state.ownPushSubscriptionId,
                    onRevoke = { viewModel.revokePushSubscription(subscription.id) },
                )
            }
            }
            if (section == SettingsSection.PRESENCE) {
            item {
                SettingsGroupCard(
                    showHeader = false,
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.settings_group_presence),
                    description = stringResource(R.string.settings_group_presence_desc),
                ) {
                    SettingsBlockLabel(text = stringResource(R.string.settings_auto_away_title))
                    Spacer(Modifier.height(ResentinSpacing.xSmall))
                    Text(
                        stringResource(R.string.settings_auto_away_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(ResentinSpacing.small))
                    val autoAwayIsCustomValue = autoAwayDebounceSeconds != null &&
                        autoAwayDebounceSeconds != 0 &&
                        autoAwayDebounceSeconds !in AUTO_AWAY_PRESET_SECONDS
                    val autoAwayShowCustom = state.autoAwayCustomMode || autoAwayIsCustomValue
                    FlowRow {
                        ResentinFilterChip(
                            selected = !autoAwayShowCustom && autoAwayDebounceSeconds == null,
                            onClick = { viewModel.onAutoAwayPresetSelected(null) },
                            label = { Text(stringResource(R.string.settings_auto_away_site_default)) },
                            modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                        )
                        ResentinFilterChip(
                            selected = !autoAwayShowCustom && autoAwayDebounceSeconds == 0,
                            onClick = { viewModel.onAutoAwayPresetSelected(0) },
                            label = { Text(stringResource(R.string.settings_auto_away_off)) },
                            modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                        )
                        AUTO_AWAY_PRESETS.forEach { (seconds, labelRes) ->
                            ResentinFilterChip(
                                selected = !autoAwayShowCustom && autoAwayDebounceSeconds == seconds,
                                onClick = { viewModel.onAutoAwayPresetSelected(seconds) },
                                label = { Text(stringResource(labelRes)) },
                                modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                            )
                        }
                        ResentinFilterChip(
                            selected = autoAwayShowCustom,
                            onClick = { viewModel.onAutoAwayCustomModeSelected() },
                            label = { Text(stringResource(R.string.settings_reply_style_custom)) },
                            modifier = Modifier.padding(end = ResentinSpacing.small, bottom = ResentinSpacing.small),
                        )
                    }
                    if (autoAwayShowCustom) {
                        OutlinedTextField(
                            value = if (state.autoAwayCustomMode) {
                                state.autoAwayCustomDraft
                            } else {
                                autoAwayDebounceSeconds?.toString().orEmpty()
                            },
                            onValueChange = viewModel::onAutoAwayCustomDraftChange,
                            label = { Text(stringResource(R.string.settings_auto_away_custom_seconds)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(ResentinSpacing.small))
                        Button(
                            onClick = { viewModel.saveAutoAwayCustomDraft(autoAwayInvalidInputMessage) },
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(stringResource(R.string.network_settings_save))
                        }
                    }
                    Text(
                        stringResource(R.string.settings_auto_away_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.autoAwaySavingError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                SettingsGroupCard(
                    showHeader = false,
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.settings_peer_profiles_title),
                    description = stringResource(R.string.settings_peer_profiles_desc),
                ) {
                    SettingsBlockLabel(text = stringResource(R.string.settings_peer_profiles_title))
                    Spacer(Modifier.height(ResentinSpacing.xSmall))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_peer_profiles_switch_label),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.showPeerProfiles,
                            enabled = !state.showPeerProfilesSaving,
                            onCheckedChange = { viewModel.toggleShowPeerProfiles() },
                        )
                    }
                    Spacer(Modifier.height(ResentinSpacing.small))
                    Text(
                        stringResource(R.string.settings_peer_profiles_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.showPeerProfilesError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            }
            if (showIdentity && section == SettingsSection.IDENTITY) {
                item {
                    SettingsGroupCard(
                        showHeader = false,
                        icon = Icons.Outlined.Person,
                        title = stringResource(R.string.settings_group_identity),
                        description = stringResource(R.string.settings_group_identity_desc),
                    ) {
                        Text(
                            stringResource(R.string.settings_vhost_title),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        Text(
                            stringResource(R.string.settings_vhost_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.vhostOptions.forEach { option ->
                            VhostRow(
                                option = option,
                                checked = option.address in state.vhostSelection,
                                onToggle = { viewModel.toggleVhostSelection(option.address) },
                            )
                        }
                        state.vhostError?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (section == SettingsSection.COMMANDS) {
            item {
                SettingsGroupCard(
                    showHeader = false,
                    icon = Icons.Outlined.Terminal,
                    title = stringResource(R.string.settings_group_commands),
                    description = stringResource(R.string.settings_group_commands_desc),
                ) {
                    SettingsBlockLabel(text = stringResource(R.string.settings_aliases))
                    Spacer(Modifier.height(ResentinSpacing.small))
                    state.aliases.entries.forEachIndexed { index, (name, expansion) ->
                        if (index > 0) SettingsRowDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    expansion,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.removeAlias(name) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove))
                            }
                        }
                    }
                    SettingsRowDivider()
                    OutlinedTextField(
                        value = state.newAliasName,
                        onValueChange = viewModel::onNewAliasNameChange,
                        label = { Text(stringResource(R.string.settings_alias_name_label)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(ResentinSpacing.small))
                    OutlinedTextField(
                        value = state.newAliasExpansion,
                        onValueChange = viewModel::onNewAliasExpansionChange,
                        label = { Text(stringResource(R.string.settings_alias_expansion_label)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(ResentinSpacing.small))
                    Button(
                        onClick = viewModel::addAlias,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_add_alias))
                    }
                    state.error?.let { error ->
                        Spacer(Modifier.height(ResentinSpacing.small))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                    val highlights by viewModel.highlightPatterns.collectAsState()
                    Spacer(Modifier.height(ResentinSpacing.large))
                    SettingsBlockLabel(text = stringResource(R.string.settings_highlights))
                    Text(
                        stringResource(R.string.settings_highlight_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(ResentinSpacing.small))
                    highlights?.forEachIndexed { index, pattern ->
                        if (index > 0) SettingsRowDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                pattern,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.removeHighlight(pattern) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove))
                            }
                        }
                    }
                    if (highlights != null) SettingsRowDivider()
                    OutlinedTextField(
                        value = state.newHighlight,
                        onValueChange = viewModel::onNewHighlightChange,
                        label = { Text(stringResource(R.string.settings_highlight_label)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(ResentinSpacing.small))
                    Button(
                        onClick = viewModel::addHighlight,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_add_highlight))
                    }
                    state.highlightError?.let { error ->
                        Spacer(Modifier.height(ResentinSpacing.small))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            }
            if (section == SettingsSection.DATA) {
            item {
                SettingsGroupCard(
                    showHeader = false,
                    icon = Icons.Outlined.Storage,
                    title = stringResource(R.string.settings_group_data),
                    description = stringResource(R.string.settings_group_data_desc),
                ) {
                    OutlinedButton(
                        onClick = { showClearMessagesConfirm = true },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_clear_messages, formatByteSize(messageDbSizeBytes)))
                    }
                }
            }
            }
        }
    }

    if (showCredits) {
        CreditsScreen(onClose = { showCredits = false })
    }

    if (showClearMessagesConfirm) {
        AlertDialog(
            onDismissRequest = { showClearMessagesConfirm = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(R.string.settings_clear_messages_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = { Text(stringResource(R.string.settings_clear_messages_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearMessageDatabase()
                        showClearMessagesConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.settings_clear_messages_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMessagesConfirm = false }) {
                    Text(stringResource(R.string.home_dialog_cancel))
                }
            },
        )
    }
}

private data class SettingsDropdownOption<T>(
    val value: T,
    val label: String,
    val fontFamily: FontFamily? = null,
)

@Composable
private fun <T> SettingsDropdown(
    selected: T,
    options: List<SettingsDropdownOption<T>>,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { it.value == selected } ?: options.first()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = ResentinSpacing.large, vertical = ResentinSpacing.medium),
        ) {
            Text(
                text = selectedOption.label,
                modifier = Modifier.weight(1f),
                fontFamily = selectedOption.fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(maxWidth),
            properties = PopupProperties(focusable = true),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, fontFamily = option.fontFamily) },
                    onClick = {
                        onSelected(option.value)
                        expanded = false
                    },
                    trailingIcon = if (option.value == selected) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun appFontFamilyOptions(): List<SettingsDropdownOption<AppFontFamily>> =
    AppFontFamily.entries.map { choice ->
        SettingsDropdownOption(
            value = choice,
            label = when (choice) {
                AppFontFamily.SYSTEM -> stringResource(R.string.settings_font_family_system)
                AppFontFamily.JETBRAINS_MONO -> stringResource(R.string.settings_font_family_jetbrains)
                AppFontFamily.FIRA_CODE -> stringResource(R.string.settings_font_family_fira)
                AppFontFamily.SOURCE_CODE_PRO -> stringResource(R.string.settings_font_family_source_code)
                AppFontFamily.IBM_PLEX_MONO -> stringResource(R.string.settings_font_family_ibm_plex)
                AppFontFamily.CASCADIA_CODE -> stringResource(R.string.settings_font_family_cascadia)
                AppFontFamily.HACK -> stringResource(R.string.settings_font_family_hack)
            },
            fontFamily = choice.toComposeFontFamily(),
        )
    }

/** The hub menu: one row per group, same row language as the home's channel rows
 * (40dp icon avatar, title + subtitle, chevron). Groups without content available
 * right now (e.g. Identità with no vhost options) are hidden, not disabled. */

@Composable
private fun SettingsHubCard(
    showIdentity: Boolean,
    isAdmin: Boolean,
    onSelect: (SettingsSection) -> Unit,
    onAdminClick: () -> Unit,
    onCreditsClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            val sections = buildList {
                add(SettingsSection.APPEARANCE)
                add(SettingsSection.CHAT)
                add(SettingsSection.NOTIFICATIONS)
                add(SettingsSection.PRESENCE)
                if (showIdentity) add(SettingsSection.IDENTITY)
                add(SettingsSection.COMMANDS)
                add(SettingsSection.DATA)
            }
            // Extra rows that aren't a SettingsSection: admin opens a separate screen
            // (not a filtered content panel of this one) and credits opens an overlay —
            // neither fits the section model, but both belong in the same top-level list.
            data class ExtraRow(val icon: ImageVector, val title: String, val description: String, val onClick: () -> Unit)
            val extraRows = buildList {
                if (isAdmin) {
                    add(
                        ExtraRow(
                            Icons.Outlined.AdminPanelSettings,
                            stringResource(R.string.settings_admin_panel),
                            stringResource(R.string.settings_admin_panel_desc),
                            onAdminClick,
                        ),
                    )
                }
                add(
                    ExtraRow(
                        Icons.Outlined.Celebration,
                        stringResource(R.string.settings_credits),
                        stringResource(R.string.settings_credits_desc),
                        onCreditsClick,
                    ),
                )
            }
            sections.forEachIndexed { index, target ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
                SettingsHubRow(
                    icon = target.icon(),
                    title = target.title(),
                    description = target.description(),
                    onClick = { onSelect(target) },
                )
            }
            extraRows.forEach { row ->
                HorizontalDivider(
                    modifier = Modifier.padding(start = 62.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                SettingsHubRow(icon = row.icon, title = row.title, description = row.description, onClick = row.onClick)
            }
        }
    }
}

@Composable
private fun SettingsHubRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 9.dp * LocalDensityScale.current, bottom = 9.dp * LocalDensityScale.current),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(ResentinSpacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A settings group: the same premium card language as the home's network cards
 * (28dp radius, tonal fill, hairline border), with an icon-avatar header in the
 * style of the home's network/channel rows. */
@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    title: String,
    description: String,
    showHeader: Boolean = true,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    start = 12.dp,
                    end = ResentinSpacing.large,
                    top = 12.dp * LocalDensityScale.current,
                    bottom = 12.dp * LocalDensityScale.current,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(ResentinSpacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp * LocalDensityScale.current)) {
                content()
            }
        }
    }
}

/** Sub-block label inside a group (Tema, Lingua, …) — same weight as a switch
 * row title, optionally with a small leading icon. */
@Composable
private fun SettingsBlockLabel(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(ResentinSpacing.small))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        )
    }
}

/** Title + optional description on the left, Switch on the right. */
@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Hairline separator between blocks/rows inside a group card. */
@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp * LocalDensityScale.current),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun PushSubscriptionRow(
    subscription: PushSubscriptionSummaryDto,
    isThisDevice: Boolean,
    onRevoke: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    subscription.userAgent?.takeIf { it.isNotBlank() }
                        ?: stringResource(if (subscription.provider == "unifiedpush") R.string.settings_push_device_unknown else R.string.settings_push_device_browser),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    if (isThisDevice) {
                        stringResource(R.string.settings_push_this_device)
                    } else {
                        stringResource(R.string.settings_push_last_used, formatIsoTimestamp(subscription.lastUsedAt))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRevoke) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.settings_push_revoke))
            }
        }
    }
}

@Composable
private fun VhostRow(option: VhostOptionDto, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(option.name, style = MaterialTheme.typography.bodyLarge)
            if (option.name != option.address) {
                Text(option.address, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val SUBSCRIPTION_TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private fun formatIsoTimestamp(iso: String?): String {
    if (iso == null) return "—"
    return runCatching { SUBSCRIPTION_TIMESTAMP_FORMATTER.format(Instant.parse(iso)) }.getOrDefault(iso)
}

private fun formatEpochMillis(epochMillis: Long): String =
    SUBSCRIPTION_TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

private val BYTE_UNITS = listOf("B", "KB", "MB", "GB")

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < BYTE_UNITS.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "$bytes ${BYTE_UNITS[0]}"
    } else {
        "%.1f %s".format(java.util.Locale.US, value, BYTE_UNITS[unitIndex])
    }
}
