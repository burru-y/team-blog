"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { api } from "./api";
import type { TokenResponse, UserResponse } from "./types";

/**
 * 会话状态。
 *
 * 为什么 access token 只放在内存里：规范 T-19 禁止把凭证写进 localStorage。
 * 代价是刷新页面就没了——所以启动时用 httpOnly 的 refresh Cookie 换一个新的。
 *
 * 为什么有个模块级的 refreshPromise：React 开发模式会把 effect 跑两遍，
 * 两次并发 refresh 会触发后端的重放检测（同一个 refresh token 用两次 = 被盗），
 * 结果就是开发环境里一刷新就被踢下线。共享同一个 Promise 只发一次请求。
 */
let refreshPromise: Promise<TokenResponse> | null = null;

function bootstrapSession(): Promise<TokenResponse> {
  refreshPromise ??= api.refresh().catch((error) => {
    refreshPromise = null;
    throw error;
  });
  return refreshPromise;
}

type SessionValue = {
  user: UserResponse | null;
  token: string | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const SessionContext = createContext<SessionValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const adopt = useCallback(async (tokens: TokenResponse) => {
    const me = await api.me(tokens.accessToken);
    setToken(tokens.accessToken);
    setUser(me);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const tokens = await bootstrapSession();
        if (!cancelled) {
          await adopt(tokens);
        }
      } catch {
        // 没有 Cookie 或已过期：这是正常状态，不是错误
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [adopt]);

  const login = useCallback(
    async (username: string, password: string) => {
      await adopt(await api.login(username, password));
    },
    [adopt],
  );

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } finally {
      refreshPromise = null;
      setToken(null);
      setUser(null);
    }
  }, []);

  const value = useMemo(
    () => ({ user, token, loading, login, logout }),
    [user, token, loading, login, logout],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error("useSession 必须在 SessionProvider 内使用");
  }
  return context;
}
