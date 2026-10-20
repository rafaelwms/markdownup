import { jiraToMarkdown } from "./jiraToMarkdown.ts";
import { markdownToJira } from "./markdownToJira.ts";

export { jiraToMarkdown, markdownToJira };

export type Format = "markdown" | "jira";

export const EXTENSIONS: Record<Format, string[]> = {
  markdown: [".md", ".markdown"],
  jira: [".jira", ".jiramarkup"],
};

export function detectFormat(fileName: string): Format | undefined {
  const lower = fileName.toLowerCase();
  return (Object.keys(EXTENSIONS) as Format[]).find((f) => EXTENSIONS[f].some((e) => lower.endsWith(e)));
}

export function convert(text: string, from: Format): string {
  return from === "markdown" ? markdownToJira(text) : jiraToMarkdown(text);
}

/** Target file name for "generate new file": swaps the extension. */
export function targetFileName(fileName: string, from: Format): string {
  const ext = EXTENSIONS[from].find((e) => fileName.toLowerCase().endsWith(e));
  const base = ext ? fileName.slice(0, -ext.length) : fileName;
  return base + EXTENSIONS[from === "markdown" ? "jira" : "markdown"][0];
}
