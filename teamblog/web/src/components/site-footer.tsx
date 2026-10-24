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

/** 开发者：想换显示名，改这一处即可（GitHub 账号来自仓库 origin）。 */
const DEVELOPERS = [{ name: "burru-y", github: "https://github.com/burru-y" }];

export function SiteFooter() {
  return (
    <footer className="mt-16 border-t bg-muted/30">
      <div className="mx-auto w-full max-w-5xl space-y-6 px-4 py-8 text-sm">
        <div className="flex flex-wrap items-center gap-2">
          <Badge className="border-amber-500/40 bg-amber-500/15 text-amber-700 dark:text-amber-400">
            内测中
          </Badge>
          <span className="text-muted-foreground">
            当前为内测版本，功能与数据可能随时调整，欢迎反馈问题。
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
            {DEVELOPERS.map((dev) => (
              <a
                key={dev.name}
                href={dev.github}
                target="_blank"
                rel="noreferrer"
                className="text-foreground underline underline-offset-4"
              >
                @{dev.name}
              </a>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Link href="/timeline" className="underline underline-offset-4 hover:text-foreground">
              版本时间线
            </Link>
            <a
              href="https://github.com/burru-y/team-blog"
              target="_blank"
              rel="noreferrer"
              className="underline underline-offset-4 hover:text-foreground"
            >
              源码
            </a>
            <span>TeamBlog · 团队技术博客</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
