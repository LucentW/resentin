package pm.antani.resentin.ui.e2e

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import pm.antani.resentin.ui.common.LocalDensityScale
import pm.antani.resentin.ui.theme.ResentinSpacing
import pm.antani.resentin.R
import pm.antani.resentin.net.dto.E2eRequestDto

/**
 * Account-level items at the top of Home: another device offering to pair, and the pairing dialog while one runs.
 * Everything else about encrypted chats is per network and lives inside that network's card ([E2eNetworkSection]).
 */
fun LazyListScope.e2eHomeItems(o: E2eOverview, vm: E2eOverviewViewModel) {
    // Another device of the account offers to pair: shown only where it is of use (an identity held elsewhere).
    if (pairingOfferUseful(o.offer, o.elsewhere)) {
        item(key = "e2e-pair-offer") { PairingOfferCard(onJoin = vm::startJoiner) }
    }
    o.joiner?.let { j -> item(key = "e2e-pair-joiner") { JoinerPairingDialog(j, onClose = vm::endPairing) } }
}

/**
 * The encrypted chats of ONE network, inside its card, with the rest of its conversations: the states that need the
 * user first (an identity held elsewhere, requests waiting on us, requests we sent and their answers), then one row per
 * conversation, drawn like a channel's or a private chat's but with a lock where the initial would be. Nothing when
 * the server does not speak E2E for this network (the identity sync never answered). Starting one is in the network's
 * "New conversation" dialog.
 */
@Composable
fun E2eNetworkSection(slug: String, o: E2eOverview, vm: E2eOverviewViewModel, onOpenChat: (slug: String, peer: String) -> Unit) {
    if (slug !in o.networks) return
    if (slug in o.elsewhere) E2eElsewhereCard(slug, onReplace = { vm.replaceIdentity(slug) })
    o.incoming.filter { it.first == slug }.forEach { (_, r) ->
        E2eRequestCard(
            text = stringResource(R.string.e2e_request_incoming, r.peer, slug),
            primary = stringResource(R.string.e2e_accept) to { vm.accept(slug, r) { ok -> if (ok) onOpenChat(slug, r.peer) } },
            secondary = stringResource(R.string.e2e_refuse) to { vm.refuse(slug, r) },
        )
    }
    o.answered.filter { it.first == slug }.forEach { (_, r) ->
        E2eRequestCard(
            text = stringResource(if (r.state == "refused") R.string.e2e_request_refused else R.string.e2e_request_expired, r.peer),
            primary = stringResource(R.string.e2e_dismiss) to { vm.dismiss(slug, r) },
        )
    }
    o.waiting.filter { it.first == slug }.forEach { (_, r) ->
        E2eRequestCard(
            text = stringResource(R.string.e2e_request_waiting, r.peer, slug),
            primary = stringResource(R.string.e2e_open) to { onOpenChat(slug, r.peer) },
        )
    }
    o.conversations.filter { it.networkSlug == slug }.forEach { c ->
        HorizontalDivider(modifier = Modifier.padding(start = 62.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        E2eConversationRow(c.peerNick, c.body, c.at, onClick = { onOpenChat(c.networkSlug, c.peerNick) })
    }
}

/** A row like a channel's or a private chat's (same box, same spacing), with a lock in the box instead of an initial. */
@Composable
internal fun E2eConversationRow(peer: String, lastBody: String, at: Long, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val time = remember(context, at) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(at)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = ResentinSpacing.medium, end = ResentinSpacing.medium,
                top = ResentinSpacing.small * LocalDensityScale.current, bottom = ResentinSpacing.small * LocalDensityScale.current,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(40.dp), shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.e2e_chat_subtitle_short), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        Spacer(Modifier.width(ResentinSpacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(peer, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(ResentinSpacing.small))
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                lastBody.ifBlank { stringResource(R.string.e2e_chat_opened) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun E2eRequestCard(text: String, primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                secondary?.let { (label, act) -> TextButton(onClick = act) { Text(label) } }
                Button(onClick = primary.second) { Text(primary.first) }
            }
        }
    }
}

/** The identity grappa holds for the account on [slug] has no keys on this device. Replacing it is destructive. */
@Composable
fun E2eElsewhereCard(slug: String, onReplace: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.e2e_elsewhere, slug), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.e2e_new_identity)) }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.e2e_new_identity)) },
            text = { Text(stringResource(R.string.e2e_new_identity_warning, slug)) },
            confirmButton = { TextButton(onClick = { confirm = false; onReplace() }) { Text(stringResource(R.string.e2e_new_identity_confirm)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.e2e_cancel)) } },
        )
    }
}

/** An offer is of use only to a device that holds no identity the account has: one that is "elsewhere" on some network. */
fun pairingOfferUseful(offer: pm.antani.resentin.e2e.PairCode?, elsewhere: List<String>): Boolean = offer != null && elsewhere.isNotEmpty()

/** Another of the user's devices offers to pair with this one. */
@Composable
internal fun PairingOfferCard(onJoin: () -> Unit) {
    E2eRequestCard(
        text = stringResource(R.string.e2e_pair_offer_banner),
        primary = stringResource(R.string.e2e_pair_join_button) to onJoin,
    )
}
