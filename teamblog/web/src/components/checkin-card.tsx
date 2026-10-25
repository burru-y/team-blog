"use client";

import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { useSession } from "@/lib/session";
import type { CheckinStatus } from "@/lib/types";

/**
 * 打卡卡片，可顺带提交一条学习内容。
 *
 * <p>打卡成功后直接用接口返回的 streak / gained / totalPoints / level 更新本地状态，
 * 不刷新页面也不再发一次请求，避免"点了没反应"的半秒空窗。
 *
 * <p>409 ALREADY_CHECKED_IN 不是错误：说明别的标签页已经打过了，重新拉一次状态即可。
 *
 * <p>学习内容是**第二次请求**，不是和打卡同一个事务。这样打卡不会因为内容校验失败
 * 而整体回滚；代价是两件事可能只成功一件，所以结果要分别告诉用户。
 */
export function CheckinCard() {
  const { user, token, loading } = useSession();
  const [status, setStatus] = useState<CheckinStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [flash, setFlash] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [noteOpen, setNoteOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");

  useEffect(() => {
    if (!token) {
      setStatus(null);
      return;
    }
    let cancelled = false;
    api
      .checkinStatus(token)
      .then((next) => {
        if (!cancelled) {
          setStatus(next);
        }
      })
      .catch(() => {
        /* 状态拿不到就不显示数字，不打扰用户 */
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  if (loading || !user) {
    return null;
  }

  async function submitLearningLog(currentToken: string) {
    const body = content.trim();
    if (!body) {
      return "打卡完成（没有填写学习内容）";
    }
    // 标题留空时取正文前 20 字，避免用户为了打个卡还要想标题
    const finalTitle = title.trim() || body.slice(0, 20);
    try {
      await api.createLearningLog(
        { type: "NOTE", title: finalTitle, contentMd: body },
        currentToken,
      );
      setTitle("");
      setContent("");
      setNoteOpen(false);
      return "打卡完成，学习内容已提交";
    } catch (err) {
      const detail = err instanceof ApiError ? messageFor(err.code) : "请稍后在发布页重试";
      return `打卡完成，但学习内容提交失败：${detail}`;
    }
  }

  async function handleCheckin() {
    if (!token) {
      return;
    }
    setBusy(true);
    setError(null);
    setFlash(null);
    try {
      const result = await api.checkin(null, token);
      setStatus({
        checkedInToday: true,
        today: result.checkinDate,
        streak: result.streak,
        totalPoints: result.totalPoints,
        level: result.level,
      });
      const gained = result.gained > 1 ? `+${result.gained}（含连续奖励）` : `+${result.gained}`;
      const noteResult = await submitLearningLog(token);
      setFlash(`${gained} · ${noteResult}`);
    } catch (err) {
      if (err instanceof ApiError && err.code === "ALREADY_CHECKED_IN") {
        const next = await api.checkinStatus(token).catch(() => null);
        if (next) {
          setStatus(next);
        }
        // 今天已打卡：内容仍然可以提交，不因为"打过了"就丢掉他写的东西
        const noteResult = await submitLearningLog(token);
        setFlash(noteResult);
      } else {
        setError(err instanceof ApiError ? messageFor(err.code) : "打卡失败");
      }
    } finally {
      setBusy(false);
    }
  }

  const checkedIn = Boolean(status?.checkedInToday);
  const nothingToDo = checkedIn && !content.trim();

  return (
    <Card className="flex h-full flex-col">
      <CardHeader className="flex-row items-start justify-between space-y-0">
        <div>
          <CardTitle className="text-base">今日打卡</CardTitle>
          <p className="mt-1 text-xs text-muted-foreground">
            {status ? `连续 ${status.streak} 天` : "正在读取状态…"}
          </p>
        </div>
        {status ? (
          <span className="rounded-full border px-2 py-0.5 text-xs tabular-nums text-muted-foreground">
            Lv{status.level}
          </span>
        ) : null}
      </CardHeader>

      <CardContent className="flex flex-1 flex-col gap-4">
        {/* 积分是这个卡片的主角，字号给足 */}
        <div>
          <p className="text-3xl font-semibold tabular-nums">
            {status ? status.totalPoints : "—"}
            <span className="ml-1 text-sm font-normal text-muted-foreground">分</span>
          </p>
          {flash ? (
            <p className="mt-1 text-xs text-emerald-600 dark:text-emerald-400">{flash}</p>
          ) : (
            <p className="mt-1 text-xs text-muted-foreground">
              {checkedIn ? "今天已打卡" : "每天打卡 +1 分，连续 7 天起有额外奖励"}
            </p>
          )}
        </div>

        <div className="mt-auto space-y-2">
          <Button className="w-full" onClick={handleCheckin} disabled={busy || nothingToDo}>
            {busy ? "提交中…" : checkedIn ? (content.trim() ? "提交学习内容" : "今天已打卡") : "打卡"}
          </Button>

          <button
            type="button"
            onClick={() => setNoteOpen((open) => !open)}
            className="w-full rounded-md py-1 text-xs text-muted-foreground transition-colors hover:text-foreground"
          >
            {noteOpen ? "收起" : "顺便记一笔学习内容"}
          </button>
        </div>

        {noteOpen ? (
          <div className="space-y-3 rounded-lg border bg-muted/30 p-3">
            <div className="space-y-1">
              <Label htmlFor="checkin-note-title" className="text-xs">
                标题（可留空，自动取正文前 20 字）
              </Label>
              <Input
                id="checkin-note-title"
                value={title}
                maxLength={200}
                placeholder="今天学了什么"
                onChange={(event) => setTitle(event.target.value)}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="checkin-note-content" className="text-xs">
                学习内容（Markdown，可留空只打卡）
              </Label>
              <Textarea
                id="checkin-note-content"
                rows={3}
                value={content}
                placeholder="例如：今天弄明白了 JWT 的 tv 字段为什么能撤销令牌……"
                onChange={(event) => setContent(event.target.value)}
              />
            </div>

            <p className="text-xs text-muted-foreground">
              每天前 3 篇学习内容计分，之后的照样保存但不加分。
            </p>
          </div>
        ) : null}
      </CardContent>

      {error ? <p className="px-6 pb-4 text-xs text-destructive">{error}</p> : null}
    </Card>
  );
}
