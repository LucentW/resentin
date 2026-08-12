package pm.antani.resentin.mirc

data class MircSpan(
    val text: String,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    /** Standard mIRC color code 0-15, or null for "no color". */
    val foreground: Int? = null,
    val background: Int? = null,
)
