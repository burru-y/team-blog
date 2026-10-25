import Link from "next/link";

import { Markdown } from "@/components/markdown";
import { PageHeader } from "@/components/page-header";
import { buttonVariants } from "@/components/ui/button";

export const metadata = { title: "打卡与积分 · 欲晓" };

const GUIDE = [
  "## 积分怎么来的",
  "",
  "| 行为 | 积分 | 限制 |",
  "|---|---|---|",
  "| 每天打卡 | +1 | 每天一次 |",
  "| 发学习笔记 | +10 | 每天前 3 篇计分 |",
  "| 连续打卡 | 额外奖励 | 连续 7 天起 |",
  "",
  "每天前 3 篇笔记计分，之后的照样保存，只是不再加分——避免为了刷分复制粘贴。",
  "",
  "## 打卡的判定边界",
  "",
  "打卡按**自然日**算，时区是北京时间（Asia/Shanghai）。",
  "",
  "凌晨 0 点前后是分界：23:59 打卡算今天，00:01 算明天。这点和手机上的日期一致。",
  "",
  "## 打卡和写笔记可以一起做",
  "",
  "首页打卡卡片里点「顺便记一笔」，会同时完成：",
  "",
  "1. 今日打卡 +1",
  "2. 把写的内容存成一篇学习笔记（+10，若在当天前 3 篇内）",
  "",
  "比先打卡再单独发笔记少一步。",
  "",
  "## 积分有什么用",
  "",
  "目前是**荣誉性质的排行**，个人主页显示总分。",
  "",
  "不兑换实物、不影响权限。它的作用是让人一眼看出谁在持续输出。",
  "",
  "## 中断了会怎样",
  "",
  "连续天数会归零，但**历史积分不会扣**。断一次不影响你已有的分数，只是重新开始累计连续天数。",
].join("\n");

export default function PointsHelpPage() {
  return (
    <div className="space-y-8">
      <PageHeader
        size="large"
        title="打卡与积分"
        description="规则简单，但有几个边界值得知道。"
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
