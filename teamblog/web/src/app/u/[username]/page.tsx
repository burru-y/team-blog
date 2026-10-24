import Link from "next/link";
import { notFound } from "next/navigation";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { ApiError } from "@/lib/api";
import { serverApi } from "@/lib/server-api";

/**
 * 同首页：每次都要最新数据，且构建时不依赖后端（见 app/page.tsx 的说明）。
 */
export const dynamic = "force-dynamic";

/**
 * 个人页 `/u/{username}`（公开，访客可看）。
 *
 * 邮箱是否出现由后端按 `email_public` 决定（S-10）——前端不做判断，
 * 只渲染拿到的字段。这样"隐私规则"只有一处，不会前后端各写一遍。
 */
export default async function ProfilePage({ params }: { params: Promise<{ username: string }> }) {
  // Next 15：params 是 Promise，必须 await（T-04）
  const { username } = await params;

  let profile;
  try {
    profile = await serverApi.getUserProfile(username);
  } catch (error) {
    if (error instanceof ApiError && error.code === "NOT_FOUND") {
      notFound(); // 抛特殊错误，渲染 app/not-found.tsx
    }
    throw error;
  }

  const joined = new Date(profile.createdAt).toLocaleDateString("zh-CN");

  return (
    <div className="space-y-8">
      <section className="flex flex-col gap-4 sm:flex-row sm:items-center">
        <Avatar className="size-16">
          <AvatarFallback className="text-lg">
            {profile.username.slice(0, 2).toUpperCase()}
          </AvatarFallback>
        </Avatar>

        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">{profile.username}</h1>
            <Badge variant={profile.role === "ADMIN" ? "default" : "secondary"}>
              {profile.role === "ADMIN" ? "管理员" : "成员"}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground">
            {profile.totalPoints} 分 · {joined} 加入
          </p>
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        <Card className="sm:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">简介</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <p className="whitespace-pre-wrap text-muted-foreground">
              {profile.bio || "这个人很神秘，还没有写简介。"}
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
            {profile.email ? (
              <p className="text-muted-foreground">邮箱：{profile.email}</p>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">成果</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            批次 3 上线后，这里会显示项目、证书与奖项。
          </CardContent>
        </Card>
      </section>

      <div>
        <Link href="/" className={buttonVariants({ variant: "ghost" })}>
          ← 回到团队首页
        </Link>
      </div>
    </div>
  );
}
