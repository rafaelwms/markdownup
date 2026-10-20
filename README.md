# MarkdownUp

Convert **Markdown ⇄ Jira Wiki Markup** from the right-click menu, in VS Code and JetBrains IDEs
(IntelliJ IDEA, Rider, Android Studio, WebStorm, PyCharm…). Works on macOS, Windows and Linux, offline.

## Usage

Right-click a file in the Explorer / Project view, or the text inside the editor:

| Action | What it does |
|---|---|
| **Convert File** | Converts the file and swaps its extension (`notes.md` → `notes.jira`). |
| **Generate New File** | Keeps the original and writes the converted file next to it. |
| **Convert Selection / Document** | Converts the selected text, or the whole editor content when nothing is selected. |

If the file type is not recognised, you are asked which direction to convert.

Recognised extensions: Markdown `.md`, `.markdown` · Jira `.jira`, `.jiramarkup`.

## What is converted

Headings · bold, italic, strikethrough · inline code and fenced code blocks (with language) · links and images ·
nested bullet and numbered lists · tables · block quotes · horizontal rules · line breaks.

| Markdown | Jira |
|---|---|
| `# Title` | `h1. Title` |
| `**bold**` · `*italic*` · `~~strike~~` | `*bold*` · `_italic_` · `-strike-` |
| `` `code` `` | `{{code}}` |
| `[text](url)` | `[text\|url]` |
| `- item` / `1. item` | `* item` / `# item` |
| ```` ```ts ```` … ```` ``` ```` | `{code:ts}` … `{code}` |

## Development

| Package | What |
|---|---|
| `packages/core` | Dependency-free TypeScript converters |
| `packages/cli` | `markdownup` command-line tool |
| `packages/vscode` | VS Code extension |
| `packages/intellij` | JetBrains plugin (Kotlin port of the core) |

The Kotlin and TypeScript converters are kept identical by shared fixtures in
`packages/core/test/fixtures` (`name.md` ⇄ `name.jira`), checked by both test suites.

```
npm install
npm test
npm run build
cd packages/intellij && ./gradlew test buildPlugin
```

## License

MIT
