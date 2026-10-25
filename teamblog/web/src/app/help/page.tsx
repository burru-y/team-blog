import Link from "next/link";
import { BookOpen, CircleHelp, Download, ListChecks, Send, UserPlus } from "lucide-react";

import { PageHeader } from "@/components/page-header";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export const metadata = {
  title: "帮助 · 欲晓",
  description: "写笔记、发成果、打卡积分、邀请同学，以及可下载的工具。",
};

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
    href: "/help/markdown#downloads",
    title: "本地编辑工具（Typora）",
    description: "Typora 安装包、增强插件与配置说明的下载，含使用声明。",
    icon: <Download className="size-4" />,
    meta: "含下载",
  },
  {
    href: "/help/faq",
    title: "常见问题",
    description: "登录、图片、AI 问答、手机端排版的常见故障排查。",
    icon: <CircleHelp className="size-4" />,
    meta: "约 3 分钟",
  },
];

export default function HelpPage() {
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
