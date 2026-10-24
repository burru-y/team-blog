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
import { Heatmap } from "@/components/heatmap";
import { serverApi } from "@/lib/server-api";

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

  return (
    <div className="space-y-10">
      <section className="space-y-4">
        <Badge variant="outline">一期 · 内测</Badge>
        <h1 className="text-4xl font-semibold tracking-tight">团队技术博客</h1>
        <p className="max-w-2xl text-muted-foreground">
          记录团队的技术成果、学习内容与每天的学习打卡。成员可以发布项目与学习笔记，
          访客可以浏览公开内容并向 AI 提问。
        </p>
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
          <h2 className="text-xl font-semibold">团队成员</h2>
          <span className="text-sm text-muted-foreground">共 {page.items.length} 人</span>
        </div>

        {page.items.length === 0 ? (
          <Card>
            <CardContent className="py-10 text-center text-sm text-muted-foreground">
              还没有成员。管理员生成邀请链接后，成员凭链接注册。
            </CardContent>
          </Card>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {page.items.map((member) => (
              <Card key={member.id} className="transition-colors hover:border-foreground/20">
                <CardHeader className="flex-row items-center gap-3 space-y-0">
                  <Avatar className="size-10">
                    <AvatarFallback>{member.username.slice(0, 2).toUpperCase()}</AvatarFallback>
                  </Avatar>
                  <div className="min-w-0 flex-1">
                    <CardTitle className="truncate text-base">
                      <Link href={`/u/${member.username}`} className="hover:underline">
                        {member.username}
                      </Link>
                    </CardTitle>
                    <CardDescription className="flex items-center gap-2">
                      <Badge variant={member.role === "ADMIN" ? "default" : "secondary"}>
                        {member.role === "ADMIN" ? "管理员" : "成员"}
                      </Badge>
                      <span>{member.totalPoints} 分</span>
                    </CardDescription>
                  </div>
                </CardHeader>
                <CardContent className="text-sm text-muted-foreground">
                  {member.bio ? (
                    <p className="line-clamp-2">{member.bio}</p>
                  ) : (
                    <p className="italic">这个人很神秘，还没有写简介</p>
                  )}
                </CardContent>
              </Card>
            ))}
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
    </div>
  );
}
