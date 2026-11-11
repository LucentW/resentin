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
import pm.antani.resentin.R
import pm.antani.resentin.net.dto.E2eRequestDto

/**
 * The Home section for encrypted chats: a header with "new", requests waiting on us (accept or
 * refuse), requests we sent, answers to show once, identities held elsewhere, and the
 * conversations. Built as list items so Home needs only one hook.
 */
fun LazyListScope.e2eHomeItems(
    o: E2eOverview,
    vm: E2eOverviewViewModel,
    onOpenChat: (slug: String, peer: String) -> Unit,
) {
    if (!o.visible) return
    item(key = "e2e-header") {
        var showNew by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.e2e_home_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { showNew = true }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.e2e_new_chat))
            }
        }
        if (showNew) {
            NewE2eChatDialog(
                networks = o.networks,
                onDismiss = { showNew = false },
                onStart = { slug, nick ->
                    showNew = false
                    vm.ask(slug, nick) { ok -> if (ok) onOpenChat(slug, nick) }
                },
            )
        }
    }
    items(o.elsewhere.size, key = { "e2e-elsewhere-${o.elsewhere[it]}" }) { i ->
        E2eElsewhereCard(o.elsewhere[i], onReplace = { vm.replaceIdentity(o.elsewhere[i]) })
    }
    items(o.incoming.size, key = { "e2e-incoming-${o.incoming[it].first}-${o.incoming[it].second.id}" }) { i ->
        val (slug, r) = o.incoming[i]
        E2eRequestCard(
            text = stringResource(R.string.e2e_request_incoming, r.peer, slug),
            primary = stringResource(R.string.e2e_accept) to { vm.accept(slug, r) { ok -> if (ok) onOpenChat(slug, r.peer) } },
            secondary = stringResource(R.string.e2e_refuse) to { vm.refuse(slug, r) },
        )
    }
    items(o.answered.size, key = { "e2e-answered-${o.answered[it].first}-${o.answered[it].second.id}" }) { i ->
        val (slug, r) = o.answered[i]
        E2eRequestCard(
            text = stringResource(if (r.state == "refused") R.string.e2e_request_refused else R.string.e2e_request_expired, r.peer),
            primary = stringResource(R.string.e2e_dismiss) to { vm.dismiss(slug, r) },
        )
    }
    items(o.waiting.size, key = { "e2e-waiting-${o.waiting[it].first}-${o.waiting[it].second.id}" }) { i ->
        val (slug, r) = o.waiting[i]
        E2eRequestCard(
            text = stringResource(R.string.e2e_request_waiting, r.peer, slug),
            primary = stringResource(R.string.e2e_open) to { onOpenChat(slug, r.peer) },
        )
    }
    items(o.conversations.size, key = { "e2e-conv-${o.conversations[it].networkSlug}-${o.conversations[it].peer}" }) { i ->
        val c = o.conversations[i]
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onOpenChat(c.networkSlug, c.peerNick) }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${c.peerNick} · ${c.networkSlug}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    c.body.ifBlank { stringResource(R.string.e2e_chat_opened) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

@Composable
fun NewE2eChatDialog(networks: List<String>, onDismiss: () -> Unit, onStart: (slug: String, nick: String) -> Unit) {
    var nick by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf(networks.firstOrNull().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.e2e_new_chat)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.e2e_new_chat_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = nick, onValueChange = { nick = it.trim() }, singleLine = true, label = { Text(stringResource(R.string.e2e_nick)) })
                if (networks.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        networks.forEach { n ->
                            TextButton(onClick = { slug = n }) { Text(n, fontWeight = if (n == slug) FontWeight.Bold else FontWeight.Normal) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = nick.isNotBlank() && slug.isNotBlank(), onClick = { onStart(slug, nick) }) { Text(stringResource(R.string.e2e_ask)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.e2e_cancel)) } },
    )
}
