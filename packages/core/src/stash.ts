/**
 * Placeholder stash: protects already-converted fragments (code spans, links,
 * images) from being touched by later regex passes.
 */
const OPEN = "";
const CLOSE = "";
let nextId = 0;

export class Stash {
  private items: string[] = [];
  private id = nextId++;
  // Unique per instance so nested stashes never resolve each other's tokens.
  private token = new RegExp(`${OPEN}${this.id}:(\\d+)${CLOSE}`, "g");

  put(value: string): string {
    this.items.push(value);
    return `${OPEN}${this.id}:${this.items.length - 1}${CLOSE}`;
  }

  /** Restores every placeholder, including ones nested inside other stashed values. */
  restore(text: string): string {
    let out = text;
    for (let i = 0; i < 10 && out.includes(`${OPEN}${this.id}:`); i++) {
      out = out.replace(this.token, (_, n) => this.items[Number(n)]);
    }
    return out;
  }
}
