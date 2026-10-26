import Link from "next/link";

import { Badge } from "@/components/ui/badge";

/**
 * 页脚：内测标识 + 技术栈 + 开发者。
 *
 * 为什么放页脚而不是单独"关于"页：这三样是"看一眼就够"的信息，
 * 做成页面没人点，放页脚每页都能看到。
 *
 * 服务端组件：纯静态内容，不需要 JS。
 */

/** 技术栈：只写真的用了的，不凑数。分组是为了让人一眼看出前后端边界。 */
const STACK: { group: string; items: string[] }[] = [
  { group: "前端", items: ["Next.js 15", "React 19", "Tailwind CSS 4", "shadcn/ui"] },
  { group: "后端", items: ["Spring Boot 4", "Java 17", "Spring Security", "JPA"] },
  { group: "数据", items: ["PostgreSQL 16", "Flyway"] },
  { group: "AI", items: ["FastAPI", "DeepSeek", "SSE"] },
  { group: "部署", items: ["Docker Compose", "Nginx", "HTTPS"] },
];

/**
 * 开发者。
 *
 * 只列两个用户名，不写姓名也不按角色拆分：分工在个人主页里看得更清楚，
 * 页脚这一行只要回答「这个站是谁做的」。链接指向站内主页，不是外链。
 */
const DEVELOPERS = ["qsburru", "wayne"] as const;

export function SiteFooter() {
  return (
    <footer className="mt-16 border-t bg-muted/30">
      <div className="mx-auto w-full max-w-6xl space-y-6 px-6 py-10 text-sm">
        <div className="flex flex-wrap items-center gap-2">
          <Badge className="border-amber-500/40 bg-amber-500/15 text-amber-700 dark:text-amber-400">
            内测中
          </Badge>
          <span className="text-muted-foreground">
            一期内测：功能已上线，界面仍在打磨；暂用 IP 访问，域名备案后换 HTTPS。
          </span>
          <Link href="/feedback" className="text-foreground underline underline-offset-4">
            提交反馈
          </Link>
        </div>

        <div className="space-y-2">
          <div className="font-medium">技术栈</div>
          <div className="space-y-1.5">
            {STACK.map((row) => (
              <div key={row.group} className="flex flex-wrap items-center gap-1.5">
                <span className="w-10 shrink-0 text-xs text-muted-foreground">{row.group}</span>
                {row.items.map((item) => (
                  <Badge key={item} variant="outline" className="font-normal">
                    {item}
                  </Badge>
                ))}
              </div>
            ))}
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-4 text-xs text-muted-foreground">
          <div className="flex flex-wrap items-center gap-2">
            <span>开发者：</span>
            {DEVELOPERS.map((username) => (
              <Link
                key={username}
                href={`/u/${username}`}
                className="text-foreground underline underline-offset-4 hover:text-brand"
              >
                @{username}
              </Link>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Link href="/timeline" className="underline underline-offset-4 hover:text-foreground">
              版本时间线
            </Link>
            {/* 个人博客是独立静态站，挂在同域的 /blog 下 */}
            <a href="/blog/" className="underline underline-offset-4 hover:text-foreground">
              个人博客
            </a>
            <a
              href="https://github.com/burru-y/team-blog"
              target="_blank"
              rel="noreferrer"
              className="underline underline-offset-4 hover:text-foreground"
            >
              源码
            </a>
            <span>欲晓 · 团队技术博客</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
