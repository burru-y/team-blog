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
 * 开发者与分工。
 *
 * 按前后端 / 数据 / AI 分列，而不是笼统写「一起做的」——
 * 分工本身是信息，读的人能判断该找谁问什么。
 * 名字用站内用户名，点进去就是个人主页。
 */
const DEVELOPERS: { role: string; name: string; username: string; note?: string }[] = [
  { role: "前端", name: "廖呈睿", username: "qsburru" },
  { role: "后端", name: "廖呈睿", username: "qsburru" },
  { role: "数据", name: "廖呈睿", username: "qsburru" },
  { role: "AI", name: "廖呈睿", username: "qsburru" },
  { role: "网站建设", name: "祝宇航", username: "wayne", note: "协作" },
];

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
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span>开发者：</span>
            {/* 按角色列出：前端 / 后端 / 数据 / AI 分别是谁 */}
            <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
              {DEVELOPERS.map((dev, index) => (
                <span key={`${dev.role}-${dev.username}`} className="inline-flex items-center gap-1">
                  {index > 0 ? <span className="text-muted-foreground/40">·</span> : null}
                  <span className="text-muted-foreground">{dev.role}</span>
                  <Link
                    href={`/u/${dev.username}`}
                    className="text-foreground underline underline-offset-4 hover:text-brand"
                  >
                    {dev.name}
                  </Link>
                </span>
              ))}
            </span>
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
            <span>欲晓 · 团队技术博客</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
