package pm.antani.resentin.ui.chat

/**
 * F4 — memoria viewport del processo, separata dal read-cursor.
 *
 * Il read-cursor è durevole (persistito via ChatRepository.markRead e usato
 * per il divider "Hai letto fino a qui"); la viewport dice solo dove il
 * lettore ha lasciato la lista. Mescolarli fa atterrare sempre su divider o
 * fondo e muove gli indici durante le animazioni.
 *
 * Lo store è un singleton di processo chiaveato su ("slug/channel"):
 * posizione come ancora di messaggio (id), mai come indice (gli indici si
 * spostano con divider e presence-burst). `parkedAtBottom` distingue "mai
 * aperto" da "lasciato in fondo": a pari cursore il primo va al divider, il
 * secondo al fondo.
 * Niente DAO, niente rete, niente Grappa — alla morte del processo si perde.
 */
internal object ChatScrollPositionStore {
    private val anchors = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val parkedAtBottom = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun putAnchor(channelKey: String, messageId: Long) {
        anchors[channelKey] = messageId
    }

    fun anchor(channelKey: String): Long? = anchors[channelKey]

    fun remove(channelKey: String) {
        anchors.remove(channelKey)
        parkedAtBottom.remove(channelKey)
    }

    fun markParkedAtBottom(channelKey: String) {
        anchors.remove(channelKey)
        parkedAtBottom.add(channelKey)
    }

    fun isParkedAtBottom(channelKey: String): Boolean = channelKey in parkedAtBottom

    fun unpark(channelKey: String) {
        parkedAtBottom.remove(channelKey)
    }
}

/** Indice di lista (reverse-layout) della riga che contiene [messageId], o null. */
internal fun anchorListIndex(
    timelineRows: List<ChatTimelineRow>,
    dividerIndex: Int?,
    messageId: Long,
): Int? {
    val rowIndex = ChatJumpResolver.rowIndexForId(timelineRows, messageId)
    if (rowIndex < 0) return null
    return reverseChatListIndex(rowIndex, timelineRows.size, dividerIndex)
}
