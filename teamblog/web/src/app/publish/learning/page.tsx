"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { TagPicker, joinTags } from "@/components/tag-picker";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { PublishTabs } from "@/components/publish-tabs";
import { UploadQueue } from "@/components/upload-queue";
import { useSession } from "@/lib/session";

import { PageHeader } from "@/components/page-header";
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
  const [tags, setTags] = useState<string[]>([]);
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



  /** 把上传产物的 Markdown 追加到正文末尾。 */
  function appendMd(markdown: string) {
    setContentMd((current) => `${current}${current ? "\n\n" : ""}${markdown}`);
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
        tags: joinTags(tags),
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

      <PageHeader
        title="写学习内容"
        description="每篇 +10 分，每天前 3 篇计分；之后的照样保存，只是不再加分。"
      />

      <div className="space-y-2">
        <Label htmlFor="title">标题</Label>
        <Input id="title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required />
      </div>

      <div className="space-y-2">
        <Label htmlFor="tags">标签（可选）</Label>
        <TagPicker value={tags} onChange={setTags} />
      </div>


      <div className="space-y-2">
        <Label htmlFor="summary">摘要（可选）</Label>
        <Input id="summary" value={summary} onChange={(e) => setSummary(e.target.value)} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">插入</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <UploadQueue
            kind="code"
            accept=".java,.py,.js,.ts,.tsx,.sql,.json,.yml,.yaml,.sh,.kt,.go,.rs,.c,.cpp,.cs,.rb,.php,.md,.txt"
            title="拖入代码文件"
            description="会作为代码块插入正文光标处"
            variant="default"
            onUploaded={({ data }) => {
              const block = data as { markdown: string; language: string; lines: number };
              appendMd(block.markdown);
              setNotice(`已插入 ${block.language} 代码块（${block.lines} 行）`);
            }}
          />
          <UploadQueue
            kind="image"
            accept="image/png,image/jpeg,image/gif,image/webp"
            title="拖入图片"
            description="会插入 Markdown 图片语法"
            variant="default"
            onUploaded={({ data }) => {
              const image = data as { url: string };
              appendMd(`![图片](${image.url})`);
              setNotice("已插入图片");
            }}
          />
        </CardContent>
      </Card>

      <div className="space-y-2">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <Label htmlFor="content">正文（Markdown）</Label>
          {/* 不熟悉 Markdown 的人在这里会卡住，给一个随手可点的入口 */}
          <Link
            href="/help/markdown"
            target="_blank"
            className="text-xs text-muted-foreground underline underline-offset-4 hover:text-foreground"
          >
            不会写？看语法速查 ↗
          </Link>
        </div>
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
