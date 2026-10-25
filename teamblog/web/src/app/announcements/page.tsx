import Link from "next/link";

import { Markdown } from "@/components/markdown";
import { PageHeader } from "@/components/page-header";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { serverApi } from "@/lib/server-api";
import type { Announcement } from "@/lib/types";

export const metadata = {
  title: "公告与更新日志 · 欲晓",
  description: "站点公告与每个版本的更新记录。",
};

export const dynamic = "force-dynamic";

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString("zh-CN", { timeZone: "Asia/Shanghai" });
}

/**
 * 公告与更新日志。
 *
 * 两种类型在同一页：置顶的公告在前，版本更新在后。
 * 更新日志的正文按「新增 / 改进 / 修复」分节写（发布时约定），
 * 这里直接渲染 Markdown，不在前端切分——切分规则写在内容里更灵活。
 */
export default async function AnnouncementsPage() {
  const list = await serverApi.listAnnouncements().catch(() => [] as Announcement[]);

  const notices = list.filter((item) => item.kind === "NOTICE");
  const changelogs = list.filter((item) => item.kind === "CHANGELOG");

  return (
    <div className="space-y-10">
      <PageHeader
        size="large"
        title="公告与更新日志"
        description="站点有什么变化，都在这里。"
        meta={<span>{list.length} 条记录</span>}
      />

      {list.length === 0 ? (
        <div className="rounded-xl border border-dashed px-6 py-14 text-center">
          <p className="text-sm font-medium">还没有公告</p>
          <p className="mt-1 text-xs text-muted-foreground">
            管理员发布后，访客进入站点会收到一次提示。
          </p>
        </div>
      ) : null}

      {notices.length > 0 ? (
        <section className="space-y-4">
          <h2 className="text-lg font-semibold tracking-tight">公告</h2>
          <div className="space-y-4">
            {notices.map((item) => (
              <Card key={item.id}>
                <CardHeader className="flex-row items-center gap-2 space-y-0">
                  {item.pinned ? (
                    <Badge className="shrink-0 text-xs">置顶</Badge>
                  ) : (
                    <Badge variant="outline" className="shrink-0 text-xs">
                      公告
                    </Badge>
                  )}
                  <CardTitle className="min-w-0 flex-1 text-base">{item.title}</CardTitle>
                  <time className="shrink-0 text-xs text-muted-foreground">
                    {formatDate(item.publishedAt)}
                  </time>
                </CardHeader>
                <CardContent>
                  <Markdown>{item.bodyMd}</Markdown>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>
      ) : null}

      {changelogs.length > 0 ? (
        <section className="space-y-4 border-t pt-8">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-lg font-semibold tracking-tight">版本更新</h2>
            <Link
              href="/timeline"
              className="text-xs text-muted-foreground underline underline-offset-4"
            >
              查看完整时间线
            </Link>
          </div>

          <div className="space-y-6">
            {changelogs.map((item) => (
              <article key={item.id} className="relative border-l-2 pl-5">
                {/* 圆点：当前最新版本用实心，其余空心 */}
                <span
                  aria-hidden
                  className={`absolute -left-[7px] top-1.5 size-3 rounded-full border-2 ${
                    item === changelogs[0]
                      ? "border-brand bg-brand"
                      : "border-border bg-background"
                  }`}
                />
                <div className="flex flex-wrap items-baseline gap-2">
                  <h3 className="font-mono text-sm font-semibold">{item.version}</h3>
                  <span className="text-sm font-medium">{item.title}</span>
                  {item === changelogs[0] ? (
                    <Badge variant="secondary" className="text-xs">
                      最新
                    </Badge>
                  ) : null}
                  <time className="text-xs text-muted-foreground">
                    {formatDate(item.publishedAt)}
                  </time>
                </div>
                <div className="mt-2">
                  <Markdown>{item.bodyMd}</Markdown>
                </div>
              </article>
            ))}
          </div>
        </section>
      ) : null}

      <div className="border-t pt-6">
        <Link href="/" className={buttonVariants({ variant: "ghost" })}>
          ← 返回首页
        </Link>
      </div>
    </div>
  );
}
