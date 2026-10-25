"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

/**
 * 全站状态公告。
 *
 * 只放"当前确实如此、且影响访问者预期"的信息。已经过时的那几条
 * （前端为基础实现、尚未部署服务器）在站点上线后删掉了——
 * 公告写着已完成的事，比没有公告更糟。
 *
 * 为什么可关闭：老用户每页都看到同一条横幅会烦。关闭状态记在 localStorage。
 *
 * 注意：改成 v2 是为了让之前点过「知道了」的人重新看到新公告，
 * 否则他们永远不会知道内容变了。旧 key 不删也没关系——不再被读取。
 */
const DISMISS_KEY = "teamblog.status-banner.v2.dismissed";

export function StatusBanner() {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    try {
      if (window.localStorage.getItem(DISMISS_KEY) === "1") {
        setVisible(false);
      }
    } catch {
      // 隐私模式下 localStorage 可能抛异常，忽略即可
    }
  }, []);

  function dismiss() {
    setVisible(false);
    try {
      window.localStorage.setItem(DISMISS_KEY, "1");
    } catch {
      /* 同上 */
    }
  }

  if (!visible) {
    return null;
  }

  return (
    <div className="border-b bg-amber-500/10">
      <div className="mx-auto flex w-full max-w-5xl items-start gap-3 px-4 py-2.5 text-xs leading-relaxed text-amber-900 dark:text-amber-200">
        <div className="flex-1 space-y-1">
          <p>
            <span className="font-medium">一期内测中</span>
            ：功能已可正常使用，界面仍在打磨。遇到问题或有想法，欢迎
            <Link href="/feedback" className="mx-1 underline underline-offset-4">
              留言反馈
            </Link>
            。
          </p>
          <p>
            <span className="font-medium">地址暂为 IP，暂无 HTTPS</span>
            ：正式域名正在备案（约 1 至 2 周），通过后换成
            <span className="mx-1 font-medium">https://</span>
            访问。当前地址可以正常浏览，但浏览器会提示「不安全」。
          </p>
        </div>

        <button
          type="button"
          onClick={dismiss}
          className="shrink-0 rounded px-2 py-0.5 text-amber-900/70 hover:bg-amber-500/20 hover:text-amber-900 dark:text-amber-200/70 dark:hover:text-amber-100"
          aria-label="关闭公告"
        >
          知道了
        </button>
      </div>
    </div>
  );
}
