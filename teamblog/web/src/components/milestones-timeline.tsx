"use client";

import { Timeline, type TimelineEvent } from "@/components/motion/timeline";
import { MILESTONES } from "@/lib/timeline";

/**
 * 时间线的横向可拖拽版本（beUI 组件）。
 *
 * 只喂已完成的里程碑：`date` 需要能解析成日期，而"待定"的计划项解析不了。
 * 下方的纵向列表仍然保留——它是无 JS 也能读的版本，这个组件只是增强。
 */
export function MilestonesTimeline() {
  const events: TimelineEvent[] = MILESTONES.filter(
    (item) => item.status !== "planned" && /^\d{4}-\d{2}-\d{2}/.test(item.date),
  ).map((item) => ({
    id: item.title,
    date: item.date.slice(0, 10),
    title: item.title,
    media: <p className="line-clamp-4 text-xs leading-relaxed text-muted-foreground">{item.detail}</p>,
    meta: (
      <div className="flex flex-wrap gap-1">
        {item.items.slice(0, 2).map((tag) => (
          <span key={tag} className="rounded border px-1.5 py-0.5 text-xs text-muted-foreground">
            {tag}
          </span>
        ))}
      </div>
    ),
  }));

  return (
    <div className="rounded-xl border bg-muted/20">
      <Timeline
        events={events}
        itemWidth={220}
        height={280}
        years
        ruler
        cables
        aria-label="项目开发里程碑"
      />
    </div>
  );
}
