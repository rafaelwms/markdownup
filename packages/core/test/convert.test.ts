import { readdirSync, readFileSync } from "node:fs";
import { test } from "node:test";
import assert from "node:assert/strict";
import { convert, detectFormat, jiraToMarkdown, markdownToJira, targetFileName } from "../src/index.ts";

const md2j = (s: string) => markdownToJira(s).trimEnd();
const j2md = (s: string) => jiraToMarkdown(s).trimEnd();

test("headings", () => {
  assert.equal(md2j("# A\n## B"), "h1. A\nh2. B");
  assert.equal(j2md("h1. A\nh3. C"), "# A\n### C");
});

test("inline formatting md -> jira", () => {
  assert.equal(md2j("**b** and *i* and ~~s~~ and `c`"), "*b* and _i_ and -s- and {{c}}");
  assert.equal(md2j("**bold *nested italic***"), "*bold _nested italic_*");
});

test("inline formatting jira -> md", () => {
  assert.equal(j2md("*b* and _i_ and -s- and {{c}}"), "**b** and *i* and ~~s~~ and `c`");
  assert.equal(j2md("snake_case_word and well-known"), "snake_case_word and well-known");
});

test("links and images", () => {
  assert.equal(md2j("[doc](https://x.io/a_b_c)"), "[doc|https://x.io/a_b_c]");
  assert.equal(md2j("![](img.png)"), "!img.png!");
  assert.equal(j2md("[doc|https://x.io]"), "[doc](https://x.io)");
  assert.equal(j2md("[https://x.io]"), "<https://x.io>");
  assert.equal(j2md("!img.png!"), "![](img.png)");
});

test("code blocks", () => {
  assert.equal(md2j("```ts\nconst a = *1*;\n```"), "{code:ts}\nconst a = *1*;\n{code}");
  assert.equal(j2md("{code:java}\nint *a;\n{code}"), "```java\nint *a;\n```");
  assert.equal(j2md("{code:title=Foo|language=kotlin}\nx\n{code}"), "```kotlin\nx\n```");
});

test("lists with nesting", () => {
  assert.equal(md2j("- a\n  - b\n    1. c\n- d"), "* a\n** b\n**# c\n* d");
  assert.equal(j2md("* a\n** b\n# c"), "- a\n  - b\n1. c");
});

test("tables", () => {
  const md = "| A | B |\n| --- | --- |\n| 1 | 2 |";
  assert.equal(md2j(md), "||A||B||\n|1|2|");
  assert.equal(j2md("||A||B||\n|1|2|"), md);
});

test("quotes and rules", () => {
  assert.equal(md2j("> hi"), "bq. hi");
  assert.equal(md2j("> a\n> b"), "{quote}\na\nb\n{quote}");
  assert.equal(j2md("{quote}\na\nb\n{quote}"), "> a\n> b");
  assert.equal(md2j("---"), "----");
  assert.equal(j2md("----"), "---");
});

test("paragraph followed directly by list", () => {
  assert.equal(md2j("intro\n- a\n- b"), "intro\n* a\n* b");
});

test("round trip", () => {
  const md = "# T\n\nSome **bold** text with [a link](http://x.io).\n\n- one\n- two\n\n```js\nlet x;\n```";
  assert.equal(j2md(md2j(md)), md);
});

test("file helpers", () => {
  assert.equal(detectFormat("a/Readme.MD"), "markdown");
  assert.equal(detectFormat("x.jira"), "jira");
  assert.equal(detectFormat("x.txt"), undefined);
  assert.equal(targetFileName("notes.md", "markdown"), "notes.jira");
  assert.equal(targetFileName("notes.jira", "jira"), "notes.md");
  assert.equal(convert("# a", "markdown"), "h1. a\n");
});

test("code span named like a macro is not a macro", () => {
  assert.equal(j2md("use {{code}} here\n\n{code}\nx\n{code}"), "use `code` here\n\n```\nx\n```");
});

// Shared with the Kotlin tests (packages/intellij) so both implementations stay identical.
test("fixtures", () => {
  const dir = new URL("./fixtures/", import.meta.url);
  for (const name of readdirSync(dir).filter((f) => f.endsWith(".md"))) {
    const md = readFileSync(new URL(name, dir), "utf8");
    const jira = readFileSync(new URL(name.replace(/\.md$/, ".jira"), dir), "utf8");
    assert.equal(markdownToJira(md), jira, `${name} md -> jira`);
    assert.equal(jiraToMarkdown(jira), md, `${name} jira -> md`);
  }
});
