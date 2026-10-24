import type { ErrorCode } from "./types";

/**
 * 错误码 → 中文文案（T-09：前端按 code 分支，不直接渲染后端 message）。
 *
 * 为什么不直接用后端 message：后端文案是给开发者和日志看的，前端要统一语气、
 * 也要能在不重启后端的情况下改文案。两边各管各的。
 */
export const ERROR_MESSAGES: Record<ErrorCode, string> = {
  UNAUTHENTICATED: "用户名或密码错误",
  SESSION_REVOKED: "登录已失效，请重新登录",
  FORBIDDEN: "没有访问权限",
  NOT_FOUND: "内容不存在",
  INVITE_INVALID: "邀请链接无效或已失效",
  VALIDATION_FAILED: "请检查填写的内容",
  RULE_VIOLATED: "操作不符合规则",
  CONFLICT: "已被占用，请换一个",
  ALREADY_CHECKED_IN: "今天已经打过卡了",
  RATE_LIMITED: "尝试过于频繁，请稍后再试",
  UPSTREAM_FAILED: "服务暂时不可用，请稍后再试",
  INTERNAL: "服务出错了，请稍后再试",
  NETWORK: "网络异常，请确认后端已启动",
};

const UNKNOWN = "请求失败";

export function messageFor(code: string | undefined, fallback?: string): string {
  if (!code) {
    return fallback ?? UNKNOWN;
  }
  return ERROR_MESSAGES[code as ErrorCode] ?? fallback ?? UNKNOWN;
}
