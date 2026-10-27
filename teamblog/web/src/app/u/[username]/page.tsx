import Link from "next/link";
import { notFound } from "next/navigation";
import { CalendarDays, Code2, Link2, Mail } from "lucide-react";

import { ActivityCard } from "@/components/activity-card";
import { ProfileEditor } from "@/components/profile-editor";
import { UserAvatar } from "@/components/user-avatar";
import { TiltCard } from "@/components/motion/tilt-card";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { toContributions } from "@/lib/contributions";
import { serverApi } from "@/lib/server-api";

/** 每次都要最新数据，且构建时不依赖后端（见 app/page.tsx 的说明）。 */
export const dynamic = "force-dynamic";

/**
 * 个人主页 = 这个人的博客首页。
 *
 * 布局参照常见博客主题：左边是文章流，右边是作者卡与数据。
 * 不另做一套「个人博客」站点——同一个人的笔记和成果本来就在这里，再复制一份只会两处不同步。
 *
 * 文章 = 学习笔记。项目成果另列，因为它们的阅读预期不同：
 * 笔记是「一段时间内的记录」，成果是「一个可交付的东西」。
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

  const [logs, heatmap, projects] = await Promise.all([
    serverApi.listLearningLogs(profile.username),
    serverApi.getHeatmap(profile.username, 365).catch(() => null),
    serverApi
      .listProjects(undefined, 100, profile.username)
      .catch(() => ({ items: [], nextCursor: null })),
  ]);

  const joined = new Date(profile.createdAt).toLocaleDateString("zh-CN", {
    year: "numeric",
    month: "long",
  });
  const checkinDays = heatmap ? Object.keys(heatmap.days).length : 0;
  const name = profile.displayName ?? profile.username;

  return (
    <div className="space-y-10">
      {/* 作者区：头像、名字、简介、外链与数据 */}
      <header className="space-y-5 border-b pb-8">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-start gap-4">
            <UserAvatar name={name} avatarUrl={profile.avatarUrl} className="size-20 text-xl" />
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-3xl font-semibold tracking-tight">{name}</h1>
                {profile.displayName !== profile.username ? (
                  <span className="text-sm text-muted-foreground">@{profile.username}</span>
                ) : null}
                <Badge variant={profile.role === "ADMIN" ? "default" : "secondary"}>
                  {profile.role === "ADMIN" ? "管理员" : "成员"}
                </Badge>
                <Badge variant="outline">Lv{profile.level}</Badge>
              </div>

              <p className="max-w-2xl whitespace-pre-wrap text-sm leading-relaxed text-muted-foreground">
                {profile.bio || "这个人还没有写简介。"}
              </p>

              <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs text-muted-foreground">
                <span className="inline-flex items-center gap-1">
                  <CalendarDays className="size-3.5" aria-hidden />
                  {joined}加入
                </span>
                {profile.githubUrl ? (
                  <a
                    href={profile.githubUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 hover:text-foreground"
                  >
                    <Code2 className="size-3.5" aria-hidden />
                    GitHub
                  </a>
                ) : null}
                {profile.blogUrl ? (
                  <a
                    href={profile.blogUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 hover:text-foreground"
                  >
                    <Link2 className="size-3.5" aria-hidden />
                    个人博客
                  </a>
                ) : null}
                {profile.email ? (
                  <span className="inline-flex items-center gap-1">
                    <Mail className="size-3.5" aria-hidden />
                    {profile.email}
                  </span>
                ) : null}
              </div>
            </div>
          </div>

          <ProfileEditor profile={profile} />
        </div>

        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[
            { label: "文章", value: logs.length },
            { label: "成果", value: projects.items.length },
            { label: "打卡天数", value: checkinDays },
            { label: "积分", value: profile.totalPoints },
          ].map((item) => (
            <div key={item.label} className="rounded-lg border px-4 py-3">
              <dt className="text-xs text-muted-foreground">{item.label}</dt>
              <dd className="mt-0.5 text-xl font-semibold tabular-nums">{item.value}</dd>
            </div>
          ))}
        </dl>
      </header>

      <div className="grid gap-10 lg:grid-cols-[1fr_18rem]">
        {/* 主列：文章流 */}
        <div className="min-w-0 space-y-6">
          <div className="flex items-baseline justify-between gap-3">
            <h2 className="text-lg font-semibold tracking-tight">文章</h2>
            <span className="text-xs text-muted-foreground">共 {logs.length} 篇</span>
          </div>

          {logs.length === 0 ? (
            <div className="rounded-xl border border-dashed px-6 py-12 text-center">
              <p className="text-sm font-medium">还没有写文章</p>
              <p className="mt-1 text-xs text-muted-foreground">
                学习笔记会显示在这里，按时间倒序排列。
              </p>
            </div>
          ) : (
            <ul className="divide-y">
              {logs.map((log) => (
                <li key={log.id}>
                  <Link href={`/learning/${log.id}`} className="group block py-5">
                    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <time className="tabular-nums">{log.logDate}</time>
                      <Badge variant={log.type === "CODE" ? "secondary" : "outline"} className="text-xs">
                        {log.type === "CODE" ? "代码" : "笔记"}
                      </Badge>
                    </div>
                    <h3 className="mt-1.5 text-base font-medium group-hover:text-brand">
                      {log.title}
                    </h3>
                    {excerpt(log) ? (
                      <p className="mt-1 line-clamp-2 text-sm leading-relaxed text-muted-foreground">
                        {excerpt(log)}
                      </p>
                    ) : null}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* 侧栏：成果 + 打卡 */}
        <aside className="space-y-6">
          <section className="space-y-3">
            <h2 className="text-sm font-semibold tracking-tight">成果</h2>
            {projects.items.length === 0 ? (
              <p className="text-xs text-muted-foreground">还没有发布成果。</p>
            ) : (
              <ul className="space-y-2">
                {projects.items.map((project) => (
                  <li key={project.id}>
                    <Link
                      href={`/projects/${project.id}`}
                      className="block rounded-lg border px-3 py-2.5 transition-colors hover:border-foreground/20"
                    >
                      <span className="line-clamp-1 text-sm font-medium">{project.title}</span>
                      <span className="mt-0.5 block text-xs text-muted-foreground">
                        {new Date(project.createdAt).toLocaleDateString("zh-CN")}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="space-y-3">
            <div className="flex items-baseline justify-between">
              <h2 className="text-sm font-semibold tracking-tight">打卡活跃度</h2>
              <span className="text-xs text-muted-foreground">{checkinDays} 天</span>
            </div>
            {heatmap ? (
              <ActivityCard contributions={toContributions(heatmap)} label="近一年" />
            ) : (
              <p className="text-xs text-muted-foreground">暂时取不到打卡数据。</p>
            )}
          </section>
        </aside>
      </div>

      <div className="border-t pt-6">
        <Link href="/" className={buttonVariants({ variant: "ghost" })}>
          ← 回到团队首页
        </Link>
      </div>
    </div>
  );
}

/** 文章摘要：优先用写的摘要，没有就从正文里剥掉标记取一段。 */
function excerpt(log: { summaryMd?: string | null; contentMd?: string | null }): string {
  if (log.summaryMd?.trim()) {
    return log.summaryMd.trim();
  }
  if (!log.contentMd) {
    return "";
  }
  return log.contentMd
    .replace(/```[\s\S]*?```/g, " ") // 代码块整体去掉
    .replace(/[#>*_`[\]()!-]/g, " ") // Markdown 标记
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 140);
}
