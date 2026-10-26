import Link from "next/link";

import { PageHeader } from "@/components/page-header";
import { VersionChangelog } from "@/components/version-changelog";
import { VersionTimeline } from "@/components/version-timeline";
import { buttonVariants } from "@/components/ui/button";
import { serverApi } from "@/lib/server-api";
import type { Announcement } from "@/lib/types";

export const metadata = {
  title: "版本时间线 · 欲晓",
  description: "每个版本改了什么，按新增 / 改进 / 修复分类展示。",
};

// 版本数据来自公告表，不能静态预渲染
export const dynamic = "force-dynamic";

/**
 * 版本时间线。
 *
 * 上：横向时间轴（方案 C），一眼看项目推进到哪。
 * 下：按版本折叠的改动分类（方案 B），看细节。
 *
 * 数据源是公告表里的 CHANGELOG 记录：发版时在后台写一条，这里自动出现，
 * 不需要改代码。
 */
export default async function TimelinePage() {
  const versions = await serverApi.listAnnouncements("CHANGELOG").catch(() => [] as Announcement[]);

  return (
    <div className="space-y-10">
      <PageHeader
        size="large"
        title="版本时间线"
        description="每个版本改了什么。最新在最上面，点版本号展开细节。"
        meta={<span>{versions.length} 个版本</span>}
        actions={
          <Link href="/announcements" className={buttonVariants({ variant: "outline", size: "sm" })}>
            公告与更新日志
          </Link>
        }
      />

      {versions.length === 0 ? (
        <div className="rounded-xl border border-dashed px-6 py-14 text-center">
          <p className="text-sm font-medium">还没有版本记录</p>
          <p className="mt-1 text-xs text-muted-foreground">
            管理员在「后台 → 公告」发布更新日志后，这里会自动出现。
          </p>
        </div>
      ) : (
        <>
          <VersionTimeline versions={versions} />

          <section className="space-y-4">
            <h2 className="text-lg font-semibold tracking-tight">各版本改动</h2>
            <VersionChangelog versions={versions} />
          </section>
        </>
      )}

      <div className="border-t pt-6">
        <Link href="/" className={buttonVariants({ variant: "ghost" })}>
          ← 回到首页
        </Link>
      </div>
    </div>
  );
}
