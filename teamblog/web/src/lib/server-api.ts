import { pageQuery, request } from "./api";
import type { UserPage, UserResponse } from "./types";

/**
 * 服务端组件专用：Node 里没有"当前站点"，相对路径 `/api/...` 发不出去，
 * 必须用绝对地址直连后端（浏览器那边才走 Next 的同源代理）。
 *
 * 生产环境设 `API_ORIGIN` 指向内网地址即可，例如 http://api:8080。
 */
const ORIGIN = process.env.API_ORIGIN ?? "http://127.0.0.1:8080";

export const serverApi = {
  listMembers(cursor?: string, limit = 20): Promise<UserPage> {
    return request<UserPage>(`/api/v1/users?${pageQuery(cursor, limit)}`, { base: ORIGIN });
  },

  getUserProfile(username: string): Promise<UserResponse> {
    return request<UserResponse>(`/api/v1/users/${encodeURIComponent(username)}`, { base: ORIGIN });
  },
};
