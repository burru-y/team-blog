import Link from "next/link";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { CheckinCard } from "@/components/checkin-card";
import { EmptyState } from "@/components/empty-state";
import { TiltCard } from "@/components/motion/tilt-card";
import { Heatmap } from "@/components/heatmap";
import { serverApi } from "@/lib/server-api";
import { STATUS_LABEL, VERSIONS } from "@/lib/timeline";

/**
 * 强制动态渲染。
 *
 * 两个原因：
 * 1. 数据每次都要最新（改库里的 bio 刷新即变，Gate G2 的验收方式）
 * 2. 构建时不能依赖后端——CI 里 `pnpm build` 没有 API 在跑，静态预渲染会直接失败
 *
 * 注意：`no-store` 写在 fetch 里不够，因为 fetch 被包了一层，Next 的静态分析
 * 看不穿；这里用显式声明兜底。
 */
export const dynamic = "force-dynamic";

/**
 * 首页：团队介绍 + 成员列表。
 *
 * 数据在服务端取（T-02），所以首屏没有"先空一下再填"的闪动。
 */
export default async function HomePage() {
  const page = await serverApi.listMembers(undefined, 12);
  // 热力图是团队视角：不给 username。失败不该让首页整页挂掉，所以单独兜住
  const heatmap = await serverApi.getHeatmap(undefined, 119).catch(() => null);

  const checkin = heatmap ? Object.values(heatmap.days).reduce((sum, n) => sum + n, 0) : 0;
  const activeDays = heatmap ? Object.keys(heatmap.days).length : 0;

  return (
    <div className="space-y-12">
      <section className="animate-fade-up space-y-6 pt-6">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline">一期 · 内测</Badge>
          <Badge variant="secondary">名称待定</Badge>
        </div>

        <h1 className="max-w-3xl text-4xl leading-tight font-semibold sm:text-5xl">
          团队技术博客
          <span className="block text-muted-foreground">
            记录每一次把问题想清楚的<span className="text-brand">过程</span>。
          </span>
        </h1>

        <p className="max-w-2xl text-base leading-relaxed text-muted-foreground">
          成员在这里发布项目成果与学习笔记，每天打卡积累积分；
          访客可以浏览公开内容，并就任意项目向 AI 提问。
        </p>

        <div className="flex flex-wrap gap-3">
          <Link href="/projects" className={buttonVariants()}>
            看看成果
          </Link>
          <Link href="/publish/learning" className={buttonVariants({ variant: "outline" })}>
            写篇笔记
          </Link>
        </div>

        <dl className="grid grid-cols-2 gap-4 border-t pt-6 sm:grid-cols-4">
          <Stat label="成员" value={page.items.length} />
          <Stat label="打卡天数" value={activeDays} />
          <Stat label="累计打卡" value={checkin} />
          <Stat label="当前版本" value="v1.0" text />
        </dl>
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">团队打卡</CardTitle>
            <CardDescription>最近 17 周，颜色越深当天打卡的人越多</CardDescription>
          </CardHeader>
          <CardContent>
            {heatmap ? (
              <Heatmap data={heatmap} emptyLabel="无人打卡" />
            ) : (
              <p className="text-sm text-muted-foreground">热力图暂时取不到数据。</p>
            )}
          </CardContent>
        </Card>
        <CheckinCard />
      </section>

      <section className="space-y-4">
        <div className="flex items-baseline justify-between">
          <h2 className="text-xl font-semibold tracking-tight">团队成员</h2>
          <span className="text-sm text-muted-foreground">共 {page.items.length} 人</span>
        </div>

        {page.items.length === 0 ? (
          <EmptyState title="还没有成员" description="管理员生成邀请链接后，成员凭链接注册。" />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {page.items.map((member) => {
              const name = member.displayName ?? member.username;
              return (
                <TiltCard key={member.id} className="border bg-card">
                  <Link href={`/u/${member.username}`} className="block p-5">
                    <div className="flex items-center gap-3">
                      <Avatar className="size-11">
                        <AvatarFallback>{name.slice(0, 2).toUpperCase()}</AvatarFallback>
                      </Avatar>
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium">{name}</p>
                        <p className="truncate text-xs text-muted-foreground">
                          {name !== member.username ? `@${member.username}` : "成员"}
                        </p>
                      </div>
                      {member.role === "ADMIN" ? (
                        <Badge variant="secondary" className="shrink-0 text-xs">
                          管理员
                        </Badge>
                      ) : null}
                    </div>

                    <p className="mt-3 line-clamp-2 min-h-10 text-xs leading-relaxed text-muted-foreground">
                      {member.bio || "还没有写简介"}
                    </p>

                    <div className="mt-3 flex items-baseline justify-between border-t pt-3">
                      <span className="text-xs text-muted-foreground">积分</span>
                      <span className="font-medium tabular-nums">{member.totalPoints}</span>
                    </div>
                  </Link>
                </TiltCard>
              );
            })}
          </div>
        )}

        {page.nextCursor ? (
          <div className="text-center">
            {/* 批次 2 只做首屏；分页按钮等成员变多再加 */}
            <Link href="/" className={buttonVariants({ variant: "outline" })}>
              加载更多（下一批）
            </Link>
          </div>
        ) : null}
      </section>

      <section className="space-y-3 border-t pt-6">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="text-lg font-semibold">版本</h2>
          <Link
            href="/timeline"
            className="text-sm text-muted-foreground underline underline-offset-4"
          >
            开发时间线
          </Link>
        </div>

        <div className="space-y-2">
          {VERSIONS.map((item) => (
            <div
              key={item.version}
              className="card-lift flex flex-wrap items-baseline gap-2 rounded-lg border px-4 py-3 hover:card-lift-hover"
            >
              <span className="font-mono text-sm">{item.version}</span>
              <span className="font-medium">{item.name}</span>
              <Badge variant={item.status === "done" ? "secondary" : "outline"}>
                {STATUS_LABEL[item.status]}
              </Badge>
              <span className="text-xs text-muted-foreground">{item.date}</span>
              <p className="w-full text-xs text-muted-foreground">{item.note}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
/** Hero 区的小统计项。value 为数字时按等宽数字排版，避免跳动。 */
function Stat({ label, value, text }: { label: string; value: number | string; text?: boolean }) {
  return (
    <div className="space-y-1">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={text ? "text-2xl font-semibold" : "text-2xl font-semibold tabular-nums"}>
        {value}
      </dd>
    </div>
  );
}
