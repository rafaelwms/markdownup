package dev.markdownup

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ConvertTest {
    private fun md2j(s: String) = markdownToJira(s).trimEnd()
    private fun j2md(s: String) = jiraToMarkdown(s).trimEnd()

    @Test fun headings() {
        assertEquals("h1. A\nh2. B", md2j("# A\n## B"))
        assertEquals("# A\n### C", j2md("h1. A\nh3. C"))
    }

    @Test fun inlineMdToJira() {
        assertEquals("*b* and _i_ and -s- and {{c}}", md2j("**b** and *i* and ~~s~~ and `c`"))
        assertEquals("*bold _nested italic_*", md2j("**bold *nested italic***"))
    }

    @Test fun inlineJiraToMd() {
        assertEquals("**b** and *i* and ~~s~~ and `c`", j2md("*b* and _i_ and -s- and {{c}}"))
        assertEquals("snake_case_word and well-known", j2md("snake_case_word and well-known"))
    }

    @Test fun linksAndImages() {
        assertEquals("[doc|https://x.io/a_b_c]", md2j("[doc](https://x.io/a_b_c)"))
        assertEquals("!img.png!", md2j("![](img.png)"))
        assertEquals("[doc](https://x.io)", j2md("[doc|https://x.io]"))
        assertEquals("<https://x.io>", j2md("[https://x.io]"))
        assertEquals("![](img.png)", j2md("!img.png!"))
    }

    @Test fun codeBlocks() {
        assertEquals("{code:ts}\nconst a = *1*;\n{code}", md2j("```ts\nconst a = *1*;\n```"))
        assertEquals("```java\nint *a;\n```", j2md("{code:java}\nint *a;\n{code}"))
        assertEquals("```kotlin\nx\n```", j2md("{code:title=Foo|language=kotlin}\nx\n{code}"))
        assertEquals("use `code` here\n\n```\nx\n```", j2md("use {{code}} here\n\n{code}\nx\n{code}"))
    }

    @Test fun lists() {
        assertEquals("* a\n** b\n**# c\n* d", md2j("- a\n  - b\n    1. c\n- d"))
        assertEquals("- a\n  - b\n1. c", j2md("* a\n** b\n# c"))
    }

    @Test fun tables() {
        val md = "| A | B |\n| --- | --- |\n| 1 | 2 |"
        assertEquals("||A||B||\n|1|2|", md2j(md))
        assertEquals(md, j2md("||A||B||\n|1|2|"))
    }

    @Test fun quotesAndRules() {
        assertEquals("bq. hi", md2j("> hi"))
        assertEquals("{quote}\na\nb\n{quote}", md2j("> a\n> b"))
        assertEquals("> a\n> b", j2md("{quote}\na\nb\n{quote}"))
        assertEquals("----", md2j("---"))
        assertEquals("---", j2md("----"))
    }

    @Test fun paragraphFollowedByList() {
        assertEquals("intro\n* a\n* b", md2j("intro\n- a\n- b"))
    }

    // Same fixtures the TypeScript tests use, so both implementations stay identical.
    @Test fun sharedFixtures() {
        val dir = File("../core/test/fixtures")
        val mds = dir.listFiles { f -> f.name.endsWith(".md") }!!
        check(mds.isNotEmpty()) { "no fixtures found in ${dir.absolutePath}" }
        for (mdFile in mds) {
            val md = mdFile.readText()
            val jira = File(dir, mdFile.name.removeSuffix(".md") + ".jira").readText()
            assertEquals(jira, markdownToJira(md), "${mdFile.name} md -> jira")
            assertEquals(md, jiraToMarkdown(jira), "${mdFile.name} jira -> md")
        }
    }
}
