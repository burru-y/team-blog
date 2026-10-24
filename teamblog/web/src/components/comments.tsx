"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { useSession } from "@/lib/session";
import type { CommentResponse } from "@/lib/types";

/**
 * 评论区（两级）。
 *
 * <p>初始数据由服务端组件传入（首屏不用等 JS），之后的发表/删除在客户端完成并
 * 用返回的结果更新本地列表，不整页刷新。
 *
 * <p>两级是硬规则：回复按钮只出现在一级评论上。给回复再回复会被后端 422 拒绝——
 * 前端不显示按钮只是体验，规则在后端。
 */
export function Comments({
  targetType,
  targetId,
  initial,
}: {
  targetType: "PROJECT" | "LEARNING_LOG";
  targetId: string;
  initial: CommentResponse[];
}) {
  const { user, token } = useSession();
  const [comments, setComments] = useState<CommentResponse[]>(initial);
  const [draft, setDraft] = useState("");
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const [replyDraft, setReplyDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function reload() {
    try {
      setComments(await api.listComments(targetType, targetId));
    } catch {
      /* 失败时保留现有列表，不打断阅读 */
    }
  }

  async function submit(parentId: string | null) {
    if (!token) {
      return;
    }
    const content = (parentId ? replyDraft : draft).trim();
    if (!content) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.createComment({ targetType, targetId, parentId, content }, token);
      setDraft("");
      setReplyDraft("");
      setReplyTo(null);
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "发表失败");
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: string) {
    if (!token || !window.confirm("删除后无法恢复（回复会保留占位）。确定吗？")) {
      return;
    }
    try {
      await api.deleteComment(id, token);
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "删除失败");
    }
  }

  const canDelete = (comment: CommentResponse) =>
    Boolean(user) && (user?.role === "ADMIN" || user?.username === comment.username);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">评论（{comments.length}）</CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {comments.length === 0 ? (
          <p className="text-sm text-muted-foreground">还没有评论，来说两句。</p>
        ) : (
          <ul className="space-y-4">
            {comments.map((comment) => (
              <li key={comment.id} className="space-y-2">
                <CommentBody comment={comment} canDelete={canDelete(comment)} onDelete={remove} />

                {comment.replies.length > 0 ? (
                  <ul className="ml-6 space-y-2 border-l pl-4">
                    {comment.replies.map((reply) => (
                      <li key={reply.id}>
                        <CommentBody comment={reply} canDelete={canDelete(reply)} onDelete={remove} />
                      </li>
                    ))}
                  </ul>
                ) : null}

                {token && !comment.deleted ? (
                  <div className="ml-6">
                    {replyTo === comment.id ? (
                      <div className="space-y-2">
                        <Textarea
                          value={replyDraft}
                          onChange={(e) => setReplyDraft(e.target.value)}
                          rows={2}
                          placeholder={`回复 @${comment.username}`}
                        />
                        <div className="flex gap-2">
                          <Button size="sm" onClick={() => submit(comment.id)} disabled={busy}>
                            发表回复
                          </Button>
                          <Button size="sm" variant="ghost" onClick={() => setReplyTo(null)}>
                            取消
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <Button size="sm" variant="ghost" onClick={() => setReplyTo(comment.id)}>
                        回复
                      </Button>
                    )}
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}

        {token ? (
          <div className="space-y-2 border-t pt-4">
            <Textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              rows={3}
              placeholder="写下你的评论…"
            />
            <Button onClick={() => submit(null)} disabled={busy}>
              {busy ? "发表中…" : "发表评论"}
            </Button>
          </div>
        ) : (
          <p className="border-t pt-4 text-sm text-muted-foreground">
            登录后可以参与讨论。
          </p>
        )}

        {error ? <p className="text-sm text-destructive">{error}</p> : null}
      </CardContent>
    </Card>
  );
}

function CommentBody({
  comment,
  canDelete,
  onDelete,
}: {
  comment: CommentResponse;
  canDelete: boolean;
  onDelete: (id: string) => void;
}) {
  return (
    <div className="space-y-1">
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        <span className="font-medium text-foreground">{comment.username}</span>
        <time>{new Date(comment.createdAt).toLocaleString("zh-CN")}</time>
        {canDelete && !comment.deleted ? (
          <button
            type="button"
            className="text-destructive hover:underline"
            onClick={() => onDelete(comment.id)}
          >
            删除
          </button>
        ) : null}
      </div>
      <p className="whitespace-pre-wrap text-sm">
        {comment.deleted ? <span className="italic text-muted-foreground">该评论已删除</span> : comment.content}
      </p>
    </div>
  );
}
