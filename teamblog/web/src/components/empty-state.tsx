import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

/**
 * 空状态。
 *
 * 不用 Card 包一层：空状态出现时页面本来就没内容，再加一个带边框的盒子
 * 会让"什么都没有"显得更重。用居中的说明文字加一个可选操作即可。
 */
export function EmptyState({
  title,
  description,
  action,
  className,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed px-6 py-14 text-center",
        className,
      )}
    >
      <p className="text-sm font-medium">{title}</p>
      {description ? (
        <p className="max-w-sm text-xs leading-relaxed text-muted-foreground">{description}</p>
      ) : null}
      {action ? <div className="pt-2">{action}</div> : null}
    </div>
  );
}
