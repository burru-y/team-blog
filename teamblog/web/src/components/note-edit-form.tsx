"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { PageHeader } from "@/components/page-header";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { useSession } from "@/lib/session";

/**
 * 编辑学习笔记。
 *
 * 权限由后端判断（作者本人或管理员），前端只做「看得到/看不到按钮」的提示，
 * 不做安全边界——把判断放在前端等于没有判断。
 */
export function NoteEditForm({
  noteId,
  ownerUsername,
  initialTitle,
  initialContent,
  initialSummary,
  initialTags,
}: {
  noteId: string;
  ownerUsername: string;
  initialTitle: string;
  initialContent: string;
  initialSummary: string;
  initialTags?: string | null;
}) {
  const { user, token, loading } = useSession();
  const router = useRouter();

  const [title, setTitle] = useState(initialTitle);
  const [content, setContent] = useState(initialContent);
  const [summary, setSummary] = useState(initialSummary);
  const [tags, setTags] = useState(initialTags ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canEdit = Boolean(user) && (user!.role === "ADMIN" || user!.username === ownerUsername);

  if (loading) {
    return <p className="text-sm text-muted-foreground">正在读取身份…</p>;
  }

  if (!canEdit) {
    return (
      <div className="space-y-4">
        <PageHeader title="编辑笔记" description="只有作者本人和管理员可以修改。" />
        <div className="rounded-xl border border-dashed px-5 py-8 text-center text-sm text-muted-foreground">
          你没有修改这篇笔记的权限。
        </div>
        <Link href={`/learning/${noteId}`} className="text-sm text-brand hover:underline">
          ← 返回笔记
        </Link>
      </div>
    );
  }

  async function save() {
    if (!token) {
      return;
    }
    if (!title.trim()) {
      setError("标题不能为空");
      return;
    }
    if (!content.trim()) {
      setError("正文不能为空");
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await api.updateLearningLog(
        noteId,
        {
          title: title.trim(),
          contentMd: content,
          summaryMd: summary.trim() ? summary.trim() : null,
          tags: tags.trim() ? tags.trim() : null,
        },
        token,
      );
      router.push(`/learning/${noteId}`);
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        title="编辑笔记"
        description="正文支持 Markdown。保存后立即生效，历史内容不会保留版本。"
        actions={
          <Link href={`/learning/${noteId}`} className="text-sm text-muted-foreground hover:text-foreground">
            ← 取消
          </Link>
        }
      />

      <div className="space-y-2">
        <Label htmlFor="title">标题</Label>
        <Input
          id="title"
          value={title}
          maxLength={200}
          onChange={(event) => setTitle(event.target.value)}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="summary">摘要（可选）</Label>
        <Input
          id="summary"
          value={summary}
          maxLength={300}
          placeholder="一句话概括，列表页会显示"
          onChange={(event) => setSummary(event.target.value)}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="tags">标签（可选）</Label>
        <Input
          id="tags"
          value={tags}
          maxLength={200}
          placeholder="用逗号分隔，如：后端,部署"
          onChange={(event) => setTags(event.target.value)}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="content">正文（Markdown）</Label>
        <Textarea
          id="content"
          value={content}
          rows={18}
          onChange={(event) => setContent(event.target.value)}
        />
        <p className="text-xs text-muted-foreground">
          不会写 Markdown 可以看
          <Link href="/help/markdown" target="_blank" className="mx-1 underline underline-offset-4">
            语法速查
          </Link>
          。
        </p>
      </div>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      <div className="flex justify-end gap-2">
        <Button
          variant="ghost"
          onClick={() => router.push(`/learning/${noteId}`)}
          disabled={saving}
        >
          取消
        </Button>
        <Button onClick={() => void save()} disabled={saving}>
          {saving ? "保存中…" : "保存"}
        </Button>
      </div>
    </div>
  );
}
