import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

/**
 * 首页壳。
 *
 * 现在只有骨架：成员、成果、热力图要等批次 2（真实数据）与批次 4（打卡）接入。
 * 先把"这是个团队站"的信息结构立住，比先做视觉更重要。
 */
export default function HomePage() {
  return (
    <div className="space-y-10">
      <section className="space-y-4">
        <Badge variant="outline">一期 · 内测</Badge>
        <h1 className="text-4xl font-semibold tracking-tight">团队技术博客</h1>
        <p className="max-w-2xl text-muted-foreground">
          记录团队的技术成果、学习内容与每天的学习打卡。成员可以发布项目与学习笔记，
          访客可以浏览公开内容并向 AI 提问。
        </p>
        <div className="flex gap-3">
          <Link href="/login" className={buttonVariants()}>
            成员登录
          </Link>
          <Link href="/login" className={buttonVariants({ variant: "outline" })}>
            凭邀请注册
          </Link>
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>成员</CardTitle>
            <CardDescription>团队介绍与个人主页</CardDescription>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">批次 2 接入真实数据</CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>成果</CardTitle>
            <CardDescription>项目、证书与奖项</CardDescription>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">批次 3 开放发布</CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>打卡</CardTitle>
            <CardDescription>连续天数、积分与热力图</CardDescription>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">批次 4 上线</CardContent>
        </Card>
      </section>
    </div>
  );
}
