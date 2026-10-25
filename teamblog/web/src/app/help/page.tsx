import Link from "next/link";
import { BookOpen, CircleHelp, Download, FileText, ListChecks, Send, UserPlus } from "lucide-react";

import { PageHeader } from "@/components/page-header";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { serverApi } from "@/lib/server-api";
import type { HelpFile } from "@/lib/types";

export const metadata = {
  title: "帮助 · 欲晓",
  description: "写笔记、发成果、打卡积分、邀请同学，以及可下载的工具。",
};

// 下载区来自接口，因此禁止静态预渲染
export const dynamic = "force-dynamic";

/**
 * 帮助中心。
 *
 * 上半部分是固定的说明页，下半部分是管理员上传的资料（从 OSS 列出来）。
 * 两件事分开：说明文字改代码，资料文件改上传，各管各的。
 */
const TOPICS = [
  {
    href: "/help/markdown",
    title: "Markdown 语法速查",
    description: "标题、列表、代码块、表格怎么写；本站上传图片与代码文件怎么用。",
    icon: <BookOpen className="size-4" />,
    meta: "约 3 分钟",
  },
  {
    href: "/help/publish",
    title: "怎么发布成果",
    description: "项目、证书、奖项怎么填；正文写什么才有人看。",
    icon: <Send className="size-4" />,
    meta: "约 2 分钟",
  },
  {
    href: "/help/points",
    title: "打卡与积分",
    description: "积分怎么来、打卡的时区边界、连续中断会怎样。",
    icon: <ListChecks className="size-4" />,
    meta: "约 2 分钟",
  },
  {
    href: "/help/invite",
    title: "邀请同学加入",
    description: "邀请链接怎么生成、有效期多久、打不开怎么办。",
    icon: <UserPlus className="size-4" />,
    meta: "约 2 分钟",
  },
  {
    href: "/help/faq",
    title: "常见问题",
    description: "登录、图片、AI 问答、手机端排版的常见故障排查。",
    icon: <CircleHelp className="size-4" />,
    meta: "约 3 分钟",
  },
];

function formatSize(bytes: number) {
  if (bytes >= 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

export default async function HelpPage() {
  // 读取失败不该让整页挂掉：说明部分仍然可用
  const files = await serverApi.listHelpFiles().catch(() => [] as HelpFile[]);

  return (
    <div className="space-y-10">
      <PageHeader
        size="large"
        title="帮助"
        description="写笔记、发内容、用工具时遇到的问题。"
      />

      <section className="space-y-4">
        <h2 className="text-lg font-semibold tracking-tight">使用说明</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          {TOPICS.map((topic) => (
            <Card key={topic.href} className="flex h-full flex-col">
              <CardHeader className="flex-row items-center gap-2 space-y-0">
                <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
                  {topic.icon}
                </span>
                <CardTitle className="min-w-0 flex-1 text-base">{topic.title}</CardTitle>
              </CardHeader>
              <CardContent className="flex flex-1 flex-col gap-3">
                <p className="flex-1 text-sm leading-relaxed text-muted-foreground">
                  {topic.description}
                </p>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs text-muted-foreground">{topic.meta}</span>
                  <Link href={topic.href} className="text-sm text-brand hover:underline">
                    查看 →
                  </Link>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </section>

      <section className="space-y-4 border-t pt-8">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-lg font-semibold tracking-tight">资料下载</h2>
          <span className="text-xs text-muted-foreground">
            {files.length > 0 ? `${files.length} 个文件` : "暂无文件"}
          </span>
        </div>

        {files.length === 0 ? (
          <div className="rounded-xl border border-dashed px-5 py-8 text-center text-sm text-muted-foreground">
            管理员还没有上传资料。
          </div>
        ) : (
          <ul className="divide-y rounded-xl border">
            {files.map((file) => (
              <li key={file.name} className="flex flex-wrap items-center gap-3 px-4 py-3">
                <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
                  {file.name.endsWith(".pdf") ? (
                    <FileText className="size-4" />
                  ) : (
                    <Download className="size-4" />
                  )}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{file.name}</p>
                  <p className="text-xs text-muted-foreground">{formatSize(file.size)}</p>
                </div>
                <a
                  href={file.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="shrink-0 text-sm text-brand hover:underline"
                >
                  下载 ↓
                </a>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="rounded-xl border border-dashed px-5 py-4 text-sm text-muted-foreground">
        <p className="font-medium text-foreground">还没解决？</p>
        <p className="mt-1">
          在
          <Link href="/feedback" className="mx-1 text-brand hover:underline">
            留言反馈
          </Link>
          里写下遇到的问题，管理员会看到。
        </p>
        <Badge variant="secondary" className="mt-3 text-xs">
          内测期，功能会持续补充
        </Badge>
      </div>
    </div>
  );
}
