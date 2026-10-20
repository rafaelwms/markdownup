import * as vscode from "vscode";
import { convert, detectFormat, targetFileName, type Format } from "../../core/src/index.ts";

const text = (b: Uint8Array) => new TextDecoder().decode(b);

/** Writes the converted content next to [uri], swapping the extension, and opens it. */
async function writeSibling(uri: vscode.Uri, from: Format, source: string): Promise<vscode.Uri> {
  const target = vscode.Uri.file(uri.fsPath.replace(/[^\\/]+$/, targetFileName(uri.fsPath.split(/[\\/]/).pop()!, from)));
  await vscode.workspace.fs.writeFile(target, new TextEncoder().encode(convert(source, from)));
  await vscode.window.showTextDocument(target);
  return target;
}

/** File-level command: "convert" replaces the original, otherwise it is kept. */
async function convertFile(uri: vscode.Uri | undefined, replace: boolean) {
  uri ??= vscode.window.activeTextEditor?.document.uri;
  const from = uri && detectFormat(uri.fsPath);
  if (!uri || !from) return void vscode.window.showWarningMessage("MarkdownUp: select a .md or .jira file.");

  await writeSibling(uri, from, text(await vscode.workspace.fs.readFile(uri)));
  if (replace) await vscode.workspace.fs.delete(uri);
}

/** Source format of the active editor; asks when the file type is not recognised. */
async function editorFormat(doc: vscode.TextDocument): Promise<Format | undefined> {
  const known = detectFormat(doc.fileName) ?? (doc.languageId === "markdown" ? "markdown" : doc.languageId === "jira" ? "jira" : undefined);
  if (known) return known;
  const pick = await vscode.window.showQuickPick(
    [{ label: "Markdown → Jira", format: "markdown" as Format }, { label: "Jira → Markdown", format: "jira" as Format }],
    { placeHolder: "Convert this content from…" },
  );
  return pick?.format;
}

async function convertEditor() {
  const editor = vscode.window.activeTextEditor;
  const from = editor && (await editorFormat(editor.document));
  if (!editor || !from) return;

  const target = from === "markdown" ? "jira" : "markdown";
  const selected = editor.selections.filter((s) => !s.isEmpty);
  await editor.edit((edit) => {
    if (selected.length === 0) {
      const all = new vscode.Range(0, 0, editor.document.lineCount, 0);
      edit.replace(all, convert(editor.document.getText(), from));
    } else for (const s of selected) edit.replace(s, convert(editor.document.getText(s), from).trimEnd());
  });
  if (selected.length === 0 && editor.document.languageId !== target) {
    await vscode.languages.setTextDocumentLanguage(editor.document, target === "jira" ? "jira" : "markdown");
  }
}

async function newEditorFile() {
  const editor = vscode.window.activeTextEditor;
  const from = editor && (await editorFormat(editor.document));
  if (!editor || !from) return;

  const doc = editor.document;
  const selection = editor.selections.find((s) => !s.isEmpty);

  // Whole saved-to-disk document: write the sibling file, like the Explorer command.
  if (!selection && doc.uri.scheme === "file" && detectFormat(doc.fileName)) {
    await writeSibling(doc.uri, from, doc.getText());
    return;
  }

  // A selection (or an unsaved buffer) must not overwrite a file, so open it unsaved beside the editor.
  const content = convert(selection ? doc.getText(selection) : doc.getText(), from);
  const result = await vscode.workspace.openTextDocument({ content, language: from === "markdown" ? "jira" : "markdown" });
  await vscode.window.showTextDocument(result, vscode.ViewColumn.Beside);
}

export function activate(ctx: vscode.ExtensionContext) {
  const reg = (id: string, fn: (...a: any[]) => unknown) => ctx.subscriptions.push(vscode.commands.registerCommand(id, fn));
  reg("markdownup.convertFile", (uri?: vscode.Uri) => convertFile(uri, true));
  reg("markdownup.newFile", (uri?: vscode.Uri) => convertFile(uri, false));
  reg("markdownup.convertEditor", convertEditor);
  reg("markdownup.newEditorFile", newEditorFile);
}

export function deactivate() {}
