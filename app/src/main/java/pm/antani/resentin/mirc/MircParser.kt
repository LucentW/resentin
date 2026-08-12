package pm.antani.resentin.mirc

// mIRC control codes, given by decimal code point to avoid embedding raw
// control bytes in source: bold=2, color=3, strikethrough=30, italic=29, underline=31, reset=15.
private val BOLD = Char(2)
private val COLOR = Char(3)
private val ITALIC = Char(29)
private val UNDERLINE = Char(31)
private val STRIKETHROUGH = Char(30)
private val RESET = Char(15)

/**
 * Parses mIRC inline formatting control codes into a flat list of styled spans.
 * Pure Kotlin, no Android/Compose dependency, so it's unit-testable on the JVM —
 * a thin Compose-touching layer converts the output to an AnnotatedString.
 */
object MircParser {

    fun parse(input: String): List<MircSpan> {
        val spans = mutableListOf<MircSpan>()
        var bold = false
        var underline = false
        var italic = false
        var strikethrough = false
        var foreground: Int? = null
        var background: Int? = null
        val current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                spans += MircSpan(current.toString(), bold, underline, italic, strikethrough, foreground, background)
                current.clear()
            }
        }

        var i = 0
        while (i < input.length) {
            when (input[i]) {
                BOLD -> {
                    flush()
                    bold = !bold
                    i++
                }
                UNDERLINE -> {
                    flush()
                    underline = !underline
                    i++
                }
                ITALIC -> {
                    flush()
                    italic = !italic
                    i++
                }
                STRIKETHROUGH -> {
                    flush()
                    strikethrough = !strikethrough
                    i++
                }
                RESET -> {
                    flush()
                    bold = false
                    underline = false
                    italic = false
                    strikethrough = false
                    foreground = null
                    background = null
                    i++
                }
                COLOR -> {
                    flush()
                    i++
                    val fgDigits = StringBuilder()
                    while (i < input.length && input[i].isDigit() && fgDigits.length < 2) {
                        fgDigits.append(input[i])
                        i++
                    }
                    val bgDigits = StringBuilder()
                    if (i < input.length && input[i] == ',') {
                        var j = i + 1
                        val tmp = StringBuilder()
                        while (j < input.length && input[j].isDigit() && tmp.length < 2) {
                            tmp.append(input[j])
                            j++
                        }
                        if (tmp.isNotEmpty()) {
                            bgDigits.append(tmp)
                            i = j
                        }
                    }
                    if (fgDigits.isEmpty() && bgDigits.isEmpty()) {
                        foreground = null
                        background = null
                    } else {
                        if (fgDigits.isNotEmpty()) foreground = fgDigits.toString().toIntOrNull()
                        if (bgDigits.isNotEmpty()) background = bgDigits.toString().toIntOrNull()
                    }
                }
                else -> {
                    current.append(input[i])
                    i++
                }
            }
        }
        flush()
        return spans
    }
}
