/**
 * 标签配色。
 *
 * 每个标签的颜色由名字哈希决定，同一个标签在全站始终同一个颜色——
 * 换页面就变色会让人以为不是同一个东西。
 *
 * 只存 base 色相，背景/文字/描边都用 color-mix 从它派生，
 * 深浅色模式下不必各写一套。
 */
const PALETTE = [
  "oklch(0.76 0.13 70)", // amber
  "oklch(0.77 0.16 122)", // lime
  "oklch(0.80 0.15 101)", // yellow
  "oklch(0.62 0.18 293)", // purple
  "oklch(0.71 0.16 48)", // orange
  "oklch(0.72 0.10 221)", // cyan
  "oklch(0.64 0.19 27)", // red
  "oklch(0.66 0.21 323)", // magenta
  "oklch(0.70 0.13 162)", // green
  "oklch(0.67 0.19 3)", // pink
] as const;

/** 常用标签：发布时一键选，省得每次手打（也可自定义）。 */
export const COMMON_TAGS = [
  "前端",
  "后端",
  "数据",
  "AI",
  "部署",
  "算法",
  "工具",
  "踩坑",
  "复盘",
  "论文",
] as const;

function hash(text: string): number {
  let value = 0;
  for (let i = 0; i < text.length; i += 1) {
    value = (value * 31 + text.charCodeAt(i)) % 100000;
  }
  return value;
}

export function tagBaseColor(tag: string): string {
  return PALETTE[hash(tag) % PALETTE.length];
}

/** 标签胶囊的样式：底色和文字都从 base 色相推导，深浅色自动适配。 */
export function tagChipStyle(tag: string): React.CSSProperties {
  const base = tagBaseColor(tag);
  return {
    background: `color-mix(in oklch, ${base} 16%, transparent)`,
    color: `color-mix(in oklch, ${base} 72%, var(--foreground))`,
    borderColor: `color-mix(in oklch, ${base} 34%, transparent)`,
  };
}
