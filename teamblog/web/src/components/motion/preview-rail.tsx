"use client";

import { useState } from "react";

import { cn } from "@/lib/utils";

/**
 * 消息列表右侧的缩略导航条。
 *
 * 消息多了以后光靠滚动找上下文很费劲，这里给一个"整段对话的缩略图"：
 * 每条消息对应一个小条，悬停看摘要，点击跳过去。
 *
 * 缩略图只画形状不画正文：窄条上放文字读不了，正文放在悬停卡片里。
 */
export type PreviewRailItem = {
  id: string;
  /** 相对长度：用来决定小条的高度，通常是字符数 */
  weight?: number;
  label?: string;
  /** 缩略提示文案，悬停卡片里展示 */
  description?: string;
  /** 读屏用的名称，缺省时按序号生成 */
  ariaLabel?: string;
  tone?: "user" | "assistant" | "system";
};

export function PreviewRail({
  items,
  label = "对话导航",
  activeId,
  onItemSelect,
  previewSide = "before",
  highlightActive = true,
  itemSize = 14,
  className,
  railClassName,
  previewContainerClassName,
  previewClassName,
  children,
}: {
  items: PreviewRailItem[];
  label?: string;
  activeId?: string;
  onItemSelect?: (item: PreviewRailItem) => void;
  previewSide?: "before" | "after";
  highlightActive?: boolean;
  itemSize?: number;
  className?: string;
  railClassName?: string;
  previewContainerClassName?: string;
  previewClassName?: string;
  /** 自定义预览内容，缺省用 label/description */
  children?: React.ReactNode;
}) {
  const [hovered, setHovered] = useState<PreviewRailItem | null>(null);

  if (items.length === 0) {
    return null;
  }

  const maxWeight = Math.max(...items.map((item) => item.weight ?? 1), 1);

  return (
    <nav
      aria-label={label}
      className={cn(
        "relative flex flex-col items-center gap-1",
        previewSide === "after" ? "order-last" : "order-first",
        className,
      )}
      onPointerLeave={() => setHovered(null)}
    >
      <div className={cn("flex flex-col items-center gap-1", railClassName)}>
        {items.map((item, index) => {
          const ratio = Math.max(0.2, (item.weight ?? 1) / maxWeight);
          const active = highlightActive && item.id === activeId;
          return (
            <button
              key={item.id}
              type="button"
              aria-label={item.ariaLabel ?? item.label ?? `跳到第 ${index + 1} 条`}
              aria-current={active ? "true" : undefined}
              onPointerEnter={() => setHovered(item)}
              onFocus={() => setHovered(item)}
              onClick={() => onItemSelect?.(item)}
              className={cn(
                "w-5 rounded-full transition-all duration-200 hover:opacity-100",
                active
                  ? "bg-brand/70 opacity-100"
                  : item.tone === "user"
                    ? "bg-muted-foreground/40 opacity-60"
                    : "bg-muted-foreground/25 opacity-50",
              )}
              style={{ height: `${Math.round(itemSize * 0.4 + ratio * itemSize * 1.6)}px` }}
            />
          );
        })}
      </div>

      {hovered ? (
        <div
          className={cn(
            "pointer-events-none absolute top-0 z-20 hidden md:block",
            previewContainerClassName,
          )}
        >
          <div className={cn("rounded-lg border bg-popover p-2 text-xs shadow-md", previewClassName)}>
            {children ?? (
              <p data-slot="preview-rail-title" className="line-clamp-1 font-medium">
                {hovered.label ?? "消息"}
              </p>
            )}
            {hovered.description ? (
              <p
                data-slot="preview-rail-description"
                className="mt-1 line-clamp-2 text-muted-foreground"
              >
                {hovered.description}
              </p>
            ) : null}
          </div>
        </div>
      ) : null}
    </nav>
  );
}
