import { Stash } from "./stash.ts";

const BOLD = "";
const LIST_ITEM = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/;
const TABLE_SEP = /^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$/;

export function markdownToJira(markdown: string): string {
  const lines = markdown.replace(/\r\n?/g, "\n").split("\n");
  const out: string[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // Fenced code block
    const fence = /^\s*(`{3,}|~{3,})\s*([\w+#.-]*)[^\n]*$/.exec(line);
    if (fence) {
      const marker = fence[1][0].repeat(fence[1].length);
      const body: string[] = [];
      i++;
      while (i < lines.length && !lines[i].trim().startsWith(marker)) body.push(lines[i++]);
      i++; // closing fence
      out.push(fence[2] ? `{code:${fence[2]}}` : "{code}", ...body, "{code}");
      continue;
    }

    // Heading
    const heading = /^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$/.exec(line);
    if (heading) {
      out.push(`h${heading[1].length}. ${inline(heading[2])}`);
      i++;
      continue;
    }

    // Horizontal rule
    if (/^\s{0,3}([-*_])(\s*\1){2,}\s*$/.test(line)) {
      out.push("----");
      i++;
      continue;
    }

    // Blockquote
    if (/^\s{0,3}>/.test(line)) {
      const body: string[] = [];
      while (i < lines.length && /^\s{0,3}>/.test(lines[i])) {
        body.push(lines[i].replace(/^\s{0,3}>\s?/, ""));
        i++;
      }
      const converted = markdownToJira(body.join("\n")).trimEnd();
      out.push(body.length === 1 ? `bq. ${converted}` : `{quote}\n${converted}\n{quote}`);
      continue;
    }

    // Table
    if (line.includes("|") && i + 1 < lines.length && TABLE_SEP.test(lines[i + 1]) && lines[i + 1].includes("-")) {
      out.push(`||${splitRow(line).map(inline).join("||")}||`);
      i += 2;
      while (i < lines.length && lines[i].trim() !== "" && lines[i].includes("|")) {
        out.push(`|${splitRow(lines[i]).map((c) => inline(c) || " ").join("|")}|`);
        i++;
      }
      continue;
    }

    // List
    if (LIST_ITEM.test(line)) {
      const stack: { indent: number; marker: string }[] = [];
      while (i < lines.length) {
        const m = LIST_ITEM.exec(lines[i]);
        if (m) {
          const indent = m[1].replace(/\t/g, "    ").length;
          while (stack.length && stack[stack.length - 1].indent > indent) stack.pop();
          const marker = /\d/.test(m[2]) ? "#" : "*";
          if (stack.length && stack[stack.length - 1].indent === indent) stack[stack.length - 1].marker = marker;
          else stack.push({ indent, marker });
          out.push(`${stack.map((s) => s.marker).join("")} ${inline(taskBox(m[3]))}`);
        } else if (lines[i].trim() !== "" && /^\s+/.test(lines[i])) {
          out[out.length - 1] += ` ${inline(lines[i].trim())}`; // continuation line
        } else break;
        i++;
      }
      continue;
    }

    // Blank line
    if (line.trim() === "") {
      out.push("");
      i++;
      continue;
    }

    // Paragraph
    const para: string[] = [];
    while (i < lines.length && lines[i].trim() !== "" && !startsBlock(lines, i)) para.push(lines[i++]);
    out.push(para.map((l) => inline(l.replace(/(\s{2,}|\\)$/, "\\\\"))).join("\n"));
  }

  return out.join("\n").replace(/\n{3,}/g, "\n\n").trim() + "\n";
}

function startsBlock(lines: string[], i: number): boolean {
  const l = lines[i];
  return (
    /^\s*(`{3,}|~{3,})/.test(l) ||
    /^\s{0,3}#{1,6}\s/.test(l) ||
    /^\s{0,3}>/.test(l) ||
    LIST_ITEM.test(l) ||
    (l.includes("|") && i + 1 < lines.length && TABLE_SEP.test(lines[i + 1]) && lines[i + 1].includes("-"))
  ) && i > 0 && lines[i - 1] !== undefined;
}

function taskBox(text: string): string {
  return text.replace(/^\[ \]\s+/, "(x) ").replace(/^\[[xX]\]\s+/, "(/) ");
}

function splitRow(row: string): string[] {
  const cells = row.trim().replace(/^\|/, "").replace(/(?<!\\)\|$/, "").split(/(?<!\\)\|/);
  return cells.map((c) => c.trim().replace(/\\\|/g, "|"));
}

export function inline(text: string): string {
  const stash = new Stash();
  let s = text;

  // Code spans
  s = s.replace(/(`+)(.+?)\1/g, (_, __, code) => stash.put(`{{${code.trim()}}}`));

  // Images
  s = s.replace(/!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g, (_, alt, url) =>
    stash.put(alt ? `!${url}|alt=${alt}!` : `!${url}!`),
  );

  // Links
  s = s.replace(/\[([^\]]+)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g, (_, label, url) =>
    stash.put(`[${inline(label)}|${url}]`),
  );
  s = s.replace(/<((?:https?|ftp|mailto):[^>\s]+)>/g, (_, url) => stash.put(`[${url}]`));

  // Line breaks
  s = s.replace(/<br\s*\/?>/gi, () => stash.put("\\\\"));

  // Bold first (via marker), then italics, so the two never collide on `*`.
  s = s.replace(/\*\*(?=\S)(.+?)(?<=\S)\*\*(?!\*)/g, `${BOLD}$1${BOLD}`);
  s = s.replace(/(?<![\w])__(?=\S)(.+?)(?<=\S)__(?![\w])/g, `${BOLD}$1${BOLD}`);
  s = s.replace(/(?<![\w*])\*(?=[^\s*])(.+?)(?<=[^\s*])\*(?![\w*])/g, "_$1_");
  s = s.replaceAll(BOLD, "*");

  // Strikethrough
  s = s.replace(/~~(?=\S)(.+?)(?<=\S)~~/g, "-$1-");

  // Inline HTML equivalents
  s = s
    .replace(/<(?:u|ins)>(.*?)<\/(?:u|ins)>/gi, "+$1+")
    .replace(/<sup>(.*?)<\/sup>/gi, "^$1^")
    .replace(/<sub>(.*?)<\/sub>/gi, "~$1~")
    .replace(/<cite>(.*?)<\/cite>/gi, "??$1??");

  return stash.restore(s);
}
