package pm.antani.resentin.irc

import pm.antani.resentin.BuildConfig

/**
 * Messaggi PART/QUIT configurabili (sezione IRC delle Impostazioni).
 *
 * Template predefinito `Grappa-IRC - Resentin %version` — `%version` è sempre
 * sostituito con [BuildConfig.VERSION_NAME] prima dell'invio, mai inviato
 * letteralmente. Stessa disciplina del server per le preferenze assenti:
 * `null` salvato (mai personalizzato) = usa il predefinito, stringa vuota =
 * nessun motivo (comportamento attuale senza motivo), qualsiasi altro valore =
 * messaggio personalizzato (con `%version` comunque espanso).
 */
const val IRC_MESSAGE_TEMPLATE = "Grappa-IRC - Resentin %version"
private const val VERSION_PLACEHOLDER = "%version"

/** Predefinito risolto con la versione effettiva dell'app (mai `%version` letterale). */
fun defaultIrcMessage(): String =
    IRC_MESSAGE_TEMPLATE.replace(VERSION_PLACEHOLDER, BuildConfig.VERSION_NAME)

/**
 * Risolve un valore salvato per l'invio:
 * - `null` (mai personalizzato, anche per chi aggiorna) -> predefinito con versione
 * - blank (campo svuotato) -> `null` (nessun motivo, comportamento attuale)
 * - altrimenti -> valore con `%version` espanso (mai letterale).
 */
fun resolveStoredIrcMessage(stored: String?): String? {
    if (stored == null) return defaultIrcMessage()
    if (stored.isBlank()) return null
    return stored.replace(VERSION_PLACEHOLDER, BuildConfig.VERSION_NAME)
}

/**
 * Risolve un motivo esplicito da `/part` o `/quit` (scelta esplicita dell'utente,
 * preservata): blank -> `null`, altrimenti `%version` espanso.
 */
fun resolveExplicitIrcReason(explicit: String?): String? {
    if (explicit == null || explicit.isBlank()) return null
    return explicit.replace(VERSION_PLACEHOLDER, BuildConfig.VERSION_NAME)
}

/**
 * Normalizza una bozza della UI per il salvataggio server-side:
 * - blank (campo svuotato) -> `""` (nessun motivo, distinto da `null`)
 * - uguale al predefinito risolto o al template -> `null` (usa predefinito,
 *   così i futuri bump di versione si propagano senza congelare la vecchia)
 * - altrimenti -> bozza così com'è (il `%version` resta e verrà espanso all'invio).
 *
 * Non sovrascrive mai con la versione numerica risolta: il server conserva
 * `null`/`""`/grezzo, la versione è solo una proiezione di lettura/invio.
 */
fun normalizeIrcMessageForSave(draft: String): String? {
    if (draft.isBlank()) return ""
    if (draft == defaultIrcMessage() || draft == IRC_MESSAGE_TEMPLATE) return null
    return draft
}

/** Motivo PART da inviare: esplicito se presente, altrimenti quello salvato. */
fun partReasonForSend(explicitArgs: List<String>, stored: String?): String? {
    val explicit = explicitArgs.joinToString(" ")
    if (explicit.isNotBlank()) return resolveExplicitIrcReason(explicit)
    return resolveStoredIrcMessage(stored)
}

/** Motivo QUIT da inviare: esplicito se presente, altrimenti quello salvato. */
fun quitReasonForSend(explicitReason: String?, stored: String?): String? {
    if (!explicitReason.isNullOrBlank()) return resolveExplicitIrcReason(explicitReason)
    return resolveStoredIrcMessage(stored)
}
