"use client";

import { useState } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";

import { PageHeader } from "@/components/page-header";
/**
 * 留言页（公开，无需登录）。
 *
 * <p>`website` 是蜜罐字段：用 CSS 移出可视区域而不是 `display:none`——
 * 有些机器人会跳过 `display:none` 的字段，但不会跳过"在屏幕外"的字段。
 * 同时给它 `tabIndex={-1}` 与 `autoComplete="off"`，真人用键盘 Tab 不会撞上它。
 */
export default function FeedbackPage() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [content, setContent] = useState("");
  const [website, setWebsite] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.submitFeedback({
        name: name.trim(),
        email: email.trim() || null,
        content: content.trim(),
        website,
      });
      setDone(true);
      setName("");
      setEmail("");
      setContent("");
      setWebsite("");
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "提交失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <PageHeader
        title="留言反馈"
        description="发现链接失效、内容有误，或者想提建议，都可以写在这里。不需要登录。"
      />

      {done ? (
        <Alert>
          <AlertTitle>已收到</AlertTitle>
          <AlertDescription>感谢反馈，管理员会尽快处理。</AlertDescription>
        </Alert>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">写留言</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">称呼</Label>
              <Input
                id="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={50}
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">邮箱（可选，方便回复你）</Label>
              <Input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                maxLength={255}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="content">内容</Label>
              <Textarea
                id="content"
                value={content}
                onChange={(e) => setContent(e.target.value)}
                rows={6}
                maxLength={2000}
                required
              />
            </div>

            {/* 蜜罐：正常用户看不到也 Tab 不到，机器人会填 */}
            <div aria-hidden="true" className="absolute -left-full top-auto h-px w-px overflow-hidden">
              <label htmlFor="website">网站</label>
              <input
                id="website"
                name="website"
                type="text"
                tabIndex={-1}
                autoComplete="off"
                value={website}
                onChange={(e) => setWebsite(e.target.value)}
              />
            </div>

            {error ? <p className="text-sm text-destructive">{error}</p> : null}

            <Button type="submit" disabled={busy}>
              {busy ? "提交中…" : "提交"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
