import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";

/**
 * 版本时间线（静态页）。
 *
 * 数据写死在代码里而不是查库：它是"项目演进记录"，不是业务数据，
 * 放数据库会多一张没人维护的表。里程碑变了就改这个数组，和 README 一起提交。
 *
 * 日期取真实提交时间，不编造。未完成的事项放在最后，标注为"计划中"。
 */
type Status = "done" | "doing" | "planned";

const STATUS_LABEL: Record<Status, string> = {
  done: "已完成",
  doing: "进行中",
  planned: "计划中",
};

const MILESTONES: {
  date: string;
  title: string;
  status: Status;
  detail: string;
  items: string[];
}[] = [
  {
    date: "2026-10-23",
    title: "项目初始化",
    status: "done",
    detail: "仓库结构、需求文档与实施方案定稿。",
    items: ["目录规划", "实施方案四份文档", "Spring Boot 骨架"],
  },
  {
    date: "2026-10-24 13:07",
    title: "认证闭环",
    status: "done",
    detail: "从注册到登出的完整链路，含令牌轮换与重放检测。",
    items: ["用户实体与 BCrypt", "JWT access token", "refresh 轮换与重放撤销", "登录限流", "邀请注册"],
  },
  {
    date: "2026-10-24 13:47",
    title: "前端骨架与权限矩阵",
    status: "done",
    detail: "Next.js 页面骨架，以及覆盖全部端点的权限测试。",
    items: ["登录/邀请页面", "端点扫描与覆盖守卫", "三身份状态码断言"],
  },
  {
    date: "2026-10-24 14:34",
    title: "内容发布",
    status: "done",
    detail: "成果与学习记录的发布链路，含文件上传与 Markdown 渲染。",
    items: ["成果 CRUD 与归属校验", "学习记录与每日计分", "图片与代码上传", "XSS 清洗"],
  },
  {
    date: "2026-10-24 15:00",
    title: "打卡与积分",
    status: "done",
    detail: "连续打卡奖励、积分账本与对账任务。",
    items: ["连续天数计算", "7/14/30 天奖励", "积分事件账本", "对账任务"],
  },
  {
    date: "2026-10-24 15:20",
    title: "互动与后台",
    status: "done",
    detail: "两级评论、匿名留言与管理员后台。",
    items: ["两级评论与软删除", "蜜罐与 IP 限流", "管理员四个 tab"],
  },
  {
    date: "2026-10-24 16:20",
    title: "容器化部署",
    status: "done",
    detail: "六个容器的编排与验收脚本。",
    items: ["Compose 编排", "Nginx 路由", "14 项部署验收"],
  },
  {
    date: "2026-10-24 17:08",
    title: "AI 问答",
    status: "done",
    detail: "独立的 AI 服务，SSE 流式回答与提示词评测。",
    items: ["FastAPI 服务", "SSE 流式输出", "摘要链路", "22 条提示词评测"],
  },
  {
    date: "2026-10-24 19:42",
    title: "上线硬化",
    status: "done",
    detail: "首启管理员、HTTPS、每日备份与密码重置。",
    items: ["空库自动建管理员", "HTTPS 与 Secure Cookie", "备份排程与恢复演练", "密码重置"],
  },
  {
    date: "2026-10-24 22:12",
    title: "内测准备",
    status: "done",
    detail: "对外可见的状态说明、数据管理与一键启动。",
    items: ["全站公告", "个人主页编辑", "邀请可撤销", "打卡附带学习内容", "版本时间线", "一键启动脚本"],
  },
  {
    date: "待定",
    title: "正式上线",
    status: "planned",
    detail: "服务器部署与域名备案。",
    items: ["域名与 ICP 备案", "监控与告警", "异地备份"],
  },
];

export const metadata = {
  title: "版本时间线 · TeamBlog",
  description: "项目各阶段的完成情况与后续计划",
};

export default function TimelinePage() {
  return (
    <div className="space-y-8">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">版本时间线</h1>
        <p className="text-sm text-muted-foreground">
          按开发顺序记录各阶段的完成情况。日期取真实提交时间，未完成为计划。
        </p>
      </header>

      <ol className="relative space-y-6 border-l pl-6">
        {MILESTONES.map((item) => (
          <li key={item.date + item.title} className="relative">
            <span
              className={[
                "absolute -left-[31px] top-1.5 size-3 rounded-full border-2 border-background",
                item.status === "done"
                  ? "bg-emerald-500"
                  : item.status === "doing"
                    ? "bg-amber-500"
                    : "bg-muted-foreground/40",
              ].join(" ")}
              aria-hidden
            />
            <div className="space-y-1.5">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="font-medium">{item.title}</h2>
                <Badge variant={item.status === "done" ? "secondary" : "outline"}>
                  {STATUS_LABEL[item.status]}
                </Badge>
                <span className="text-xs text-muted-foreground">{item.date}</span>
              </div>
              <p className="text-sm text-muted-foreground">{item.detail}</p>
              <div className="flex flex-wrap gap-1.5 pt-1">
                {item.items.map((entry) => (
                  <span
                    key={entry}
                    className="rounded border px-1.5 py-0.5 text-xs text-muted-foreground"
                  >
                    {entry}
                  </span>
                ))}
              </div>
            </div>
          </li>
        ))}
      </ol>

      <div>
        <Link href="/" className={buttonVariants({ variant: "ghost" })}>
          ← 回到首页
        </Link>
      </div>
    </div>
  );
}
