import Link from "next/link";

import { Markdown } from "@/components/markdown";
import { PageHeader } from "@/components/page-header";
import { buttonVariants } from "@/components/ui/button";

export const metadata = { title: "怎么发布成果 · 欲晓" };

const GUIDE = [
  "## 发一篇成果要准备什么",
  "",
  "| 项 | 必填 | 说明 |",
  "|---|---|---|",
  "| 类型 | 是 | 项目 / 证书 / 奖项，决定列表里的分类标签 |",
  "| 标题 | 是 | 一句话说清楚做了什么，不要用「作业1」这种 |",
  "| 简介 | 是 | 两三句话，列表页显示的摘要 |",
  "| 正文 | 否 | Markdown，写背景、做法、结果 |",
  "| 配图 | 否 | 截图或成品图，存到 OSS |",
  "| GitHub | 否 | 有仓库就填，访问者可以直接看代码 |",
  "",
  "## 发布的三个步骤",
  "",
  "1. 侧栏点「发布成果」",
  "2. 选类型，填标题和简介",
  "3. 正文可以先用「代码文件」或「图片」上传，再补文字",
  "",
  "发布后立刻出现在「成果」列表里，访客都能看到。",
  "",
  "## 正文怎么写才有人看",
  "",
  "- **先写为什么做**：解决什么问题，比技术栈更重要",
  "- **写清楚卡在哪**：踩过的坑是最有价值的部分",
  "- **给结果**：跑通了什么、数据是多少、和原来比快了多少",
  "- **代码贴关键片段**：整份代码放 GitHub，正文只贴核心几十行",
  "",
  "## 之后能改吗",
  "",
  "作者本人和管理员可以下架成果。下架后列表里不再显示，但链接仍然有效（方便你私发给别人）。",
  "",
  "> 想修改内容目前需要下架后重新发布。改标题这类小改动，找管理员在后台处理更快。",
].join("\n");

export default function PublishHelpPage() {
  return (
    <div className="space-y-8">
      <PageHeader
        size="large"
        title="怎么发布成果"
        description="项目、证书、奖项都走同一个入口。"
        actions={
          <Link href="/publish" className={buttonVariants({ size: "sm" })}>
            去发布
          </Link>
        }
        meta={<span>帮助 · 约 2 分钟</span>}
      />
      <Markdown>{GUIDE}</Markdown>
      <div className="border-t pt-6">
        <Link href="/help" className={buttonVariants({ variant: "ghost" })}>
          ← 返回帮助
        </Link>
      </div>
    </div>
  );
}
