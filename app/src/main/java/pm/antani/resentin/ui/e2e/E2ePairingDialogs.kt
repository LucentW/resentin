package pm.antani.resentin.ui.e2e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pm.antani.resentin.R
import pm.antani.resentin.e2e.HolderPairing
import pm.antani.resentin.e2e.HolderStage
import pm.antani.resentin.e2e.JoinerPairing
import pm.antani.resentin.e2e.JoinerStage
import pm.antani.resentin.e2e.NetworkResult

@Composable
private fun endedText(reason: String?): String = when (reason) {
    "code-mismatch" -> stringResource(R.string.e2e_pair_end_mismatch)
    "nothing-to-offer" -> stringResource(R.string.e2e_pair_end_nothing_offer)
    "nothing-to-pair" -> stringResource(R.string.e2e_pair_end_nothing_pair)
    "expired" -> stringResource(R.string.e2e_pair_end_expired)
    "closed" -> stringResource(R.string.e2e_pair_end_closed)
    "replaced" -> stringResource(R.string.e2e_pair_end_replaced)
    else -> stringResource(R.string.e2e_pair_end_failed)
}

@Composable
private fun resultsText(results: List<NetworkResult>): String {
    val added = stringResource(R.string.e2e_pair_result_added)
    val full = stringResource(R.string.e2e_pair_result_full)
    val paired = stringResource(R.string.e2e_pair_result_paired)
    val changed = stringResource(R.string.e2e_pair_result_changed)
    val failed = stringResource(R.string.e2e_pair_result_failed)
    return results.joinToString("\n") { r ->
        val what = when (r.outcome) { "added" -> added; "full" -> full; "paired" -> paired; "list-changed" -> changed; else -> failed }
        "${r.network}: $what"
    }
}

/** The device that holds the identity: waits for the other to accept the offer, then asks for the 2-digit code it shows. */
@Composable
fun HolderPairingDialog(holder: HolderPairing, onClose: () -> Unit) {
    val s by holder.state.collectAsState()
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.e2e_pair_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (s.stage) {
                    HolderStage.WAITING -> {
                        Text(stringResource(R.string.e2e_pair_holder_waiting))
                        CircularProgressIndicator()
                    }
                    HolderStage.CODE -> {
                        Text(stringResource(R.string.e2e_pair_holder_code))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.filter(Char::isDigit).take(2) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.e2e_pair_code_label)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        )
                    }
                    HolderStage.OFFERED -> {
                        Text(stringResource(R.string.e2e_pair_holder_offered, s.offered.joinToString()))
                        CircularProgressIndicator()
                    }
                    HolderStage.SIGNING -> { Text(stringResource(R.string.e2e_pair_working)); CircularProgressIndicator() }
                    HolderStage.DONE -> Text(resultsText(s.results).ifBlank { stringResource(R.string.e2e_pair_done) })
                    HolderStage.ENDED -> {
                        Text(endedText(s.ended), color = MaterialTheme.colorScheme.error)
                        if (s.results.isNotEmpty()) Text(resultsText(s.results))
                    }
                }
                if (s.notMaster.isNotEmpty()) Text(stringResource(R.string.e2e_pair_not_master, s.notMaster.joinToString()), style = MaterialTheme.typography.bodySmall)
                if (s.full.isNotEmpty()) Text(stringResource(R.string.e2e_pair_full, s.full.joinToString()), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (s.stage == HolderStage.CODE) TextButton(enabled = code.length == 2, onClick = { holder.submitCode(code) }) { Text(stringResource(R.string.e2e_pair_confirm_code)) }
            else if (s.stage == HolderStage.DONE || s.stage == HolderStage.ENDED) TextButton(onClick = onClose) { Text(stringResource(R.string.e2e_dismiss)) }
        },
        dismissButton = {
            if (s.stage != HolderStage.DONE && s.stage != HolderStage.ENDED) TextButton(onClick = { holder.cancel(); onClose() }) { Text(stringResource(R.string.e2e_cancel)) }
        },
    )
}

/** The new device: shows the check code to type on the holder, then the networks, and takes its place when the user confirms. */
@Composable
fun JoinerPairingDialog(joiner: JoinerPairing, onClose: () -> Unit) {
    val s by joiner.state.collectAsState()
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.e2e_pair_join_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (s.stage) {
                    JoinerStage.JOINING -> { Text(stringResource(R.string.e2e_pair_working)); CircularProgressIndicator() }
                    JoinerStage.CODE -> {
                        Text(stringResource(R.string.e2e_pair_joiner_code))
                        Text(s.checkCode.orEmpty(), fontSize = 48.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        CircularProgressIndicator()
                    }
                    JoinerStage.CONFIRM -> {
                        Text(stringResource(R.string.e2e_pair_joiner_confirm, s.offered.joinToString()))
                        if (s.full.isNotEmpty()) Text(stringResource(R.string.e2e_pair_full, s.full.joinToString()), style = MaterialTheme.typography.bodySmall)
                    }
                    JoinerStage.JOINING_NETWORKS -> { Text(stringResource(R.string.e2e_pair_working)); CircularProgressIndicator() }
                    JoinerStage.DONE -> Text(resultsText(s.results).ifBlank { stringResource(R.string.e2e_pair_done) })
                    JoinerStage.ENDED -> Text(endedText(s.ended), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (s.stage == JoinerStage.CONFIRM) TextButton(onClick = joiner::confirm) { Text(stringResource(R.string.e2e_pair_join_confirm)) }
            else if (s.stage == JoinerStage.DONE || s.stage == JoinerStage.ENDED) TextButton(onClick = onClose) { Text(stringResource(R.string.e2e_dismiss)) }
        },
        dismissButton = {
            if (s.stage != JoinerStage.DONE && s.stage != JoinerStage.ENDED) TextButton(onClick = { joiner.cancel(); onClose() }) { Text(stringResource(R.string.e2e_cancel)) }
        },
    )
}
