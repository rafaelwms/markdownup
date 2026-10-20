package dev.markdownup

private const val BOLD = ""

fun jiraToMarkdown(jira: String): String {
    val blocks = Stash()
    var text = jira.replace(Regex("\r\n?"), "\n")

    // Code / noformat macros
    text = Regex("""(?<!\{)\{(code|noformat)(?::([^}\n]*))?\}\n?([\s\S]*?)\n?\{\1\}(?!\})""").replace(text) {
        val lang = if (it.groupValues[1] == "code") languageOf(it.groupValues[2]) else ""
        blocks.put("```$lang\n${it.groupValues[3]}\n```")
    }

    // Quote / panel macros (content converted recursively)
    text = Regex("""\{(quote|panel)(?::[^}\n]*)?\}\n?([\s\S]*?)\n?\{\1\}""").replace(text) {
        blocks.put(
            jiraToMarkdown(it.groupValues[2]).trimEnd().split("\n")
                .joinToString("\n") { l -> if (l.isNotEmpty()) "> $l" else ">" },
        )
    }

    val lines = text.split("\n")
    val out = mutableListOf<String>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        val heading = Regex("""^h([1-6])\.\s+(.*)$""").find(line)
        if (heading != null) {
            out.add("${"#".repeat(heading.groupValues[1].toInt())} ${jiraInline(heading.groupValues[2])}")
            i++
            continue
        }

        val bq = Regex("""^bq\.\s+(.*)$""").find(line)
        if (bq != null) {
            out.add("> ${jiraInline(bq.groupValues[1])}")
            i++
            continue
        }

        if (Regex("""^-{4,}\s*$""").matches(line)) {
            out.add("---")
            i++
            continue
        }

        val item = Regex("""^\s*([*#-]+)\s+(.*)$""").find(line)
        if (item != null) {
            val marker = item.groupValues[1]
            val indent = marker.dropLast(1).sumOf { if (it == '#') 3 else 2 }
            val bullet = if (marker.endsWith("#")) "1." else "-"
            out.add("${" ".repeat(indent)}$bullet ${jiraInline(item.groupValues[2])}")
            i++
            continue
        }

        if (Regex("""^\s*\|""").containsMatchIn(line)) {
            val rows = mutableListOf<String>()
            while (i < lines.size && Regex("""^\s*\|""").containsMatchIn(lines[i])) rows.add(lines[i++])
            out.addAll(table(rows))
            continue
        }

        out.add(jiraInline(line))
        i++
    }

    return blocks.restore(out.joinToString("\n")).replace(Regex("\n{3,}"), "\n\n").trim() + "\n"
}

private fun languageOf(params: String): String {
    if (params.isEmpty()) return ""
    for (part in params.split("|")) {
        val kv = part.split("=", limit = 2)
        if (kv.size == 1) return kv[0].trim()
        if (kv[0].trim() == "language") return kv[1].trim()
    }
    return ""
}

private fun table(rows: List<String>): List<String> {
    class Row(val header: Boolean, val cells: List<String>)

    val parsed = rows.map { r ->
        val t = r.trim()
        val header = t.startsWith("||")
        val body = jiraInline(t).trim().replaceFirst(Regex("""^\|\|?"""), "").replaceFirst(Regex("""\|\|?$"""), "")
        val cells = (if (header) body.split("||") else body.split("|")).map { it.trim().replace("|", "\\|") }
        Row(header, cells)
    }
    val width = parsed.maxOf { it.cells.size }
    fun fmt(cells: List<String>) = "| " + List(width) { cells.getOrElse(it) { "" } }.joinToString(" | ") + " |"
    val sep = "| " + List(width) { "---" }.joinToString(" | ") + " |"

    return if (parsed[0].header) listOf(fmt(parsed[0].cells), sep) + parsed.drop(1).map { fmt(it.cells) }
    else listOf(fmt(emptyList()), sep) + parsed.map { fmt(it.cells) }
}

private fun jiraInline(text: String): String {
    val stash = Stash()
    var s = text

    // Inline code
    s = Regex("""\{\{(.+?)\}\}""").replace(s) { stash.put("`${it.groupValues[1]}`") }

    // Mentions, links, images
    s = Regex("""\[~([^\]]+)]""").replace(s) { stash.put("@${it.groupValues[1]}") }
    s = Regex("""\[([^\]|]+)\|([^\]|]+)(?:\|[^\]]*)?]""").replace(s) {
        stash.put("[${jiraInline(it.groupValues[1])}](${it.groupValues[2]})")
    }
    s = Regex("""\[((?:https?|ftp|mailto):[^\]\s]+)]""").replace(s) { stash.put("<${it.groupValues[1]}>") }
    s = Regex("""!([^\s!|]+)(?:\|[^!]*)?!""").replace(s) { stash.put("![](${it.groupValues[1]})") }

    // Layout-only macros
    s = Regex("""\{color:[^}]*\}(.*?)\{color\}""").replace(s) { it.groupValues[1] }
    s = Regex("""\{anchor:[^}]*\}""").replace(s, "")

    // Line break
    s = Regex("""\\\\""").replace(s) { stash.put("<br>") }

    // Bold via marker, then italics, so the two never collide on `*`.
    s = Regex("""(?<![\w*])\*(?=[^\s*])(.+?)(?<=[^\s*])\*(?![\w*])""").replace(s) { BOLD + it.groupValues[1] + BOLD }
    s = Regex("""(?<![\w_])_(?=[^\s_])(.+?)(?<=[^\s_])_(?![\w_])""").replace(s) { "*${it.groupValues[1]}*" }
    s = s.replace(BOLD, "**")

    // Strikethrough, underline, sup/sub, citation
    s = Regex("""(?<![\w-])-(?=[^\s-])(.+?)(?<=[^\s-])-(?![\w-])""").replace(s) { "~~${it.groupValues[1]}~~" }
    s = Regex("""(?<![\w+])\+(?=[^\s+])(.+?)(?<=[^\s+])\+(?![\w+])""").replace(s) { "<ins>${it.groupValues[1]}</ins>" }
    s = Regex("""(?<!\w)\^(?=\S)(.+?)(?<=\S)\^(?!\w)""").replace(s) { "<sup>${it.groupValues[1]}</sup>" }
    s = Regex("""(?<![\w~])~(?=[^\s~])(.+?)(?<=[^\s~])~(?![\w~])""").replace(s) { "<sub>${it.groupValues[1]}</sub>" }
    s = Regex("""\?\?(?=\S)(.+?)(?<=\S)\?\?""").replace(s) { "<cite>${it.groupValues[1]}</cite>" }

    return stash.restore(s)
}
