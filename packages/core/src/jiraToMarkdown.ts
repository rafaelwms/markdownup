import { Stash } from "./stash.ts";

const BOLD = "";

export function jiraToMarkdown(jira: string): string {
  const blocks = new Stash();
  let text = jira.replace(/\r\n?/g, "\n");

  // Code / noformat macros
  text = text.replace(/(?<!\{)\{(code|noformat)(?::([^}\n]*))?\}\n?([\s\S]*?)\n?\{\1\}(?!\})/g, (_, kind, params, body) => {
    const lang = kind === "code" ? languageOf(params) : "";
    return blocks.put("```" + lang + "\n" + body + "\n```");
  });

  // Quote / panel macros (content converted recursively)
  text = text.replace(/\{(quote|panel)(?::[^}\n]*)?\}\n?([\s\S]*?)\n?\{\1\}/g, (_, __, body) =>
    blocks.put(
      jiraToMarkdown(body)
        .trimEnd()
        .split("\n")
        .map((l) => (l ? `> ${l}` : ">"))
        .join("\n"),
    ),
  );

  const lines = text.split("\n");
  const out: string[] = [];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    const heading = /^h([1-6])\.\s+(.*)$/.exec(line);
    if (heading) {
      out.push(`${"#".repeat(Number(heading[1]))} ${inline(heading[2])}`);
      continue;
    }

    const bq = /^bq\.\s+(.*)$/.exec(line);
    if (bq) {
      out.push(`> ${inline(bq[1])}`);
      continue;
    }

    if (/^-{4,}\s*$/.test(line)) {
      out.push("---");
      continue;
    }

    const item = /^\s*([*#-]+)\s+(.*)$/.exec(line);
    if (item) {
      const marker = item[1];
      const indent = [...marker.slice(0, -1)].map((c) => (c === "#" ? 3 : 2)).reduce((a, b) => a + b, 0);
      const bullet = marker.endsWith("#") ? "1." : "-";
      out.push(`${" ".repeat(indent)}${bullet} ${inline(item[2])}`);
      continue;
    }

    if (/^\s*\|/.test(line)) {
      const rows: string[] = [];
      while (i < lines.length && /^\s*\|/.test(lines[i])) rows.push(lines[i++]);
      i--;
      out.push(...table(rows));
      continue;
    }

    out.push(inline(line));
  }

  return blocks.restore(out.join("\n")).replace(/\n{3,}/g, "\n\n").trim() + "\n";
}

function languageOf(params?: string): string {
  if (!params) return "";
  for (const part of params.split("|")) {
    const [k, v] = part.split("=");
    if (v === undefined) return k.trim();
    if (k.trim() === "language") return v.trim();
  }
  return "";
}

function table(rows: string[]): string[] {
  const parsed = rows.map((r) => {
    const t = r.trim();
    const header = t.startsWith("||");
    const body = inline(t).trim().replace(/^\|\|?/, "").replace(/\|\|?$/, "");
    const cells = (header ? body.split("||") : body.split("|")).map((c) => c.trim().replace(/\|/g, "\\|"));
    return { header, cells };
  });
  const width = Math.max(...parsed.map((r) => r.cells.length));
  const fmt = (cells: string[]) => `| ${Array.from({ length: width }, (_, i) => cells[i] ?? "").join(" | ")} |`;
  const sep = `| ${Array.from({ length: width }, () => "---").join(" | ")} |`;

  if (parsed[0].header) return [fmt(parsed[0].cells), sep, ...parsed.slice(1).map((r) => fmt(r.cells))];
  return [fmt([]), sep, ...parsed.map((r) => fmt(r.cells))];
}

export function inline(text: string): string {
  const stash = new Stash();
  let s = text;

  // Inline code
  s = s.replace(/\{\{(.+?)\}\}/g, (_, code) => stash.put("`" + code + "`"));

  // Mentions, links, images
  s = s.replace(/\[~([^\]]+)\]/g, (_, user) => stash.put(`@${user}`));
  s = s.replace(/\[([^\]|]+)\|([^\]|]+)(?:\|[^\]]*)?\]/g, (_, label, url) => stash.put(`[${inline(label)}](${url})`));
  s = s.replace(/\[((?:https?|ftp|mailto):[^\]\s]+)\]/g, (_, url) => stash.put(`<${url}>`));
  s = s.replace(/!([^\s!|]+)(?:\|[^!]*)?!/g, (_, url) => stash.put(`![](${url})`));

  // Layout-only macros
  s = s.replace(/\{color:[^}]*\}(.*?)\{color\}/g, "$1").replace(/\{anchor:[^}]*\}/g, "");

  // Line break
  s = s.replace(/\\\\/g, () => stash.put("<br>"));

  // Bold via marker, then italics, so the two never collide on `*`.
  s = s.replace(/(?<![\w*])\*(?=[^\s*])(.+?)(?<=[^\s*])\*(?![\w*])/g, `${BOLD}$1${BOLD}`);
  s = s.replace(/(?<![\w_])_(?=[^\s_])(.+?)(?<=[^\s_])_(?![\w_])/g, "*$1*");
  s = s.replaceAll(BOLD, "**");

  // Strikethrough, underline, sup/sub, citation
  s = s
    .replace(/(?<![\w-])-(?=[^\s-])(.+?)(?<=[^\s-])-(?![\w-])/g, "~~$1~~")
    .replace(/(?<![\w+])\+(?=[^\s+])(.+?)(?<=[^\s+])\+(?![\w+])/g, "<ins>$1</ins>")
    .replace(/(?<!\w)\^(?=\S)(.+?)(?<=\S)\^(?!\w)/g, "<sup>$1</sup>")
    .replace(/(?<![\w~])~(?=[^\s~])(.+?)(?<=[^\s~])~(?![\w~])/g, "<sub>$1</sub>")
    .replace(/\?\?(?=\S)(.+?)(?<=\S)\?\?/g, "<cite>$1</cite>");

  return stash.restore(s);
}
