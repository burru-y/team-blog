"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

/**
 * 发布页的两个入口切换。
 *
 * 为什么需要这个组件：`/publish` 只放成果表单，`/publish/learning` 原本没有任何
 * 入口链接，只能手输地址。两个页面顶部各放一条切换栏，从任一处都能到另一处。
 */
const TABS = [
  { href: "/publish", label: "发布成果", hint: "项目、证书、奖项" },
  { href: "/publish/learning", label: "写学习笔记", hint: "笔记、代码、图片" },
];

export function PublishTabs() {
  const pathname = usePathname();

  return (
    <div className="flex flex-wrap gap-2 rounded-lg border bg-muted/30 p-1">
      {TABS.map((tab) => {
        const active = pathname === tab.href;
        return (
          <Link
            key={tab.href}
            href={tab.href}
            className={[
              "flex-1 rounded-md px-3 py-2 text-sm transition-colors",
              active
                ? "bg-background font-medium shadow-sm"
                : "text-muted-foreground hover:text-foreground",
            ].join(" ")}
          >
            <span className="block">{tab.label}</span>
            <span className="block text-xs text-muted-foreground">{tab.hint}</span>
          </Link>
        );
      })}
    </div>
  );
}
