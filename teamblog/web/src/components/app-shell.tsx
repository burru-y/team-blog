"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Clock3,
  FolderKanban,
  House,
  LogIn,
  LogOut,
  MessageSquare,
  PanelLeft,
  PenLine,
  Send,
  Settings2,
} from "lucide-react";
import type { ReactNode } from "react";

import {
  AnimatedSidebar,
  AnimatedSidebarClose,
  AnimatedSidebarContent,
  AnimatedSidebarFooter,
  AnimatedSidebarGroup,
  AnimatedSidebarGroupContent,
  AnimatedSidebarGroupLabel,
  AnimatedSidebarHeader,
  AnimatedSidebarInset,
  AnimatedSidebarMenu,
  AnimatedSidebarMenuButton,
  AnimatedSidebarMenuItem,
  AnimatedSidebarProvider,
  AnimatedSidebarRail,
  AnimatedSidebarTrigger,
} from "@/components/motion/animated-sidebar";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { useSession } from "@/lib/session";

/**
 * 应用外壳：左侧导航 + 右侧内容。
 *
 * 用 beUI 的 animated-sidebar：桌面端可折叠成图标栏，移动端变成抽屉。
 * 导航项与页头原来的那一排链接一一对应，只是换了个方向。
 *
 * 顶栏只保留三样：折叠按钮、当前页标题、用户入口——
 * 导航已经在侧栏里，顶部再放一排链接会和侧栏重复。
 */
type NavItem = {
  href: string;
  label: string;
  icon: ReactNode;
};

const MAIN_NAV: NavItem[] = [
  { href: "/", label: "首页", icon: <House className="size-4" /> },
  { href: "/projects", label: "成果", icon: <FolderKanban className="size-4" /> },
  { href: "/timeline", label: "时间线", icon: <Clock3 className="size-4" /> },
  { href: "/feedback", label: "留言", icon: <MessageSquare className="size-4" /> },
];

const MEMBER_NAV: NavItem[] = [
  { href: "/publish/learning", label: "写笔记", icon: <PenLine className="size-4" /> },
  { href: "/publish", label: "发布成果", icon: <Send className="size-4" /> },
];

const ADMIN_NAV: NavItem[] = [
  { href: "/admin", label: "后台", icon: <Settings2 className="size-4" /> },
];

