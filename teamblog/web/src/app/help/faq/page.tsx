import Link from "next/link";

import { Markdown } from "@/components/markdown";
import { PageHeader } from "@/components/page-header";
import { buttonVariants } from "@/components/ui/button";

export const metadata = { title: "常见问题 · 欲晓" };

const GUIDE = [
  "## 登录",
  "",
  "**登录成功后刷新又变访客**",
  "",
  "浏览器没保存 Cookie。常见原因：用了无痕模式、装了拦截 Cookie 的插件、或者站点的 HTTPS 与 Cookie 安全属性不匹配。换普通窗口再试。",
  "",
  "**忘记密码**",
  "",
  "目前没有自助找回。找管理员在后台重置，重置后用自己的新密码登录。",
  "",
  "**用户名能改吗**",
  "",
  "个人主页可以改昵称（显示名）；用户名是登录用的，不建议频繁改。",
  "",
  "## 内容",
  "",
  "**上传的图片显示不出来**",
  "",
  "图片存在对象存储上，链接形如 `https://yu-xiao-media.oss-cn-hangzhou.aliyuncs.com/uploads/...`。",
  "如果打不开，先检查这条链接本身能不能访问；能访问说明是网络问题，不能访问请联系管理员。",
  "",
  "**笔记里的图表没渲染**",
  "",
  "本站不支持 Mermaid 和数学公式，会显示成纯文本。需要图表时截图上传。",
  "",
  "**能删除自己发的内容吗**",
  "",
  "成果可以下架（作者本人或管理员）；学习笔记目前不支持删除，发布前检查一遍。",
  "",
  "## AI 问答",
  "",
  "**提示「服务暂时不可用」**",
  "",
  "上游模型服务异常，通常几分钟后恢复。连续出现请联系管理员。",
  "",
  "**提示「今天的提问次数已用完」**",
  "",
  "访客每天 8 次（按 IP）。登录后不限次数。",
  "",
  "**AI 会编造内容吗**",
  "",
  "会。它只读项目正文或笔记正文，资料里没有的东西可能被「猜」。关键信息请自己核对。",
  "",
  "## 其它",
  "",
  "**手机上排版乱**",
  "",
  "移动端适配还在完善中。遇到明显错位，欢迎在「留言」里写下页面地址和现象。",
  "",
  "**想加新功能**",
  "",
  "写在留言里，管理员会看。",
].join("\n");

export default function FaqHelpPage() {
  return (
    <div className="space-y-8">
      <PageHeader
        size="large"
        title="常见问题"
        description="遇到问题先看这里，没解决就去留言。"
        meta={<span>帮助 · 约 3 分钟</span>}
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
