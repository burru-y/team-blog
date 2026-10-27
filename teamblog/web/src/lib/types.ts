import type { components } from "./api-schema";

/**
 * 接口类型一律从 OpenAPI 生成（T-06）。这里只做别名，不手写字段。
 */
export type TokenResponse = Required<components["schemas"]["TokenResponse"]>;

export type UserResponse = Required<components["schemas"]["UserResponse"]>;

export type InviteInfo = Required<NonNullable<components["schemas"]["ApiInviteInfo"]["data"]>>;

export type InviteCreated = Required<NonNullable<components["schemas"]["ApiInviteCreated"]["data"]>>;

/** 邀请列表的一行（不含链接本身：库里只存哈希）。 */
export type InviteSummary = Required<components["schemas"]["InviteSummary"]>;

/** 成员列表里的一张卡片（列表不返回邮箱、等级等详情字段）。 */
export type UserCard = Required<components["schemas"]["UserCardResponse"]>;

/** 游标分页信封（A-08）。 */
export type UserPage = {
  items: UserCard[];
  nextCursor: string | null;
};

export type ProjectCard = Required<components["schemas"]["ProjectCardResponse"]>;

export type ProjectPage = {
  items: ProjectCard[];
  nextCursor: string | null;
};

export type ProjectResponse = Required<components["schemas"]["ProjectResponse"]>;

export type LearningLogResponse = Required<components["schemas"]["LearningLogResponse"]>;

export type LearningLogCreated = {
  log: LearningLogResponse;
  counted: boolean;
  points: number;
  remainingToday: number;
};

/** 发布成果的表单体（类型只允许这三种）。 */
export type ProjectCreateBody = {
  type: "PROJECT" | "CERTIFICATE" | "AWARD";
  title: string;
  description: string;
  githubUrl?: string | null;
  contentMd?: string | null;
  imageUrl?: string | null;
};

export type LearningLogBody = {
  type: "NOTE" | "CODE";
  title: string;
  contentMd: string;
  summaryMd?: string | null;
  /** 逗号分隔的标签，最多 5 个 */
  tags?: string | null;
};

export type RegisterRequest = components["schemas"]["RegisterRequest"];

export type UploadedImage = Required<components["schemas"]["UploadedImage"]>;

export type CodeBlock = Required<components["schemas"]["CodeBlock"]>;

export type CommentResponse = {
  id: string;
  username: string;
  content: string | null;
  deleted: boolean;
  createdAt: string;
  replies: CommentResponse[];
};

export type AdminProject = {
  id: string;
  type: string;
  title: string;
  ownerUsername: string;
  status: "ACTIVE" | "REMOVED";
  createdAt: string;
  removedAt: string | null;
};

export type AdminFeedback = {
  id: string;
  name: string;
  email: string | null;
  content: string;
  status: "NEW" | "READ" | "ARCHIVED";
  createdAt: string;
};

export type FeedbackBody = {
  name: string;
  email?: string | null;
  content: string;
  /** 蜜罐：正常用户永远为空 */
  website?: string;
};

export type CommentBody = {
  targetType: "PROJECT" | "LEARNING_LOG";
  targetId: string;
  parentId?: string | null;
  content: string;
};

export type CheckinResult = Required<components["schemas"]["CheckinResult"]>;

export type CheckinStatus = Required<components["schemas"]["CheckinStatus"]>;

/** 热力图：日期 → 数值；缺失的日期视为 0。 */
export type HeatmapResponse = {
  from: string;
  to: string;
  days: Record<string, number>;
};

/** 后端统一信封（A-03）。 */
export type Envelope<T> = {
  ok: boolean;
  data: T | null;
  error: { code: string; message: string } | null;
};

/** 前端只需要区分这几种 code，展示文案从 messages 里取（T-09）。 */
export type ErrorCode =
  | "UNAUTHENTICATED"
  | "SESSION_REVOKED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "INVITE_INVALID"
  | "VALIDATION_FAILED"
  | "RULE_VIOLATED"
  | "CONFLICT"
  | "ALREADY_CHECKED_IN"
  | "RATE_LIMITED"
  | "UPSTREAM_FAILED"
  | "INTERNAL"
  | "NETWORK";

/** 帮助资料（存在 OSS 上的文件）。 */
export type HelpFile = {
  name: string;
  url: string;
  size: number;
  updatedAt: string;
};

/** 公告 / 更新日志。 */
export type Announcement = {
  id: string;
  kind: "NOTICE" | "CHANGELOG";
  version: string | null;
  title: string;
  bodyMd: string;
  pinned: boolean;
  publishedAt: string;
};
