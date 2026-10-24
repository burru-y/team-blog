/**
 * 版本里程碑数据。
 *
 * 放这里而不是只写在 /timeline 页面里：首页也要展示精简版，
 * 两处共用一份数据，避免改一处忘一处。
 *
 * 日期取真实提交时间。新增里程碑时同步更新 README 的进度表。
 */
export type MilestoneStatus = "done" | "doing" | "planned";

export type Milestone = {
  date: string;
  title: string;
  status: MilestoneStatus;
  detail: string;
  items: string[];
};

export const STATUS_LABEL: Record<MilestoneStatus, string> = {
  done: "已完成",
  doing: "进行中",
  planned: "计划中",
};

export const MILESTONES: Milestone[] = [
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

/** 首页只展示最近的若干个里程碑，完整列表去 /timeline。 */
export function recentMilestones(count = 4): Milestone[] {
  const done = MILESTONES.filter((item) => item.status !== "planned");
  const planned = MILESTONES.filter((item) => item.status === "planned");
  return [...done.slice(-count), ...planned];
}

/**
 * 版本号列表：首页只显示"现在处于哪个版本"。
 *
 * 与里程碑分开：里程碑是开发过程记录（十几个），版本是面向使用者的阶段划分（现在只有一个）。
 * 两件事混在一起，首页就会被十几条过程记录撑满。
 */
export type Version = {
  version: string;
  name: string;
  status: MilestoneStatus;
  date: string;
  note: string;
};

export const VERSIONS: Version[] = [
  {
    version: "v1.0",
    name: "一期内测",
    status: "doing",
    date: "2026-10-24",
    note: "功能验证阶段：认证、发布、打卡积分、AI 问答、后台均可使用。",
  },
];
