# MarkdownUp

Convert **Markdown ⇄ Jira Wiki Markup** from the right-click menu, in VS Code and JetBrains IDEs.

| Package | What |
| --- | --- |
| `packages/core` | Dependency-free TypeScript converters (single source of truth) |
| `packages/cli` | `markdownup` command, used by the IntelliJ plugin |
| `packages/vscode` | VS Code extension |
| `packages/intellij` | JetBrains plugin (Kotlin, calls the CLI) — *planned* |

Actions: **Convert** (replaces the file/selection) and **Generate new file** (keeps the original).
Extensions: Markdown `.md/.markdown`, Jira `.jira/.jiramarkup`.

## Develop
```
npm install
npm test
npm run build
```
