"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import type { InviteInfo } from "@/lib/types";

type State =
  | { kind: "loading" }
  | { kind: "invalid"; message: string }
  | { kind: "ready"; info: InviteInfo }
  | { kind: "done" };

export function JoinForm({ token }: { token: string }) {
  const [state, setState] = useState<State>({ kind: "loading" });
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const info = await api.inviteInfo(token);
        if (!cancelled) {
          setState({ kind: "ready", info });
        }
      } catch (err) {
        if (!cancelled) {
          setState({
            kind: "invalid",
            message: err instanceof ApiError ? messageFor(err.code) : "邀请链接无效",
          });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [token]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.acceptInvite(token, {
        username,
        password,
        email: email.trim() === "" ? null : email,
      });
      setState({ kind: "done" });
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "注册失败");
    } finally {
      setSubmitting(false);
    }
  }

  if (state.kind === "loading") {
    return <p className="py-16 text-center text-sm text-muted-foreground">正在校验邀请…</p>;
  }

  if (state.kind === "invalid") {
    return (
      <div className="mx-auto max-w-sm py-16">
        <Alert variant="destructive">
          <AlertTitle>邀请不可用</AlertTitle>
          <AlertDescription>{state.message}。请联系管理员重新生成链接。</AlertDescription>
        </Alert>
        <p className="mt-6 text-center text-sm">
          <Link href="/" className="underline-offset-4 hover:underline">
            返回首页
          </Link>
        </p>
      </div>
    );
  }

  if (state.kind === "done") {
    return (
      <div className="mx-auto max-w-sm py-16">
        <Alert>
          <AlertTitle>注册成功</AlertTitle>
          <AlertDescription>账号已创建，现在可以用刚设置的密码登录了。</AlertDescription>
        </Alert>
        <div className="mt-6 text-center">
          <Link href="/login" className={buttonVariants()}>
            去登录
          </Link>
        </div>
      </div>
    );
  }

  // 邀请指定了邮箱时，邮箱不可改：这张邀请本来就是发给那个人的
  const emailLocked = Boolean(state.info.email);

  return (
    <div className="mx-auto max-w-sm py-10">
      <Card>
        <CardHeader>
          <CardTitle>加入团队</CardTitle>
          <CardDescription>
            {emailLocked
              ? `这张邀请是发给 ${state.info.email} 的`
              : `邀请有效至 ${new Date(state.info.expiresAt).toLocaleString("zh-CN")}`}
          </CardDescription>
        </CardHeader>

        <form onSubmit={handleSubmit}>
          <CardContent className="space-y-4">
            {error ? (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}

            <div className="space-y-2">
              <Label htmlFor="username">用户名</Label>
              <Input
                id="username"
                autoComplete="username"
                required
                pattern="[a-zA-Z0-9_]{3,20}"
                title="3-20 位字母、数字或下划线"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
              <p className="text-xs text-muted-foreground">3-20 位字母、数字或下划线，注册后不可修改</p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">密码</Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                required
                minLength={8}
                maxLength={72}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <p className="text-xs text-muted-foreground">至少 8 位</p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">邮箱</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                required={!emailLocked}
                disabled={emailLocked}
                value={emailLocked ? (state.info.email ?? "") : email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
          </CardContent>

          <CardFooter className="pt-4">
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? "创建中…" : "创建账号"}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}
