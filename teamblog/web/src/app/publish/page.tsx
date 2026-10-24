"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { useSession } from "@/lib/session";
import type { ProjectCreateBody } from "@/lib/types";

const TYPES = [
  { value: "PROJECT", label: "项目", hint: "需要仓库链接 + 正文" },
  { value: "CERTIFICATE", label: "证书", hint: "至少要有图片或链接" },
  { value: "AWARD", label: "奖项", hint: "至少要有图片或链接" },
] as const;

/** 发布成果表单。必填规则由后端按类型判断，前端只做"看起来对"的提示。 */
export default function PublishProjectPage() {
  const router = useRouter();
  const { user, token, loading } = useSession();

  const [type, setType] = useState<ProjectCreateBody["type"]>("PROJECT");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [githubUrl, setGithubUrl] = useState("");
  const [contentMd, setContentMd] = useState("");
  const [imageUrl, setImageUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (loading) {
    return <p className="py-10 text-sm text-muted-foreground">正在确认登录状态…</p>;
  }
  if (!user || !token) {
    return (
      <p className="py-10 text-sm text-muted-foreground">
        发布需要先登录。<a className="text-primary underline" href="/login">去登录</a>
      </p>
    );
  }

  async function handleImage(file: File) {
    if (!token) {
      return;
    }
    setUploading(true);
    setError(null);
    try {
      const uploaded = await api.uploadImage(file, token);
      setImageUrl(uploaded.url);
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "上传失败");
    } finally {
      setUploading(false);
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
      const created = await api.createProject(
        {
          type,
          title: title.trim(),
          description: description.trim(),
          githubUrl: githubUrl.trim() || null,
          contentMd: contentMd.trim() || null,
          imageUrl: imageUrl || null,
        },
        token,
      );
      router.push(`/projects/${created.id}`);
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "发布失败");
    } finally {
      setBusy(false);
    }
  }

  const activeType = TYPES.find((item) => item.value === type);

  return (
    <form onSubmit={handleSubmit} className="mx-auto max-w-2xl space-y-6">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">发布成果</h1>
        <p className="text-sm text-muted-foreground">{activeType?.hint}</p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">类型</CardTitle>
        </CardHeader>
        <CardContent className="flex gap-2">
          {TYPES.map((item) => (
            <Button
              key={item.value}
              type="button"
              variant={type === item.value ? "default" : "outline"}
              size="sm"
              onClick={() => setType(item.value)}
            >
              {item.label}
            </Button>
          ))}
        </CardContent>
      </Card>

      <div className="space-y-2">
        <Label htmlFor="title">标题</Label>
        <Input id="title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required />
      </div>

      <div className="space-y-2">
        <Label htmlFor="description">一句话描述</Label>
        <Textarea
          id="description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          required
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="githubUrl">链接（仓库 / 证书地址）</Label>
        <Input
          id="githubUrl"
          value={githubUrl}
          onChange={(e) => setGithubUrl(e.target.value)}
          placeholder="https://github.com/..."
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="image">配图</Label>
        <Input
          id="image"
          type="file"
          accept="image/png,image/jpeg,image/gif,image/webp"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) {
              void handleImage(file);
            }
          }}
        />
        {uploading ? <p className="text-xs text-muted-foreground">上传中…</p> : null}
        {imageUrl ? <p className="text-xs text-muted-foreground">已上传：{imageUrl}</p> : null}
      </div>

      <div className="space-y-2">
        <Label htmlFor="contentMd">正文（Markdown）</Label>
        <Textarea
          id="contentMd"
          value={contentMd}
          onChange={(e) => setContentMd(e.target.value)}
          rows={10}
          placeholder={"# 项目简介\n\n- 做了什么\n- 用了什么技术"}
        />
      </div>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      <Button type="submit" disabled={busy || uploading}>
        {busy ? "发布中…" : "发布"}
      </Button>
    </form>
  );
}
