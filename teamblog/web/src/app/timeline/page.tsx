import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { MilestonesTimeline } from "@/components/milestones-timeline";
import { MILESTONES, STATUS_LABEL } from "@/lib/timeline";

import { PageHeader } from "@/components/page-header";
/**
 * 版本时间线（静态页）。
 *
 * 数据在 lib/timeline.ts，首页的精简版与之共用一份。
 * 日期取真实提交时间，不编造；未完成的事项标注为"计划中"。
 */
export const metadata = {
  title: "版本时间线 · 欲晓",
  description: "项目各阶段的完成情况与后续计划",
};

export default function TimelinePage() {
  return (
    <div className="space-y-10">
      <PageHeader
        size="large"
        title="版本时间线"
        description="按开发顺序记录各阶段的完成情况。日期取真实提交时间，未完成为计划。"
      />

      <MilestonesTimeline />

      <ol className="relative space-y-6 border-l pl-6">
        {MILESTONES.map((item) => (
          <li key={item.date + item.title} className="relative">
            <span
              className={[
                "absolute -left-8 top-1.5 size-3 rounded-full border-2 border-background",
                item.status === "done"
                  ? "bg-emerald-500"
                  : item.status === "doing"
                    ? "bg-amber-500"
                    : "bg-muted-foreground/40",
              ].join(" ")}
              aria-hidden
            />
            <div className="space-y-1.5">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="font-medium">{item.title}</h2>
                <Badge variant={item.status === "done" ? "secondary" : "outline"}>
                  {STATUS_LABEL[item.status]}
                </Badge>
                <span className="text-xs text-muted-foreground">{item.date}</span>
              </div>
              <p className="text-sm text-muted-foreground">{item.detail}</p>
              <div className="flex flex-wrap gap-1.5 pt-1">
                {item.items.map((entry) => (
                  <span
                    key={entry}
                    className="rounded border px-1.5 py-0.5 text-xs text-muted-foreground"
                  >
                    {entry}
                  </span>
                ))}
              </div>
            </div>
          </li>
        ))}
      </ol>

      <div>
        <Link href="/" className={buttonVariants({ variant: "ghost" })}>
          ← 回到首页
        </Link>
      </div>
    </div>
  );
}