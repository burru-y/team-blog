import Link from "next/link";
import { notFound } from "next/navigation";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ActivityCard } from "@/components/activity-card";
import { toContributions } from "@/lib/contributions";
import { ProfileEditor } from "@/components/profile-editor";
import { ApiError } from "@/lib/api";
import { serverApi } from "@/lib/server-api";

/** 每次都要最新数据，且构建时不依赖后端（见 app/page.tsx 的说明）。 */
export const dynamic = "force-dynamic";

/**
 * 个人页 `/u/{username}`（公开，访客可看）。
 *
 * 邮箱是否出现由后端按 `email_public` 决定（S-10），前端不做判断，只渲染拿到的字段。
 * 这样隐私规则只有一处实现。
 */
export default async function ProfilePage({ params }: { params: Promise<{ username: string }> }) {
  // Next 15：params 是 Promise，必须 await
  const { username } = await params;

  let profile;
  try {
    profile = await serverApi.getUserProfile(username);
  } catch (error) {
    if (error instanceof ApiError && error.code === "NOT_FOUND") {
      notFound();
    }
    throw error;
  }

  const joined = new Date(profile.createdAt).toLocaleDateString("zh-CN");
  const [logs, heatmap, projects] = await Promise.all([
    serverApi.listLearningLogs(profile.username),
    serverApi.getHeatmap(profile.username, 365).catch(() => null),
    serverApi.listProjects(undefined, 100, profile.username).catch(() => ({ items: [], nextCursor: null })),
  ]);

  const checkinDays = heatmap ? Object.keys(heatmap.days).length : 0;

  return (
    <div className="space-y-10">
      <section className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-4">
          <Avatar className="size-16">
            <AvatarFallback className="text-lg">
              {profile.displayName.slice(0, 2).toUpperCase()}
            </AvatarFallback>
          </Avatar>

          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <h1 className="text-3xl font-semibold tracking-tight">{profile.displayName}</h1>
              {profile.displayName !== profile.username ? (
                <span className="text-sm text-muted-foreground">@{profile.username}</span>
              ) : null}
              <Badge variant={profile.role === "ADMIN" ? "default" : "secondary"}>
                {profile.role === "ADMIN" ? "管理员" : "成员"}
              </Badge>
              <Badge variant="outline">Lv{profile.level}</Badge>
            </div>
            <p className="text-sm text-muted-foreground">
              {profile.totalPoints} 分 · {joined} 加入
            </p>
          </div>
        </div>

        <ProfileEditor profile={profile} />
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        <Card className="card-lift sm:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">简介</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <p className="whitespace-pre-wrap text-muted-foreground">
              {profile.bio || "这个人还没有写简介。"}
            </p>
            {profile.githubUrl ? (
              <a
                href={profile.githubUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary underline-offset-4 hover:underline"
              >
                {profile.githubUrl}
              </a>
            ) : null}
            {profile.email ? <p className="text-muted-foreground">邮箱：{profile.email}</p> : null}
          </CardContent>
        </Card>

        <Card className="card-lift">
          <CardHeader>
            <CardTitle className="text-base">数据</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <Stat label="成果" value={projects.items.length} />
            <Stat label="学习内容" value={logs.length} />
            <Stat label="打卡天数" value={checkinDays} />
            <Stat label="积分" value={profile.totalPoints} />
          </CardContent>
        </Card>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">成果</h2>
        {projects.items.length === 0 ? (
          <Card>
            <CardContent className="py-8 text-center text-sm text-muted-foreground">
              还没有发布成果。
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-2">
            {projects.items.map((project) => (
              <Link
                key={project.id}
                href={`/projects/${project.id}`}
                className="flex items-baseline justify-between gap-3 rounded-lg border px-4 py-3 transition-colors hover:border-foreground/20"
              >
                <span className="truncate font-medium">{project.title}</span>
                <span className="shrink-0 text-xs text-muted-foreground">
                  {new Date(project.createdAt).toLocaleDateString("zh-CN")}
                </span>
              </Link>
            ))}
          </div>
        )}
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">打卡记录</h2>
        {heatmap ? (
          <ActivityCard contributions={toContributions(heatmap)} label="打卡活跃度" />
        ) : (
          <p className="text-sm text-muted-foreground">暂时取不到打卡数据。</p>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">学习内容</h2>
        {logs.length === 0 ? (
          <Card>
            <CardContent className="py-8 text-center text-sm text-muted-foreground">
              还没有发布学习内容。
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-2">
            {logs.map((log) => (
              <Link
                key={log.id}
                href={`/learning/${log.id}`}
                className="flex items-baseline justify-between rounded-lg border px-4 py-3 transition-colors hover:border-foreground/20"
              >
                <span className="font-medium">{log.title}</span>
                <span className="text-xs text-muted-foreground">{log.logDate}</span>
              </Link>
            ))}
          </div>
        )}
      </section>

      <div>
        <Link href="/" className={buttonVariants({ variant: "ghost" })}>
          ← 回到团队首页
        </Link>
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-baseline justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium tabular-nums">{value}</span>
    </div>
  );
}
