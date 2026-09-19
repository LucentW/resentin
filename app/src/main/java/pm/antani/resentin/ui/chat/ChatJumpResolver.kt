package pm.antani.resentin.ui.chat

import pm.antani.resentin.data.db.MessageEntity

/**
 * F3 — jump deterministico sui soli dati locali (niente Grappa).
 *
 * Il jump da mentions arriva come `serverTime`: si atterra sulla prima riga
 * caricata con `serverTime >= target`. A parità di `serverTime` vince l'id
 * minore (riga più vecchia): stesso input -> stesso atterraggio, anche quando
 * il server compatta più eventi nello stesso millisecondo.
 * La seconda metà del percorso resta per id (`pendingActivityJumpId`), già
 * esatta; il retry via `loadOlder` resta invariato in ChatScreen.
 */
object ChatJumpResolver {
    fun firstAtOrAfterTime(messages: List<MessageEntity>, targetServerTime: Long): MessageEntity? =
        messages.filter { it.serverTime >= targetServerTime }
            .minWithOrNull(compareBy({ it.serverTime }, { it.id }))

    fun rowIndexForId(rows: List<ChatTimelineRow>, targetId: Long): Int =
        rows.indexOfFirst { row -> row.messages.any { it.id == targetId } }
}
