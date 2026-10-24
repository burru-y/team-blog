import type {
  AdminFeedback,
  AdminProject,
  CheckinResult,
  InviteCreated,
  CommentBody,
  FeedbackBody,
  CommentResponse,
  CheckinStatus,
  CodeBlock,
  Envelope,
  ErrorCode,
  InviteInfo,
  LearningLogBody,
  LearningLogCreated,
  ProjectCreateBody,
  ProjectResponse,
  RegisterRequest,
  TokenResponse,
  UploadedImage,
  UserPage,
  UserResponse,
} from "./types";

/**
 * 全站唯一的 HTTP 入口（T-08：禁止散落 fetch）。
 *
 * 三件事在这里做掉，业务代码不用重复：
 * 1. 解信封、失败时抛 ApiError（带 code，前端按 code 分支）
 * 2. cache: "no-store" —— 漏了这个参数就可能把 A 的数据缓存给 B（T-03）
 * 3. 拼 Authorization 头
 */
export class ApiError extends Error {
  constructor(
    readonly code: ErrorCode | "NETWORK",
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  token?: string | null;
  /** 服务端组件用：Node 里没有"当前站点"，必须给绝对地址。 */
  base?: string;
};

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, token, base = "" } = options;

  let response: Response;
  try {
    response = await fetch(base + path, {
      method,
      cache: "no-store",
      headers: {
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError("NETWORK", "网络异常，请检查后端是否已启动", 0);
  }

  const envelope = (await response.json().catch(() => null)) as Envelope<T> | null;

  if (!response.ok || !envelope?.ok || envelope.data === null) {
    const code = (envelope?.error?.code as ErrorCode | undefined) ?? "INTERNAL";
    throw new ApiError(code, envelope?.error?.message ?? "请求失败", response.status);
  }

  return envelope.data;
}

export const api = {
  login(username: string, password: string): Promise<TokenResponse> {
    return request<TokenResponse>("/api/v1/auth/login", {
      method: "POST",
      body: { username, password },
    });
  },

  /** 用 httpOnly Cookie 换新的 access token（页面刷新后恢复会话靠它）。 */
  refresh(): Promise<TokenResponse> {
    return request<TokenResponse>("/api/v1/auth/refresh", { method: "POST" });
  },

  logout(): Promise<null> {
    return request<null>("/api/v1/auth/logout", { method: "POST" });
  },

  me(token: string): Promise<UserResponse> {
    return request<UserResponse>("/api/v1/me", { token });
  },

  inviteInfo(token: string): Promise<InviteInfo> {
    return request<InviteInfo>(`/api/v1/invites/${encodeURIComponent(token)}`);
  },

  acceptInvite(token: string, payload: RegisterRequest): Promise<UserResponse> {
    return request<UserResponse>(`/api/v1/invites/${encodeURIComponent(token)}/accept`, {
      method: "POST",
      body: payload,
    });
  },

  /** 成员列表（公开）。客户端走同源代理，服务端用 server-api。 */
  listMembers(cursor?: string, limit = 20): Promise<UserPage> {
    return request<UserPage>(`/api/v1/users?${pageQuery(cursor, limit)}`);
  },

  /** 公开资料（公开）。 */
  getUserProfile(username: string): Promise<UserResponse> {
    return request<UserResponse>(`/api/v1/users/${encodeURIComponent(username)}`);
  },

  createProject(body: ProjectCreateBody, token: string): Promise<ProjectResponse> {
    return request<ProjectResponse>("/api/v1/projects", { method: "POST", body, token });
  },

  removeProject(id: string, token: string): Promise<null> {
    return request<null>(`/api/v1/projects/${encodeURIComponent(id)}`, { method: "DELETE", token });
  },

  createLearningLog(body: LearningLogBody, token: string): Promise<LearningLogCreated> {
    return request<LearningLogCreated>("/api/v1/learning-logs", { method: "POST", body, token });
  },

  /** 图片上传：走 multipart，不能手动设 Content-Type（浏览器要自己带 boundary）。 */
  uploadImage(file: File, token: string): Promise<UploadedImage> {
    return requestForm<UploadedImage>("/api/v1/uploads/images", file, token);
  },

  /** 代码文件 → Markdown 代码块。 */
  uploadCode(file: File, token: string): Promise<CodeBlock> {
    return requestForm<CodeBlock>("/api/v1/uploads/code", file, token);
  },

  /** 今日打卡状态（按钮置灰用）。 */
  checkinStatus(token: string): Promise<CheckinStatus> {
    return request<CheckinStatus>("/api/v1/checkins/status", { token });
  },

  /** 留言：公开接口，不需要 token。网站字段是蜜罐，正常留空。 */
  submitFeedback(body: FeedbackBody): Promise<null> {
    return request<null>("/api/v1/feedback", { method: "POST", body });
  },

  listComments(targetType: "PROJECT" | "LEARNING_LOG", targetId: string): Promise<CommentResponse[]> {
    return request<CommentResponse[]>(
      `/api/v1/comments?targetType=${targetType}&targetId=${encodeURIComponent(targetId)}`,
    );
  },

  createComment(body: CommentBody, token: string): Promise<CommentResponse> {
    return request<CommentResponse>("/api/v1/comments", { method: "POST", body, token });
  },

  deleteComment(id: string, token: string): Promise<null> {
    return request<null>(`/api/v1/comments/${encodeURIComponent(id)}`, { method: "DELETE", token });
  },

  /** 打卡。今天已打过会抛 ALREADY_CHECKED_IN（409），属于正常情况。 */
  // ---------- 管理端 ----------

  adminUsers(token: string): Promise<UserResponse[]> {
    return request<UserResponse[]>("/api/v1/admin/users", { token });
  },

  changeRole(id: string, role: "ADMIN" | "MEMBER", token: string): Promise<UserResponse> {
    return request<UserResponse>(`/api/v1/admin/users/${encodeURIComponent(id)}/role`, {
      method: "PATCH",
      body: { role },
      token,
    });
  },

  createInvite(payload: { email?: string | null; role: "ADMIN" | "MEMBER" }, token: string): Promise<InviteCreated> {
    return request<InviteCreated>("/api/v1/admin/invites", { method: "POST", body: payload, token });
  },

  adminProjects(status: "ACTIVE" | "REMOVED" | undefined, token: string): Promise<AdminProject[]> {
    const query = status ? `?status=${status}` : "";
    return request<AdminProject[]>(`/api/v1/admin/projects${query}`, { token });
  },

  adminFeedback(status: "NEW" | "READ" | undefined, token: string): Promise<AdminFeedback[]> {
    const query = status ? `?status=${status}` : "";
    return request<AdminFeedback[]>(`/api/v1/admin/feedback${query}`, { token });
  },

  markFeedbackRead(id: string, token: string): Promise<null> {
    return request<null>(`/api/v1/admin/feedback/${encodeURIComponent(id)}/read`, {
      method: "POST",
      token,
    });
  },

  checkin(note: string | null, token: string): Promise<CheckinResult> {
    return request<CheckinResult>("/api/v1/checkins", {
      method: "POST",
      body: { note },
      token,
    });
  },
};

async function requestForm<T>(path: string, file: File, token: string): Promise<T> {
  const form = new FormData();
  form.append("file", file);

  let response: Response;
  try {
    response = await fetch(path, {
      method: "POST",
      body: form,
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    throw new ApiError("NETWORK", "网络异常，请检查后端是否已启动", 0);
  }

  const envelope = (await response.json().catch(() => null)) as Envelope<T> | null;
  if (!response.ok || !envelope?.ok || envelope.data === null) {
    const code = (envelope?.error?.code as ErrorCode | undefined) ?? "INTERNAL";
    throw new ApiError(code, envelope?.error?.message ?? "上传失败", response.status);
  }
  return envelope.data;
}

export function pageQuery(cursor?: string, limit = 20): string {
  const params = new URLSearchParams();
  if (cursor) {
    params.set("cursor", cursor);
  }
  params.set("limit", String(limit));
  return params.toString();
}
