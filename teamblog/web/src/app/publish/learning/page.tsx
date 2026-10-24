"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { PublishTabs } from "@/components/publish-tabs";
import { useSession } from "@/lib/session";

/**
 * 学习内容编辑器。
 *
 * <p>两个上传入口各有用处：
 * - 图片：拿到 URL 插进正文（`![](url)`）
 * - 代码文件：后端转成代码块，直接拼到正文末尾
 *
 * <p>发布后必须处理"没加分"的情况：第 4 篇起照常入库但 0 分，
 * 提示要说清楚，否则用户以为系统漏算了。
 */
export default function PublishLearningPage() {
  const router = useRouter();
  const { user, token, loading } = useSession();

  const [title, setTitle] = useState("");
  const [contentMd, setContentMd] = useState("");
  const [summary, setSummary] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (loading) {
    return <p className="py-10 text-sm text-muted-foreground">正在确认登录状态…</p>;
  }
  if (!user || !token) {
    return (
      <p className="py-10 text-sm text-muted-foreground">
        发布需要先登录。<Link className="text-primary underline" href="/login">去登录</Link>
      </p>
    );
  }

  async function handleCode(file: File) {
    if (!token) {
      return;
    }
    setError(null);
    try {
      const block = await api.uploadCode(file, token);
      setContentMd((current) => `${current}${current ? "\n\n" : ""}${block.markdown}`);
      setNotice(`已插入 ${block.language} 代码块（${block.lines} 行）`);
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "代码文件解析失败");
    }
  }

  async function handleImage(file: File) {
    if (!token) {
      return;
    }
    setError(null);
    try {
      const uploaded = await api.uploadImage(file, token);
      setContentMd((current) => `${current}${current ? "\n\n" : ""}![图片](${uploaded.url})`);
      setNotice("已插入图片");
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "图片上传失败");
    }
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!token) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const created = await api.createLearningLog(
        {
          type: "NOTE",
          title: title.trim(),
          contentMd,
          summaryMd: summary.trim() || null,
        },
        token,
      );

      if (!created.counted) {
        // 第 4 篇起：内容保存了，但今天不再计分
        window.alert("内容已保存，但今天的计分额度已用完（每天前 3 篇计分）。");
      }
      router.push(`/learning/${created.log.id}`);
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "发布失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mx-auto max-w-3xl space-y-6">
      <PublishTabs />

      <header className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">写学习内容</h1>
        <p className="text-sm text-muted-foreground">
          每篇 +10 分，每天前 3 篇计分；之后的照样保存，只是不再加分。
        </p>
      </header>

      <div className="space-y-2">
        <Label htmlFor="title">标题</Label>
        <Input id="title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required />
      </div>

      <div className="space-y-2">
        <Label htmlFor="summary">摘要（可选）</Label>
        <Input id="summary" value={summary} onChange={(e) => setSummary(e.target.value)} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">插入</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-4">
          <div className="space-y-1">
            <Label htmlFor="code" className="text-xs">代码文件</Label>
            <Input
              id="code"
              type="file"
              accept=".java,.py,.js,.ts,.tsx,.sql,.json,.yml,.yaml,.sh,.kt,.go,.rs,.c,.cpp,.cs,.rb,.php,.md,.txt"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) {
                  void handleCode(file);
                }
              }}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="image2" className="text-xs">图片</Label>
            <Input
              id="image2"
              type="file"
              accept="image/png,image/jpeg,image/gif,image/webp"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) {
                  void handleImage(file);
                }
              }}
            />
          </div>
        </CardContent>
      </Card>

      <div className="space-y-2">
        <Label htmlFor="content">正文（Markdown）</Label>
        <Textarea
          id="content"
          value={contentMd}
          onChange={(e) => setContentMd(e.target.value)}
          rows={16}
          required
          placeholder={"## 今天学了什么\n\n- 第一点\n- 第二点"}
        />
      </div>

      {notice ? <p className="text-sm text-muted-foreground">{notice}</p> : null}
      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      <Button type="submit" disabled={busy}>
        {busy ? "发布中…" : "发布"}
      </Button>
    </form>
  );
}
