"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { useSession } from "@/lib/session";

/**
 * 顶部导航。
 *
 * 为什么是客户端组件：登录态在 SessionProvider 里（内存中的 access token），
 * 服务端渲染时读不到。代价是首屏会有一瞬间的"未登录"——用 loading 状态遮掉。
 */
export function SiteHeader() {
  const { user, loading, logout } = useSession();
  const router = useRouter();

  async function handleLogout() {
    await logout();
    router.push("/login");
    router.refresh();
  }

  return (
    <header className="border-b">
      <div className="mx-auto flex h-14 w-full max-w-5xl items-center justify-between px-4">
        <Link href="/" className="flex items-center gap-2 font-semibold tracking-tight">
          <span className="text-lg">TeamBlog</span>
          <Badge variant="secondary" className="hidden sm:inline-flex">
            团队技术博客
          </Badge>
        </Link>

        <nav className="flex items-center gap-3">
          {loading ? (
            <span className="text-sm text-muted-foreground">…</span>
          ) : user ? (
            <>
              <Avatar className="size-8">
                <AvatarFallback>{user.username.slice(0, 2).toUpperCase()}</AvatarFallback>
              </Avatar>
              <span className="hidden text-sm text-muted-foreground sm:inline">{user.username}</span>
              <Button variant="ghost" size="sm" onClick={handleLogout}>
                退出
              </Button>
            </>
          ) : (
            <Link href="/login" className={buttonVariants({ size: "sm" })}>
              登录
            </Link>
          )}
        </nav>
      </div>
    </header>
  );
}
