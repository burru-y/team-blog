"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import {
  Alert,
  AlertClose,
  AlertContent,
  AlertDescription,
  AlertIcon,
  AlertTitle,
} from "@/components/motion/animated-alert";
import { Info } from "lucide-react";

/**
 * 全站状态公告。
 *
 * 只放"当前确实如此、且影响访问者预期"的信息。已经过时的那几条
 * （前端为基础实现、尚未部署服务器）在站点上线后删掉了——
 * 公告写着已完成的事，比没有公告更糟。
 *
 * 动效用的是通用 Alert：进出场、位置变化、关闭按钮都由它统一处理，
 * 这里只负责文案与"记住已关闭"。
 *
 * 注意：key 改成 v2 是为了让之前点过「知道了」的人重新看到新公告。
 */
const DISMISS_KEY = "teamblog.status-banner.v2.dismissed";

export function StatusBanner() {
  const [open, setOpen] = useState(true);

  useEffect(() => {
    try {
      if (window.localStorage.getItem(DISMISS_KEY) === "1") {
        setOpen(false);
      }
    } catch {
      // 隐私模式下 localStorage 可能抛异常，忽略即可
    }
  }, []);

  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (!next) {
      try {
        window.localStorage.setItem(DISMISS_KEY, "1");
      } catch {
        /* 同上 */
      }
    }
  }

  return (
    <div className="border-b">
      <div className="mx-auto w-full max-w-5xl px-4 py-2">
        <Alert
          variant="warning"
          open={open}
          onOpenChange={handleOpenChange}
          className="border-amber-500/30 text-xs"
        >
          <AlertIcon>
            <Info className="size-4" aria-hidden />
          </AlertIcon>
          <AlertContent>
            <AlertTitle>一期内测中</AlertTitle>
            <AlertDescription>
              功能已可正常使用，界面仍在打磨。遇到问题或有想法，欢迎
              <Link href="/feedback" className="mx-1 underline underline-offset-4">
                留言反馈
              </Link>
              。
            </AlertDescription>
            <AlertDescription>
              <span className="font-medium">地址暂为 IP，暂无 HTTPS</span>
              ：正式域名正在备案（约 1 至 2 周），通过后换成 https:// 访问。
            </AlertDescription>
          </AlertContent>
          <AlertClose aria-label="关闭公告" />
        </Alert>
      </div>
    </div>
  );
}
