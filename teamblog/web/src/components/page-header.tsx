import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

/**
 * 页面标题区。
 *
 * 全站统一这一处，避免每个页面各写一套字号和间距——
 * 之前标题从 text-2xl 到 text-5xl 有五种，页面之间看起来不像同一个站。
 */
export function PageHeader({
  title,
  description,
  actions,
  meta,
  size = "default",
  className,
}: {
  title: string;
  description?: string;
  /** 右侧操作区，如按钮组 */
  actions?: ReactNode;
  /** 标题上方的小字，如分类或返回链接 */
  meta?: ReactNode;
  size?: "default" | "large";
  className?: string;
}) {
  return (
    <header className={cn("space-y-3", className)}>
      {meta ? (
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">{meta}</div>
      ) : null}

      <div className="flex flex-wrap items-start justify-between gap-4">
        <h1
          className={cn(
            "font-semibold tracking-tight text-balance",
            size === "large"
              ? "text-3xl leading-tight sm:text-4xl"
              : "text-2xl leading-tight sm:text-3xl",
          )}
        >
          {title}
        </h1>
        {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
      </div>

      {description ? (
        <p className="max-w-2xl text-sm leading-relaxed text-muted-foreground">{description}</p>
      ) : null}
    </header>
  );
}
