"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

/**
 * 全站状态公告：前端完成度与部署状态。
 *
 * 为什么要挂在页面顶部而不是只写进页脚：这两条会影响访问者的预期
 * （"页面怎么这么朴素""为什么没人能注册"），放在需要滚动才能看到的位置等于没说。
 *
 * 为什么可关闭：老用户每页都看到同一条横幅会烦。关闭状态记在 localStorage，
 * 换浏览器或清缓存后会重新出现——这是有意的，"没部署"这件事值得反复提醒。
 */
const DISMISS_KEY = "teamblog.status-banner.dismissed";

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
            <span className="font-medium">前端为基础实现</span>
            ：当前页面只做了功能验证，未做完整视觉设计。对页面有想法欢迎
            <Link href="/feedback" className="mx-1 underline underline-offset-4">
              提交反馈
            </Link>
            。
          </p>
          <p>
            <span className="font-medium">站点名称待定</span>
            ：当前暂用「TeamBlog」，正式名称确定后统一替换；如果你有建议，欢迎一并反馈。
          </p>
          <p>
            <span className="font-medium">尚未部署服务器</span>
            ：站点目前运行在作者本机，通过临时隧道对外提供；正式上线需完成 ICP 备案，
            预计 1 至 2 周。
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
