"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { NotificationBell } from "@/components/motion/notification-bell";
import { api } from "@/lib/api";
import type { Announcement } from "@/lib/types";

/**
 * 顶栏的公告铃铛：显示"没看过的公告"数量。
 *
 * 与进站弹窗共用同一个已读标记（`teamblog.announcement.seen` 存的是最新一条的 id）：
 * 弹过之后铃铛归零，不会出现"弹窗关了但铃铛还在响"的矛盾。
 */
const SEEN_KEY = "teamblog.announcement.seen";

export function AnnouncementBell() {
  const [count, setCount] = useState(0);

  useEffect(() => {
    let alive = true;

    api
      .listAnnouncements()
      .then((list: Announcement[]) => {
        if (!alive || list.length === 0) {
          return;
        }
        let seen: string | null = null;
        try {
          seen = window.localStorage.getItem(SEEN_KEY);
        } catch {
          /* 隐私模式读不到，按未读处理 */
        }
        // 以"已读的那条"为界，比它新的都算未读
        const seenIndex = seen ? list.findIndex((item) => item.id === seen) : -1;
        const unread = seenIndex === -1 ? list.length : seenIndex;
        setCount(Math.max(0, unread));
      })
      .catch(() => {
        // 公告取不到就不显示角标，不影响顶栏
      });

    return () => {
      alive = false;
    };
  }, []);

  return (
    <Link
      href="/announcements"
      aria-label={count > 0 ? `公告，${count} 条未读` : "公告"}
      className="inline-flex items-center justify-center rounded-lg p-1 transition-colors hover:bg-muted"
    >
      <NotificationBell count={count} size={22} variant="count" color="red" />
    </Link>
  );
}
