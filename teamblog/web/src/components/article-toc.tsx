"use client";

import { useEffect, useState } from "react";

import type { TocItem } from "@/lib/markdown-outline";
import { cn } from "@/lib/utils";

/**
 * 文章目录（右侧粘性导航）。
 *
 * 用 IntersectionObserver 高亮当前段落，而不是监听 scroll：
 * scroll 事件每帧都会触发，算出「当前在哪一节」还要遍历所有标题；
 * 观察者只在标题进出视口时回调，代价小得多。
 */
export function ArticleToc({ items }: { items: TocItem[] }) {
  const [activeId, setActiveId] = useState<string>(items[0]?.id ?? "");

  useEffect(() => {
    if (items.length === 0) {
      return;
    }

    const elements = items
      .map((item) => document.getElementById(item.id))
      .filter((el): el is HTMLElement => Boolean(el));

    if (elements.length === 0) {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        // 取第一个进入视口的标题，避免多个同时可见时来回跳
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) {
          setActiveId(visible[0].target.id);
        }
      },
      // 顶部留出导航高度，底部留出大半屏，让"当前段落"更贴近视觉
      { rootMargin: "-80px 0px -70% 0px", threshold: 0 },
    );

    elements.forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, [items]);

  if (items.length === 0) {
    return null;
  }

  return (
    <nav aria-label="文章目录" className="text-sm">
      <p className="mb-2 text-xs font-medium text-muted-foreground">目录</p>
      <ul className="space-y-1 border-l">
        {items.map((item) => (
          <li key={item.id}>
            <a
              href={`#${item.id}`}
              onClick={() => setActiveId(item.id)}
              className={cn(
                "-ml-px block border-l-2 py-1 leading-snug transition-colors",
                item.level === 3 ? "pl-6 text-xs" : "pl-3",
                activeId === item.id
                  ? "border-brand text-foreground"
                  : "border-transparent text-muted-foreground hover:text-foreground",
              )}
            >
              {item.text}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
