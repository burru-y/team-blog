"use client";

import { Timeline, type TimelineEvent } from "@/components/motion/timeline";
import type { Announcement } from "@/lib/types";

/**
 * 横向版本时间轴（方案 C）。
 *
 * 只喂「更新日志」类型：公告没有版本号，放进版本轴会显得莫名其妙。
 * 初始焦点放在**最新版本**——默认落在第一个（最旧）会让人一进来就看见几个月前的东西。
 */
export function VersionTimeline({ versions }: { versions: Announcement[] }) {
  if (versions.length === 0) {
    return null;
  }

  // beUI 组件按日期升序排列，这里也按发布时间升序喂进去，最新在最后
  const ordered = versions
    .slice()
    .sort((a, b) => a.publishedAt.localeCompare(b.publishedAt));

  const events: TimelineEvent[] = ordered.map((item) => ({
    id: item.id,
    date: item.publishedAt.slice(0, 10),
    title: `${item.version ?? ""} ${item.title}`.trim(),
    media: (
      <div className="flex h-full flex-col justify-between gap-2 p-3">
        <p className="line-clamp-4 text-xs leading-5 text-muted-foreground">
          {countLines(item.bodyMd)}
        </p>
        <span className="text-xs text-muted-foreground">
          {item.version ?? ""}
        </span>
      </div>
    ),
    meta: <span className="truncate text-xs">{(item.version ?? "").toUpperCase()}</span>,
  }));

  return (
    <div className="rounded-xl border bg-muted/20">
      <Timeline
        events={events}
        // 初始聚焦最新一条
        defaultValue={ordered[ordered.length - 1]?.id}
        itemWidth={240}
        height={300}
        years
        ruler
        cables
        aria-label="版本时间轴"
      />
    </div>
  );
}

/** 从正文里抽一行摘要：取第一条列表项，没有就取首行文字。 */
function countLines(bodyMd: string): string {
  const lines = bodyMd.split("\n").map((line) => line.trim()).filter(Boolean);
  const bullet = lines.find((line) => line.startsWith("- "));
  if (bullet) {
    const items = lines.filter((line) => line.startsWith("- "));
    return `${bullet.slice(2)} 等 ${items.length} 项改动`;
  }
  return lines.find((line) => !line.startsWith("#")) ?? "";
}
