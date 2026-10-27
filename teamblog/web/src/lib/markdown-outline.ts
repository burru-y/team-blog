/**
 * Markdown 标题锚点与目录提取。
 *
 * 目录必须和正文用同一套 id 规则，否则点了不跳转。
 * 这里把规则集中成两个函数，正文渲染与目录提取都调用它们。
 */

/** 从 Markdown 里取出 h2/h3 标题，供目录侧栏使用。 */
export type TocItem = { id: string; text: string; level: 2 | 3 };

/** 标题 → id：去掉行内标记，空白转连字符，中文保留。 */
export function headingId(text: string): string {
  return text
    .replace(/`([^`]*)`/g, "$1") // 行内代码
    .replace(/\*\*([^*]*)\*\*/g, "$1") // 粗体
    .replace(/[*_~]/g, "")
    .trim()
    .toLowerCase()
    .replace(/\s+/g, "-")
    .replace(/[^\p{L}\p{N}-]/gu, "");
}

/**
 * 提取目录。
 *
 * 跳过代码块里的 `##`（那里是注释，不是标题），否则目录里会混进代码内容。
 */
export function extractToc(markdown: string): TocItem[] {
  const items: TocItem[] = [];
  const seen = new Map<string, number>();
  let inFence = false;

  for (const rawLine of markdown.split("\n")) {
    const line = rawLine.trimEnd();

    if (line.trimStart().startsWith("```")) {
      inFence = !inFence;
      continue;
    }
    if (inFence) {
      continue;
    }

    const match = /^(#{2,3})\s+(.+)$/.exec(line);
    if (!match) {
      continue;
    }

    const level = match[1].length as 2 | 3;
    const text = match[2].replace(/#+$/, "").trim();
    let id = headingId(text);
    if (!id) {
      continue;
    }
    // 重名标题：追加序号，保证 id 唯一
    const count = seen.get(id) ?? 0;
    seen.set(id, count + 1);
    if (count > 0) {
      id = `${id}-${count}`;
    }

    items.push({ id, text, level });
  }

  return items;
}

/** 正文里第一张图片，用作文章顶部封面。 */
export function firstImage(markdown: string): string | null {
  const match = /!\[[^\]]*\]\((\S+?)(?:\s+"[^"]*")?\)/.exec(markdown);
  return match ? match[1] : null;
}
