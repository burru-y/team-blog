"use client";

import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { useSession } from "@/lib/session";
import type { CheckinStatus } from "@/lib/types";

/**
 * 打卡卡片。
 *
 * <p>打卡成功后直接用接口返回的 `streak / gained / totalPoints / level` 更新本地状态，
 * **不刷新页面、不再发一次请求**——否则会有半秒空窗，体验上就是"点了没反应"。
 *
 * <p>409 `ALREADY_CHECKED_IN` 不是错误：说明别的标签页已经打过了，
 * 重新拉一次状态即可，不该弹红色报错。
 */
export function CheckinCard() {
  const { user, token, loading } = useSession();
  const [status, setStatus] = useState<CheckinStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [flash, setFlash] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

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
      setFlash(result.gained > 1 ? `+${result.gained}（含连续奖励）` : `+${result.gained}`);
    } catch (err) {
      if (err instanceof ApiError && err.code === "ALREADY_CHECKED_IN") {
        const next = await api.checkinStatus(token).catch(() => null);
        if (next) {
          setStatus(next);
        }
        setFlash("今天已经打过卡了");
      } else {
        setError(err instanceof ApiError ? messageFor(err.code) : "打卡失败");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle className="text-base">今日打卡</CardTitle>
        {status ? (
          <span className="text-xs text-muted-foreground">
            连续 {status.streak} 天 · Lv{status.level}
          </span>
        ) : null}
      </CardHeader>
      <CardContent className="flex items-center justify-between gap-3">
        <div className="text-sm text-muted-foreground">
          {status ? (
            <>
              当前 <span className="font-medium text-foreground">{status.totalPoints}</span> 分
              {flash ? <span className="ml-2 text-emerald-600">{flash}</span> : null}
            </>
          ) : (
            "正在读取打卡状态…"
          )}
        </div>
        <Button onClick={handleCheckin} disabled={busy || status?.checkedInToday}>
          {status?.checkedInToday ? "今天已打卡" : busy ? "打卡中…" : "打卡"}
        </Button>
      </CardContent>
      {error ? <p className="px-6 pb-4 text-xs text-destructive">{error}</p> : null}
    </Card>
  );
}
