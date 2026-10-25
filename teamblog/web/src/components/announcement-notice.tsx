"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Megaphone, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import type { Announcement } from "@/lib/types";

/**
 * 进站提示：有新公告时弹一次。
 *
 * 判断「新」用的是**最新一条公告的 id**，不是时间戳：
 * 时间戳会因为时区、时钟偏差出现「每次都算新」的情况，而 id 一旦见过就不会再变。
 *
 * 存 localStorage 而不是 Cookie：这是纯前端的展示偏好，
 * 不需要发给服务端，也就不该出现在请求头里。
 */
const SEEN_KEY = "teamblog.announcement.seen";

export function AnnouncementNotice() {
  const [latest, setLatest] = useState<Announcement | null>(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    let alive = true;

    api
      .listAnnouncements()
      .then((list) => {
        if (!alive || list.length === 0) {
          return;
        }
        // 置顶优先，否则取最新——和后端的排序保持一致
        const top = list.slice().sort((a, b) => {
          if (a.pinned !== b.pinned) {
            return a.pinned ? -1 : 1;
          }
          return b.publishedAt.localeCompare(a.publishedAt);
        })[0];

        let seen: string | null = null;
        try {
          seen = window.localStorage.getItem(SEEN_KEY);
        } catch {
          // 隐私模式下读不到，当作没看过：宁可多弹一次
        }

        if (seen !== top.id) {
          setLatest(top);
          setOpen(true);
        }
      })
      .catch(() => {
        // 公告取不到不影响页面，静默失败
      });

    return () => {
      alive = false;
    };
  }, []);

  function dismiss() {
    setOpen(false);
    if (latest) {
      try {
        window.localStorage.setItem(SEEN_KEY, latest.id);
      } catch {
        /* 同上 */
      }
    }
  }

  if (!open || !latest) {
    return null;
  }

  return (
    <div className="fixed inset-x-3 bottom-3 z-50 sm:inset-x-auto sm:right-4 sm:bottom-4 sm:w-96">
      <div className="overflow-hidden rounded-xl border bg-background shadow-lg">
        <div className="flex items-start gap-3 px-4 py-3">
          <span className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-full bg-brand/10 text-brand">
            <Megaphone className="size-4" aria-hidden />
          </span>
          <div className="min-w-0 flex-1">
            <p className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-medium">{latest.title}</span>
              {latest.version ? (
                <span className="rounded border px-1.5 py-0.5 font-mono text-xs text-muted-foreground">
                  {latest.version}
                </span>
              ) : null}
            </p>
            <p className="mt-1 line-clamp-3 whitespace-pre-wrap text-xs leading-relaxed text-muted-foreground">
              {latest.bodyMd.replace(/[#*`>]/g, "").slice(0, 120)}
            </p>
            <div className="mt-2 flex items-center gap-2">
              <Link
                href="/announcements"
                onClick={dismiss}
                className="text-xs text-brand hover:underline"
              >
                查看完整内容 →
              </Link>
              <Button
                size="sm"
                variant="ghost"
                onClick={dismiss}
                className="h-6 px-2 text-xs text-muted-foreground"
              >
                知道了
              </Button>
            </div>
          </div>
          <button
            type="button"
            onClick={dismiss}
            aria-label="关闭公告"
            className="shrink-0 rounded p-1 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            <X className="size-4" aria-hidden />
          </button>
        </div>
      </div>
    </div>
  );
}
