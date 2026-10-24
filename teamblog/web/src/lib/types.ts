import type { components } from "./api-schema";

/**
 * 接口类型一律从 OpenAPI 生成（T-06）。
 *
 * 这里只做"起别名"，不手写字段——手写 interface 等于在本地复制一份契约，
 * 后端改了字段前端不会报错，只在运行时炸。
 *
 * 为什么要套一层 `Required`：OpenAPI 里的响应 schema 没有写 `required` 列表，
 * 生成器就把所有字段标成可选。但后端实际总是返回这些字段（见 ApiResponse 的实现），
 * 不套 Required 的话每个使用点都要写 `?.` 和空值兜底，噪音比价值大。
 */
export type TokenResponse = Required<components["schemas"]["TokenResponse"]>;

export type UserResponse = Required<components["schemas"]["UserResponse"]>;

export type InviteInfo = Required<NonNullable<components["schemas"]["ApiInviteInfo"]["data"]>>;

export type InviteCreated = Required<NonNullable<components["schemas"]["ApiInviteCreated"]["data"]>>;

/** 成员列表里的一张卡片（列表不返回邮箱等详情字段）。 */
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
};

export type UploadedImage = Required<components["schemas"]["UploadedImage"]>;

export type CodeBlock = Required<components["schemas"]["CodeBlock"]>;

export type LoginRequest = components["schemas"]["LoginRequest"];

export type RegisterRequest = components["schemas"]["RegisterRequest"];

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
  | "RATE_LIMITED"
  | "UPSTREAM_FAILED"
  | "INTERNAL";
