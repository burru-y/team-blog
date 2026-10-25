"use client";

import { Timeline, type TimelineEvent } from "@/components/motion/timeline";
import { MILESTONES } from "@/lib/timeline";

/**
 * 时间线的横向可拖拽版本（beUI 组件）。
 *
 * 只喂已完成的里程碑：`date` 需要能解析成日期，而"待定"的计划项解析不了。
 * 下方的纵向列表仍然保留——它是无 JS 也能读的版本，这个组件只是增强。
 *
 * 布局注意：卡片里的 media 是固定 16:10 的框，内容要自己负责内边距和纵向分布，
 * 否则文字会贴在左上角、下半截空着。
 */
export function MilestonesTimeline() {
  const events: TimelineEvent[] = MILESTONES.filter(
    (item) => item.status !== "planned" && /^\d{4}-\d{2}-\d{2}/.test(item.date),
  ).map((item) => ({
    id: item.title,
    date: item.date.slice(0, 10),
    title: item.title,
    media: (
      <div className="flex h-full flex-col justify-between gap-2 p-3">
        <p className="line-clamp-4 text-xs leading-5 text-muted-foreground">{item.detail}</p>
        <div className="flex flex-wrap gap-1">
          {item.items.slice(0, 2).map((tag) => (
            <span
              key={tag}
              className="rounded border px-1.5 py-0.5 text-xs leading-none text-muted-foreground"
            >
              {tag}
            </span>
          ))}
        </div>
      </div>
    ),
    // meta 那一行在组件里固定 text-sm，塞标签会比正文还大，所以只放一条摘要
    meta: (
      <span className="truncate text-xs">
        {item.items.length > 2 ? `共 ${item.items.length} 项` : (item.items[0] ?? "")}
      </span>
    ),
  }));

  return (
    <div className="rounded-xl border bg-muted/20">
      <Timeline
        events={events}
        itemWidth={240}
        height={300}
        years
        ruler
        cables
        aria-label="项目开发里程碑"
      />
    </div>
  );
}
