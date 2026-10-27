"use client";

import { motion, useReducedMotion } from "motion/react";
import { useState } from "react";

import { cn } from "@/lib/utils";

/**
 * 菜单高亮滑动。
 *
 * 高亮块用一个绝对定位的 div，跟着 hover/focus 的行移动，
 * 行本身不加背景色——否则移动时会出现"两块同时亮"的重叠。
 */
export function GlideMenu({
  children,
  className,
  highlightClassName,
}: {
  children: React.ReactNode;
  className?: string;
  highlightClassName?: string;
}) {
  const reduce = useReducedMotion();
  const [box, setBox] = useState<{ top: number; height: number } | null>(null);

  function moveTo(event: React.PointerEvent<HTMLDivElement> | React.FocusEvent<HTMLDivElement>) {
    const row = (event.target as HTMLElement).closest<HTMLElement>("[data-menu-row]");
    const container = event.currentTarget;
    if (!row || !container.contains(row)) {
      return;
    }
    const rowRect = row.getBoundingClientRect();
    const containerRect = container.getBoundingClientRect();
    setBox({ top: rowRect.top - containerRect.top, height: rowRect.height });
  }

  return (
    <div
      className={cn("relative", className)}
      onPointerMove={moveTo}
      onFocus={moveTo}
      onPointerLeave={() => setBox(null)}
      onBlur={() => setBox(null)}
    >
      {box ? (
        <motion.div
          aria-hidden
          className={cn("pointer-events-none absolute left-0 w-full rounded-lg bg-muted", highlightClassName)}
          initial={false}
          animate={{ y: box.top, height: box.height }}
          transition={reduce ? { duration: 0 } : { type: "spring", stiffness: 500, damping: 40 }}
          style={{ top: 0 }}
        />
      ) : null}
      <div className="relative">{children}</div>
    </div>
  );
}

export default GlideMenu;
