import Link from "next/link";
import { BookOpen, Download, FileText, Package } from "lucide-react";

import { PageHeader } from "@/components/page-header";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export const metadata = {
  title: "帮助 · 欲晓",
  description: "写笔记的语法说明、本地编辑工具与下载。",
};

/**
 * 帮助首页。
 *
 * Markdown 语法只是这里的一栏——以后要加「怎么发成果」「怎么打卡」，
 * 直接往 TOPICS 里加一条即可，不用改结构。
 */
type Topic = {
  href: string;
  title: string;
  description: string;
  icon: React.ReactNode;
  /** 站内页面还是下载文件 */
  kind: "page" | "download";
  meta?: string;
};

const TOPICS: Topic[] = [
  {
    href: "/help/markdown",
    title: "Markdown 语法速查",
    description: "写学习笔记用的格式：标题、列表、代码块、表格，以及本站上传图片与代码文件的用法。",
    icon: <BookOpen className="size-4" />,
    kind: "page",
    meta: "约 3 分钟",
  },
  {
    href: "https://yu-xiao-media.oss-cn-hangzhou.aliyuncs.com/help/typora-guide.pdf",
    title: "Typora 安装与配置（PDF）",
    description: "本地写 Markdown 的完整配置说明：图片存相对路径、语法偏好、导出 PDF。",
    icon: <FileText className="size-4" />,
    kind: "download",
    meta: "PDF · 0.5 MB",
  },
  {
    href: "https://yu-xiao-media.oss-cn-hangzhou.aliyuncs.com/help/Typora-1.8.10.zip",
    title: "Typora 1.8.10 安装包",
    description: "Windows 版，解压后按 PDF 里的步骤安装。若杀毒软件报毒，是破解补丁导致的误报。",
    icon: <Download className="size-4" />,
    kind: "download",
    meta: "ZIP · 81 MB",
  },
  {
    href: "https://yu-xiao-media.oss-cn-hangzhou.aliyuncs.com/help/typora-plugin-1.11.34.zip",
    title: "Typora 增强插件",
    description: "可选。支持目录大纲、图片自动上传、表格编辑等，按压缩包里的说明安装。",
    icon: <Package className="size-4" />,
    kind: "download",
    meta: "ZIP · 6 MB",
  },
];

export default function HelpPage() {
  return (
    <div className="space-y-8">
      <PageHeader
        size="large"
        title="帮助"
        description="写笔记、用工具、发内容时遇到的问题。"
      />

      <div className="grid gap-4 sm:grid-cols-2">
        {TOPICS.map((topic) => (
          <Card key={topic.href} className="flex h-full flex-col">
            <CardHeader className="flex-row items-center gap-2 space-y-0">
              <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
                {topic.icon}
              </span>
              <CardTitle className="min-w-0 flex-1 text-base">{topic.title}</CardTitle>
              {topic.kind === "download" ? (
                <Badge variant="outline" className="shrink-0 text-xs">
                  下载
                </Badge>
              ) : null}
            </CardHeader>

            <CardContent className="flex flex-1 flex-col gap-3">
              <p className="flex-1 text-sm leading-relaxed text-muted-foreground">
                {topic.description}
              </p>
              <div className="flex items-center justify-between gap-2">
                {topic.meta ? (
                  <span className="text-xs text-muted-foreground">{topic.meta}</span>
                ) : (
                  <span />
                )}
                {topic.kind === "download" ? (
                  <a
                    href={topic.href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-sm text-brand hover:underline"
                  >
                    下载 ↓
                  </a>
                ) : (
                  <Link href={topic.href} className="text-sm text-brand hover:underline">
                    查看 →
                  </Link>
                )}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="rounded-xl border border-dashed px-5 py-4 text-sm text-muted-foreground">
        <p className="font-medium text-foreground">还没解决？</p>
        <p className="mt-1">
          在
          <Link href="/feedback" className="mx-1 text-brand hover:underline">
            留言反馈
          </Link>
          里写下遇到的问题，管理员会看到。
        </p>
      </div>
    </div>
  );
}
