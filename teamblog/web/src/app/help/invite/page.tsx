import Link from "next/link";

import { Markdown } from "@/components/markdown";
import { PageHeader } from "@/components/page-header";
import { buttonVariants } from "@/components/ui/button";

export const metadata = { title: "邀请同学加入 · 欲晓" };

const GUIDE = [
  "## 为什么不是开放注册",
  "",
  "这是班级内部的小站，注册需要邀请链接。原因有两个：",
  "",
  "1. 防止陌生人和爬虫进来灌水",
  "2. 每个成员都认识管理者，出问题能找到人",
  "",
  "## 怎么邀请一位同学",
  "",
  "1. 管理员进「后台 → 邀约」",
  "2. 生成邀请链接（可以指定角色：成员 / 管理员）",
  "3. 把链接发给同学",
  "4. 对方打开链接，设用户名和密码即完成注册",
  "",
  "## 链接的规则",
  "",
  "| 项 | 规则 |",
  "|---|---|",
  "| 有效期 | 7 天 |",
  "| 使用次数 | 一次，注册后失效 |",
  "| 撤销 | 管理员可在后台随时撤销未使用的链接 |",
  "| 频率 | 同一管理员每小时最多生成 20 条 |",
  "",
  "## 常见情况",
  "",
  "**链接打不开**：可能已过期或被撤销。让管理员重新生成一条。",
  "",
  "**提示链接已使用**：一条链接只能注册一个账号，需要新链接。",
  "",
  "**同学注册后进不来**：确认对方用的是完整链接（包含末尾的 token 部分），而不是只复制了域名。",
  "",
  "## 想移除某个成员",
  "",
  "目前不提供删除账号的功能。如果有人不该在这里，管理员可以：",
  "",
  "- 改掉对方密码（后台可重置）",
  "- 把角色降为成员",
  "- 撤销所有未使用的邀请链接",
].join("\n");

export default function InviteHelpPage() {
  return (
    <div className="space-y-8">
      <PageHeader
        size="large"
        title="邀请同学加入"
        description="注册需要邀请链接，由管理员生成。"
        actions={
          <Link href="/admin" className={buttonVariants({ size: "sm", variant: "outline" })}>
            后台邀约
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
