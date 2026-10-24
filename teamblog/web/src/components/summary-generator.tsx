"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { useSession } from "@/lib/session";

/**
 * 摘要生成与保存（批次 7.3 的前端部分）。
 *
 * 两个刻意的设计：
 * 1. **生成不落库**：AI 生成后只填进编辑框。自动保存等于替用户决定，
 *    而他可能还没看完/还想改。
 * 2. **保存要显式点**：PUT 时把 title/contentMd 原样带回，因为后端接口是整体替换，
 *    不是 PATCH——少传一个字段就会被清空。
 */
export function SummaryGenerator({
  logId,
  ownerUsername,
  title,
  contentMd,
  initialSummary,
}: {
  logId: string;
  ownerUsername: string;
  title: string;
  contentMd: string;
  initialSummary: string | null;
}) {
  const router = useRouter();
  const { user, token } = useSession();
  const [summary, setSummary] = useState(initialSummary ?? "");
  const [busy, setBusy] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const canEdit = Boolean(token) && (user?.role === "ADMIN" || user?.username === ownerUsername);
  if (!canEdit) {
    return null;
  }

  async function generate() {
    if (!token) {
      return;
    }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const result = await api.summarizeLearningLog(logId, token);
      setSummary(result.summary);
      setNotice("已生成，可以直接改，确认后再保存");
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "生成失败");
    } finally {
      setBusy(false);
    }
  }

  async function save() {
    if (!token) {
      return;
    }
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      await api.updateLearningLog(logId, { title, contentMd, summaryMd: summary.trim() || null }, token);
      setNotice("已保存");
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-2 rounded-lg border p-3">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium">摘要（AI 辅助）</p>
        <div className="flex gap-2">
          <Button size="sm" variant="outline" onClick={() => void generate()} disabled={busy || saving}>
            {busy ? "生成中…" : "生成摘要"}
          </Button>
          <Button size="sm" onClick={() => void save()} disabled={saving || !summary.trim()}>
            {saving ? "保存中…" : "保存摘要"}
          </Button>
        </div>
      </div>
      <Textarea
        value={summary}
        onChange={(event) => setSummary(event.target.value)}
        rows={3}
        maxLength={500}
        placeholder="点「生成摘要」让 AI 写一段，或者自己手写"
      />
      {notice ? <p className="text-xs text-muted-foreground">{notice}</p> : null}
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  );
}
