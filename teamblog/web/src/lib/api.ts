import type {
  Envelope,
  ErrorCode,
  InviteInfo,
  RegisterRequest,
  TokenResponse,
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
};

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, token } = options;

  let response: Response;
  try {
    response = await fetch(path, {
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
};
