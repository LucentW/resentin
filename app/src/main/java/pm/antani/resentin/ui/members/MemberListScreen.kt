package pm.antani.resentin.ui.members

import pm.antani.resentin.ui.theme.ResentinSpacing

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pm.antani.resentin.R
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.irc.SIGIL_PRIORITY
import pm.antani.resentin.irc.highestSigil
import pm.antani.resentin.ui.common.ResentinHeaderAction
import pm.antani.resentin.ui.common.isLightTheme
import pm.antani.resentin.ui.common.LocalDensityScale
import pm.antani.resentin.ui.common.UserCardSheet
import pm.antani.resentin.ui.common.colorForNick
import pm.antani.resentin.ui.common.sigilsOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MemberListScreen(
    viewModel: MembersViewModel,
    title: String,
    networkSlug: String,
    viewerUsername: String,
    onBack: () -> Unit,
    onOpenQuery: (networkSlug: String, nick: String) -> Unit,
) {
    val members by viewModel.members.collectAsState()
    val ownSigils by viewModel.ownSigils.collectAsState()
    val privilegeModes by viewModel.privilegeModes.collectAsState()
    val whois by viewModel.selectedWhois.collectAsState()
    val coloredNicklist by viewModel.coloredNicklist.collectAsState()
    var query by remember { mutableStateOf("") }
    val sorted = remember(members) { members.sortedWith(memberOrdering) }
    val filtered = remember(sorted, query) {
        val q = query.trim()
        if (q.isEmpty()) sorted else sorted.filter { it.nick.contains(q, ignoreCase = true) }
    }
    // Role sections in display order, skipping whichever are empty (e.g. a channel
    // with no halfops never shows that header). Within a section the shared
    // memberOrdering still applies, so this stays consistent with the ungrouped sort.
    val groups = remember(filtered) {
        MemberGroup.entries.mapNotNull { group ->
            val list = filtered.filter { memberGroupOf(it) == group }
            if (list.isEmpty()) null else group to list
        }
    }
    val memberCountLabel = pluralStringResource(R.plurals.member_count, sorted.size, sorted.size)

    LaunchedEffect(Unit) {
        viewModel.navigateToQuery.collect { nick -> onOpenQuery(networkSlug, nick) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = memberCountLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).size(20.dp),
                    )
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.members_search_hint)) },
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
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_cancel))
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.members_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groups.forEach { (group, list) ->
                        stickyHeader(key = "group-${group.name}") {
                            Text(
                                text = stringResource(
                                    R.string.members_group_header,
                                    stringResource(group.labelRes).uppercase(),
                                    list.size,
                                ),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    letterSpacing = 0.8.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(list, key = { it.nick }) { member ->
                            MemberRow(
                                member = member,
                                coloredNicklist = coloredNicklist,
                                roleLabel = group.roleRes?.let { stringResource(it) },
                                onClick = { viewModel.onMemberClick(member.nick) },
                            )
                        }
                    }
                }
            }
        }
    }

    val whoisValue = whois
    if (whoisValue != null) {
        val ignored by viewModel.isIgnored(whoisValue.target).collectAsState(initial = false)
        val avatar by viewModel.avatarBitmap.collectAsState()
        UserCardSheet(
            whois = whoisValue,
            viewerUsername = viewerUsername,
            ownSigils = ownSigils,
            targetSigils = viewModel.sigilsFor(whoisValue.target),
            availableModes = privilegeModes,
            onDismiss = viewModel::dismissWhois,
            onContactPrivately = viewModel::contactPrivately,
            onKick = viewModel::kick,
            onBan = viewModel::ban,
            onSetMode = viewModel::setMode,
            isIgnored = ignored,
            onIgnore = viewModel::ignore,
            onUnignore = viewModel::unignore,
            avatarBitmap = avatar,
        )
    }
}

@Composable
private fun MemberRow(
    member: MemberEntity,
    coloredNicklist: Boolean,
    roleLabel: String?,
    onClick: () -> Unit,
) {
    val nickColor = colorForNick(member.nick, isLightTheme())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = ResentinSpacing.small * LocalDensityScale.current),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(nickColor.copy(alpha = 0.18f), MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = member.nick.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = nickColor,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.nick,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (coloredNicklist) nickColor else MaterialTheme.colorScheme.onSurface,
            )
            if (roleLabel != null) {
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val sigils = sigilsOf(member)
        if (sigils.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = sigils,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ResentinSpacing.small, vertical = ResentinSpacing.xSmall),
                )
            }
        }
    }
}

// Privilege sections, highest first: owners/admins/ops together, then halfops, then
// voice, then everyone else. Decided by the member's HIGHEST sigil, same rule as
// memberOrdering — a "@+" nick counts as ops, not voice.
private enum class MemberGroup(val labelRes: Int, val roleRes: Int?) {
    OPS(R.string.members_group_ops, R.string.members_role_operator),
    HALFOP(R.string.members_group_halfops, R.string.members_role_halfop),
    VOICE(R.string.members_group_voiced, R.string.members_role_voice),
    USERS(R.string.members_group_users, null),
}

private fun memberGroupOf(member: MemberEntity): MemberGroup =
    when (highestSigil(sigilsOf(member))) {
        '~', '&', '@' -> MemberGroup.OPS
        '%' -> MemberGroup.HALFOP
        '+' -> MemberGroup.VOICE
        else -> MemberGroup.USERS
    }

// A member can hold several sigils at once (e.g. "@+"); rank by the highest one they
// have (SIGIL_PRIORITY), not by presence/absence of any single one.
private fun highestRank(member: MemberEntity): Int {
    val sigil = highestSigil(sigilsOf(member))
    return sigil?.let { SIGIL_PRIORITY.indexOf(it) } ?: SIGIL_PRIORITY.size
}

private val memberOrdering = compareBy<MemberEntity> { highestRank(it) }
    .thenBy { it.nick.lowercase() }
