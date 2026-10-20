import { readFileSync, writeFileSync, unlinkSync } from "node:fs";
import { convert, detectFormat, targetFileName, type Format } from "../../core/src/index.ts";

const USAGE = `markdownup <file> [--new | --in-place] [--from md|jira]
markdownup --stdin --from md|jira

  --new        write the converted result to a new file next to the original (default)
  --in-place   convert the file and replace it (the extension changes too)
  --stdin      read stdin, write the result to stdout`;

const args = process.argv.slice(2);
const flag = (name: string) => args.includes(name);
const value = (name: string) => args[args.indexOf(name) + 1];
const parseFormat = (v?: string): Format | undefined => (v === "md" ? "markdown" : v === "jira" ? "jira" : undefined);

function fail(message: string): never {
  console.error(`markdownup: ${message}\n\n${USAGE}`);
  process.exit(2);
}

if (flag("--help") || args.length === 0) {
  console.log(USAGE);
  process.exit(0);
}

if (flag("--stdin")) {
  const from = parseFormat(value("--from")) ?? fail("--from md|jira is required with --stdin");
  process.stdout.write(convert(readFileSync(0, "utf8"), from));
} else {
  const file = args.find((a) => !a.startsWith("--") && a !== value("--from")) ?? fail("missing file");
  const from = parseFormat(value("--from")) ?? detectFormat(file) ?? fail(`cannot detect format of ${file}`);
  const target = targetFileName(file, from);
  writeFileSync(target, convert(readFileSync(file, "utf8"), from));
  if (flag("--in-place")) unlinkSync(file);
  console.log(target);
}
