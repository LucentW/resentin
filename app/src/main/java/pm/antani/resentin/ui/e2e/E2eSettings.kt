package pm.antani.resentin.ui.e2e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pm.antani.resentin.R
import pm.antani.resentin.domain.repository.E2eRepository
import pm.antani.resentin.e2e.IdentityState

/** A key as groups of four, easier to read aloud and compare out of band. */
fun fingerprint(key: String): String = key.chunked(4).joinToString(" ")

/** Settings → Chat cifrate. */
@Composable
fun E2eSettingsContent(vm: E2eSettingsViewModel) {
    val secure by vm.secureScreen.collectAsState()
    val identities by vm.identities.collectAsState()
    val states by vm.states.collectAsState()
    val offer by vm.offer.collectAsState()
    val holder by vm.holder.collectAsState()
    val joiner by vm.joiner.collectAsState()
    val devices by vm.devices.collectAsState()
    var revoking by remember { mutableStateOf<Pair<String, pm.antani.resentin.e2e.E2eIdentity.OwnDevice>?>(null) }
    androidx.compose.runtime.LaunchedEffect(states) { vm.refreshDevices() }
    holder?.let { HolderPairingDialog(it, onClose = { vm.endPairing(); vm.refreshDevices() }) }
    joiner?.let { JoinerPairingDialog(it, onClose = { vm.endPairing(); vm.refreshDevices() }) }
    revoking?.let { (slug, d) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text(stringResource(R.string.e2e_device_revoke)) },
            text = { Text(stringResource(R.string.e2e_device_revoke_warning, d.did, slug)) },
            confirmButton = { TextButton(onClick = { vm.revoke(slug, d.ed25519); revoking = null }) { Text(stringResource(R.string.e2e_device_revoke)) } },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text(stringResource(R.string.e2e_cancel)) } },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        offer?.let {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.e2e_pair_offer_banner))
                    androidx.compose.material3.Button(onClick = vm::startJoiner) { Text(stringResource(R.string.e2e_pair_join_button)) }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.e2e_secure_screen), fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.e2e_secure_screen_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = secure, onCheckedChange = vm::setSecureScreen)
            }
        }
        if (states.isEmpty()) {
            Text(stringResource(R.string.e2e_settings_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        states.forEach { (slug, state) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.e2e_identity_on, slug), fontWeight = FontWeight.SemiBold)
                    val info = identities[slug]
                    when {
                        state is IdentityState.Elsewhere -> Text(stringResource(R.string.e2e_elsewhere_short), color = MaterialTheme.colorScheme.error)
                        info == null -> Text(stringResource(R.string.e2e_identity_loading))
                        else -> {
                            OwnKeyBlock(info)
                            devices[slug].orEmpty().forEach { d ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        d.did + if (d.here) " · " + stringResource(R.string.e2e_device_here) else "" + (d.addedAt?.let { " · " + it.take(10) } ?: ""),
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                                    )
                                    if (!d.here && info.holdsMaster) TextButton(onClick = { revoking = slug to d }) { Text(stringResource(R.string.e2e_device_revoke)) }
                                }
                            }
                            if (info.holdsMaster) androidx.compose.material3.Button(onClick = vm::startHolder) { Text(stringResource(R.string.e2e_pair_add_button)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OwnKeyBlock(info: E2eRepository.OwnKeyInfo) {
    Text(stringResource(R.string.e2e_fingerprint_label), style = MaterialTheme.typography.labelMedium)
    SelectionContainer { Text(fingerprint(info.master), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) }
    Text(stringResource(R.string.e2e_device_line, info.did, info.devices, info.listVersion), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!info.holdsMaster) {
        Text(stringResource(R.string.e2e_not_master), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
