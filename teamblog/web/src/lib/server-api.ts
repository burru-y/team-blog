import { pageQuery, request } from "./api";
import type {
  Announcement,
  HelpFile,
  CommentResponse,
  HeatmapResponse,
  LearningLogResponse,
  ProjectPage,
  ProjectResponse,
  UserPage,
  UserResponse,
} from "./types";

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

  getLearningLog(id: string): Promise<LearningLogResponse> {
    return request<LearningLogResponse>(`/api/v1/learning-logs/${encodeURIComponent(id)}`, { base: ORIGIN });
  },

  /** 公告列表（公告页）。 */
  listAnnouncements(kind?: "CHANGELOG"): Promise<Announcement[]> {
    const query = kind ? `?kind=${kind}` : "";
    return request<Announcement[]>(`/api/v1/announcements${query}`, { base: ORIGIN });
  },

  /** 帮助资料列表（帮助页下载区）。 */
  listHelpFiles(): Promise<HelpFile[]> {
    return request<HelpFile[]>("/api/v1/help/files", { base: ORIGIN });
  },

  /** 全站最新笔记（首页模块用）。不传 username 时后端返回最新 limit 条。 */
  listLatestLearningLogs(limit = 5): Promise<LearningLogResponse[]> {
    return request<LearningLogResponse[]>(`/api/v1/learning-logs?limit=${limit}`, {
      base: ORIGIN,
    });
  },

  listLearningLogs(username: string): Promise<LearningLogResponse[]> {
    return request<LearningLogResponse[]>(
      `/api/v1/learning-logs?username=${encodeURIComponent(username)}`,
      { base: ORIGIN },
    );
  },

  /** 个人页用：按作者筛选（后端 `?author=`），不传则返回全站成果。 */
  listProjects(cursor?: string, limit = 20, author?: string): Promise<ProjectPage> {
    const base = pageQuery(cursor, limit);
    const suffix = author ? `&author=${encodeURIComponent(author)}` : "";
    return request<ProjectPage>(`/api/v1/projects?${base}${suffix}`, { base: ORIGIN });
  },

  getProject(id: string): Promise<ProjectResponse> {
    return request<ProjectResponse>(`/api/v1/projects/${encodeURIComponent(id)}`, { base: ORIGIN });
  },

  listComments(targetType: "PROJECT" | "LEARNING_LOG", targetId: string): Promise<CommentResponse[]> {
    return request<CommentResponse[]>(
      `/api/v1/comments?targetType=${targetType}&targetId=${encodeURIComponent(targetId)}`,
      { base: ORIGIN },
    );
  },

  /** 热力图：不给 username 是团队视角，给了是个人视角。 */
  getHeatmap(username?: string, days = 120): Promise<HeatmapResponse> {
    const params = new URLSearchParams({ days: String(days) });
    if (username) {
      params.set("username", username);
    }
    return request<HeatmapResponse>(`/api/v1/stats/heatmap?${params.toString()}`, { base: ORIGIN });
  },
};
