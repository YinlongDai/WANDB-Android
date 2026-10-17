package io.github.jiecaoboy.runwatch.data

/**
 * Minimal ANSI SGR parser for console logs. Produces styled spans without any
 * UI dependency; the UI maps color indices to theme colors.
 */
object Ansi {

    data class Span(
        val text: String,
        /** 0-7 standard, 8-15 bright, null = default. */
        val fg: Int?,
        val bold: Boolean,
    )

    private val escape = Regex("""\u001B\[([0-9;]*)([A-Za-z])""")

    fun parse(line: String): List<Span> {
        val spans = mutableListOf<Span>()
        var fg: Int? = null
        var bold = false
        var pos = 0
        for (m in escape.findAll(line)) {
            if (m.range.first > pos) spans += Span(line.substring(pos, m.range.first), fg, bold)
            pos = m.range.last + 1
            if (m.groupValues[2] != "m") continue // cursor movement etc.: drop
            val codes = m.groupValues[1].split(';').map { it.toIntOrNull() ?: 0 }
            var i = 0
            while (i < codes.size) {
                when (val c = codes[i]) {
                    0 -> { fg = null; bold = false }
                    1 -> bold = true
                    22 -> bold = false
                    39 -> fg = null
                    in 30..37 -> fg = c - 30
                    in 90..97 -> fg = c - 90 + 8
                    38 -> {
                        // 38;5;n (256 colors): map the first 16, otherwise ignore.
                        if (codes.getOrNull(i + 1) == 5) {
                            val n = codes.getOrNull(i + 2)
                            fg = if (n != null && n < 16) n else fg
                            i += 2
                        } else if (codes.getOrNull(i + 1) == 2) {
                            i += 4 // 38;2;r;g;b truecolor: ignored
                        }
                    }
                }
                i++
            }
        }
        if (pos < line.length) spans += Span(line.substring(pos), fg, bold)
        return spans.filter { it.text.isNotEmpty() }
    }

    fun strip(line: String): String = escape.replace(line, "")
}
