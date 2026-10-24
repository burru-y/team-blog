"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
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
import { ApiError } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { useSession } from "@/lib/session";

export default function LoginPage() {
  const { login } = useSession();
  const router = useRouter();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username, password);
      router.push("/");
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "登录失败");
    } finally {
      // 必须放在 finally：失败时按钮也要恢复可点，否则用户卡死在这一页
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto flex max-w-sm flex-col gap-6 py-10">
      <Card>
        <CardHeader>
          <CardTitle>成员登录</CardTitle>
          <CardDescription>用管理员发给你的账号登录</CardDescription>
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
                name="username"
                autoComplete="username"
                autoFocus
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">密码</Label>
              <Input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </CardContent>

          <CardFooter className="flex-col gap-3 pt-4">
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? "登录中…" : "登录"}
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              还没有账号？用邀请链接注册（由管理员生成）
            </p>
          </CardFooter>
        </form>
      </Card>

      <p className="text-center text-xs text-muted-foreground">
        连续输错 5 次会被临时限制，10 分钟后自动恢复
      </p>

      <p className="text-center text-sm">
        <Link href="/" className="underline-offset-4 hover:underline">
          返回首页
        </Link>
      </p>
    </div>
  );
}
