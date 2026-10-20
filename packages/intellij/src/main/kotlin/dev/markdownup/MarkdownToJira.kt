package dev.markdownup

private const val BOLD = ""
private val LIST_ITEM = Regex("""^(\s*)([-*+]|\d+[.)])\s+(.*)$""")
private val TABLE_SEP = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")
private val FENCE = Regex("""^\s*(`{3,}|~{3,})\s*([\w+#.-]*)[^\n]*$""")
private val HEADING = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
private val RULE = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
private val QUOTE = Regex("""^\s{0,3}>""")
private val QUOTE_PREFIX = Regex("""^\s{0,3}>\s?""")
private val HARD_BREAK = Regex("""(\s{2,}|\\)$""")

fun markdownToJira(markdown: String): String {
    val lines = markdown.replace(Regex("\r\n?"), "\n").split("\n")
    val out = mutableListOf<String>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block
        val fence = FENCE.find(line)
        if (fence != null) {
            val marker = fence.groupValues[1].take(1).repeat(fence.groupValues[1].length)
            val body = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith(marker)) body.add(lines[i++])
            i++ // closing fence
            val lang = fence.groupValues[2]
            out.add(if (lang.isNotEmpty()) "{code:$lang}" else "{code}")
            out.addAll(body)
            out.add("{code}")
            continue
        }

        // Heading
        val heading = HEADING.find(line)
        if (heading != null) {
            out.add("h${heading.groupValues[1].length}. ${mdInline(heading.groupValues[2])}")
            i++
            continue
        }

        // Horizontal rule
        if (RULE.matches(line)) {
            out.add("----")
            i++
            continue
        }

        // Blockquote
        if (QUOTE.containsMatchIn(line)) {
            val body = mutableListOf<String>()
            while (i < lines.size && QUOTE.containsMatchIn(lines[i])) {
                body.add(lines[i].replaceFirst(QUOTE_PREFIX, ""))
                i++
            }
            val converted = markdownToJira(body.joinToString("\n")).trimEnd()
            out.add(if (body.size == 1) "bq. $converted" else "{quote}\n$converted\n{quote}")
            continue
        }

        // Table
        if (startsTable(lines, i)) {
            out.add("||" + splitRow(line).joinToString("||") { mdInline(it) } + "||")
            i += 2
            while (i < lines.size && lines[i].trim().isNotEmpty() && lines[i].contains("|")) {
                out.add("|" + splitRow(lines[i]).joinToString("|") { mdInline(it).ifEmpty { " " } } + "|")
                i++
            }
            continue
        }

        // List
        if (LIST_ITEM.matches(line)) {
            class Level(val indent: Int, var marker: Char)
            val stack = mutableListOf<Level>()
            while (i < lines.size) {
                val m = LIST_ITEM.find(lines[i])
                if (m != null) {
                    val indent = m.groupValues[1].replace("\t", "    ").length
                    while (stack.isNotEmpty() && stack.last().indent > indent) stack.removeLast()
                    val marker = if (m.groupValues[2].any { it.isDigit() }) '#' else '*'
                    if (stack.isNotEmpty() && stack.last().indent == indent) stack.last().marker = marker
                    else stack.add(Level(indent, marker))
                    out.add("${stack.joinToString("") { it.marker.toString() }} ${mdInline(taskBox(m.groupValues[3]))}")
                } else if (lines[i].trim().isNotEmpty() && lines[i].first().isWhitespace()) {
                    out[out.lastIndex] += " ${mdInline(lines[i].trim())}" // continuation line
                } else break
                i++
            }
            continue
        }

        // Blank line
        if (line.trim().isEmpty()) {
            out.add("")
            i++
            continue
        }

        // Paragraph
        val para = mutableListOf<String>()
        while (i < lines.size && lines[i].trim().isNotEmpty() && !startsBlock(lines, i)) para.add(lines[i++])
        out.add(para.joinToString("\n") { mdInline(it.replace(HARD_BREAK) { "\\\\" }) })
    }

    return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim() + "\n"
}

private fun startsTable(lines: List<String>, i: Int) =
    lines[i].contains("|") && i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1]) && lines[i + 1].contains("-")

private fun startsBlock(lines: List<String>, i: Int): Boolean {
    val l = lines[i]
    return Regex("""^\s*(`{3,}|~{3,})""").containsMatchIn(l) ||
        Regex("""^\s{0,3}#{1,6}\s""").containsMatchIn(l) ||
        QUOTE.containsMatchIn(l) ||
        LIST_ITEM.matches(l) ||
        startsTable(lines, i)
}

private fun taskBox(text: String) =
    text.replace(Regex("""^\[ \]\s+"""), "(x) ").replace(Regex("""^\[[xX]\]\s+"""), "(/) ")

private fun splitRow(row: String): List<String> =
    row.trim().replaceFirst(Regex("""^\|"""), "").replaceFirst(Regex("""(?<!\\)\|$"""), "")
        .split(Regex("""(?<!\\)\|"""))
        .map { it.trim().replace("\\|", "|") }

private fun mdInline(text: String): String {
    val stash = Stash()
    var s = text

    // Code spans
    s = Regex("""(`+)(.+?)\1""").replace(s) { stash.put("{{${it.groupValues[2].trim()}}}") }

    // Images
    s = Regex("""!\[([^\]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)""").replace(s) {
        val (_, alt, url) = it.groupValues
        stash.put(if (alt.isNotEmpty()) "!$url|alt=$alt!" else "!$url!")
    }

    // Links
    s = Regex("""\[([^\]]+)]\(([^)\s]+)(?:\s+"[^"]*")?\)""").replace(s) {
        stash.put("[${mdInline(it.groupValues[1])}|${it.groupValues[2]}]")
    }
    s = Regex("""<((?:https?|ftp|mailto):[^>\s]+)>""").replace(s) { stash.put("[${it.groupValues[1]}]") }

    // Line breaks
    s = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE).replace(s) { stash.put("\\\\") }

    // Bold first (via marker), then italics, so the two never collide on `*`.
    s = Regex("""\*\*(?=\S)(.+?)(?<=\S)\*\*(?!\*)""").replace(s) { BOLD + it.groupValues[1] + BOLD }
    s = Regex("""(?<!\w)__(?=\S)(.+?)(?<=\S)__(?!\w)""").replace(s) { BOLD + it.groupValues[1] + BOLD }
    s = Regex("""(?<![\w*])\*(?=[^\s*])(.+?)(?<=[^\s*])\*(?![\w*])""").replace(s) { "_${it.groupValues[1]}_" }
    s = s.replace(BOLD, "*")

    // Strikethrough
    s = Regex("""~~(?=\S)(.+?)(?<=\S)~~""").replace(s) { "-${it.groupValues[1]}-" }

    // Inline HTML equivalents
    val ci = RegexOption.IGNORE_CASE
    s = Regex("""<(?:u|ins)>(.*?)</(?:u|ins)>""", ci).replace(s) { "+${it.groupValues[1]}+" }
    s = Regex("""<sup>(.*?)</sup>""", ci).replace(s) { "^${it.groupValues[1]}^" }
    s = Regex("""<sub>(.*?)</sub>""", ci).replace(s) { "~${it.groupValues[1]}~" }
    s = Regex("""<cite>(.*?)</cite>""", ci).replace(s) { "??${it.groupValues[1]}??" }

    return stash.restore(s)
}