/** 当前路径是否属于这一项（子路径也算选中，避免点进详情后导航全灭）。 */
function isActive(pathname: string, href: string) {
  if (href === "/") {
    return pathname === "/";
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}

function currentTitle(pathname: string) {
  const all = [...MAIN_NAV, ...MEMBER_NAV, ...ADMIN_NAV];
  const hit = all
    .slice()
    .sort((a, b) => b.href.length - a.href.length)
    .find((item) => isActive(pathname, item.href));
  return hit?.label ?? "TeamBlog";
}

export function AppShell({ children }: { children: ReactNode }) {
  const { user, loading, logout } = useSession();
  const pathname = usePathname();

  return (
    <AnimatedSidebarProvider className="h-svh overflow-hidden bg-background">
      <AnimatedSidebar
        ariaLabel="TeamBlog 导航"
        collapsible="icon"
        className="min-h-0"
        panelClassName="h-full border-foreground/10"
      >
        <AnimatedSidebarHeader className="p-3 pb-2">
          <div className="flex min-h-11 items-center gap-2 overflow-hidden px-2">
            <Link href="/" className="flex min-w-0 flex-1 items-center gap-2">
              <span className="grid size-7 shrink-0 place-items-center rounded-lg bg-foreground text-xs font-semibold text-background">
                TB
              </span>
              <span className="truncate text-sm font-semibold group-data-[state=collapsed]/sidebar:hidden">
                TeamBlog
              </span>
            </Link>
            <AnimatedSidebarClose className="ml-auto text-muted-foreground hover:bg-muted md:hidden">
              <PanelLeft aria-hidden className="size-4" />
            </AnimatedSidebarClose>
          </div>
        </AnimatedSidebarHeader>

        <AnimatedSidebarContent className="px-2 pt-1">
          <AnimatedSidebarGroup>
            <AnimatedSidebarGroupLabel>浏览</AnimatedSidebarGroupLabel>
            <AnimatedSidebarGroupContent>
              <AnimatedSidebarMenu>
                {MAIN_NAV.map((item) => (
                  <AnimatedSidebarMenuItem key={item.href}>
                    <AnimatedSidebarMenuButton
                      href={item.href}
                      icon={item.icon}
                      isActive={isActive(pathname, item.href)}
                    >
                      {item.label}
                    </AnimatedSidebarMenuButton>
                  </AnimatedSidebarMenuItem>
                ))}
              </AnimatedSidebarMenu>
            </AnimatedSidebarGroupContent>
          </AnimatedSidebarGroup>

          {user ? (
            <AnimatedSidebarGroup className="pt-1">
              <AnimatedSidebarGroupLabel>发布</AnimatedSidebarGroupLabel>
              <AnimatedSidebarGroupContent>
                <AnimatedSidebarMenu>
                  {MEMBER_NAV.map((item) => (
                    <AnimatedSidebarMenuItem key={item.href}>
                      <AnimatedSidebarMenuButton
                        href={item.href}
                        icon={item.icon}
                        isActive={isActive(pathname, item.href)}
                      >
                        {item.label}
                      </AnimatedSidebarMenuButton>
                    </AnimatedSidebarMenuItem>
                  ))}
                </AnimatedSidebarMenu>
              </AnimatedSidebarGroupContent>
            </AnimatedSidebarGroup>
          ) : null}

          {user?.role === "ADMIN" ? (
            <AnimatedSidebarGroup className="pt-1">
              <AnimatedSidebarGroupLabel>管理</AnimatedSidebarGroupLabel>
              <AnimatedSidebarGroupContent>
                <AnimatedSidebarMenu>
                  {ADMIN_NAV.map((item) => (
                    <AnimatedSidebarMenuItem key={item.href}>
                      <AnimatedSidebarMenuButton
                        href={item.href}
                        icon={item.icon}
                        isActive={isActive(pathname, item.href)}
                      >
                        {item.label}
                      </AnimatedSidebarMenuButton>
                    </AnimatedSidebarMenuItem>
                  ))}
                </AnimatedSidebarMenu>
              </AnimatedSidebarGroupContent>
            </AnimatedSidebarGroup>
          ) : null}
        </AnimatedSidebarContent>

        <AnimatedSidebarFooter className="gap-2 border-none p-3">
          {loading ? (
            <p className="px-2 text-xs text-muted-foreground">…</p>
          ) : user ? (
            <div className="flex items-center gap-2">
              <Link
                href={`/u/${user.username}`}
                className="flex min-w-0 flex-1 items-center gap-2 rounded-xl p-1 transition-colors hover:bg-muted"
              >
                <Avatar className="size-8 shrink-0">
                  <AvatarFallback>
                    {(user.displayName ?? user.username).slice(0, 2).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <span className="min-w-0 flex-1 group-data-[state=collapsed]/sidebar:hidden">
                  <span className="block truncate text-sm font-medium">
                    {user.displayName ?? user.username}
                  </span>
                  <span className="block truncate text-xs text-muted-foreground">
                    {user.role === "ADMIN" ? "管理员" : "成员"}
                  </span>
                </span>
              </Link>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => void logout()}
                aria-label="退出登录"
                className="shrink-0 group-data-[state=collapsed]/sidebar:hidden"
              >
                <LogOut className="size-4" aria-hidden />
              </Button>
            </div>
          ) : (
            <Link
              href="/login"
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
            >
              <LogIn className="size-4" aria-hidden /> 登录
            </Link>
          )}
        </AnimatedSidebarFooter>

        <AnimatedSidebarRail />
      </AnimatedSidebar>

      <AnimatedSidebarInset className="min-h-0 bg-background">
        <header className="flex h-16 shrink-0 items-center gap-3 border-border border-b px-4">
          <AnimatedSidebarTrigger className="text-muted-foreground transition-colors hover:bg-muted hover:text-foreground">
            <PanelLeft aria-hidden className="size-4" />
          </AnimatedSidebarTrigger>
          <div className="h-5 w-px bg-border" />
          <p className="truncate text-sm font-medium">{currentTitle(pathname)}</p>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto">
          <div className="mx-auto w-full max-w-6xl px-6 py-8">{children}</div>
        </div>
      </AnimatedSidebarInset>
    </AnimatedSidebarProvider>
  );
}
