"use client";

import { motion, type Transition } from "motion/react";

import { cn } from "@/lib/utils";

/**
 * 液态高亮容器。
 *
 * 滑块（LiquidItem）用 layout 动画在若干位置间移动，父层加一层模糊把边缘"化开"，
 * 看起来像液体而不是一个硬边方块。
 *
 * blur/contrast 用 CSS filter 实现：比 SVG 滤镜便宜，且不打断 layout 动画。
 */
export function Liquid({
  blur = 6,
  contrast = 18,
  fill = "var(--background)",
  className,
  children,
}: {
  blur?: number;
  contrast?: number;
  fill?: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className={cn("relative", className)}
      style={{
        filter: `blur(${blur}px) contrast(${100 + contrast}%)`,
        background: fill,
      }}
    >
      {children}
    </div>
  );
}

/** 液态容器里的可选块：位置变化时用 layout 动画滑过去。 */
export function LiquidItem({
  x,
  y,
  width,
  height,
  radius = 12,
  transition,
  className,
  children,
}: {
  x: number;
  y: number;
  width: number;
  height: number;
  radius?: number;
  transition?: Transition;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <motion.div
      className={cn("absolute", className)}
      animate={{ x, y, width, height, borderRadius: radius }}
      transition={transition ?? { type: "spring", stiffness: 380, damping: 34 }}
    >
      {children}
    </motion.div>
  );
}

export type LiquidTransition = Transition;
