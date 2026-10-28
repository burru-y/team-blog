"use client";

import { useRef, type ReactNode } from "react";
import {
  motion,
  useMotionTemplate,
  useMotionValue,
  useReducedMotion,
  useSpring,
} from "motion/react";

import { SPRING_MOUSE } from "@/lib/ease";
import { useHoverCapable } from "@/lib/hooks/use-hover-capable";
import { cn } from "@/lib/utils";

/**
 * 跟随鼠标倾斜的卡片（beUI 的 tilt-card）。
 *
 * 三层判断决定要不要真的倾斜：
 *   1. 用户开了"减少动态效果" → 不倾斜
 *   2. 设备没有精确指针（触屏）→ 不倾斜，否则点击时会出现幽灵 hover
 *   3. 其余情况才挂 mouse 事件，避免在手机上白白绑定监听
 *
 * 倾斜角度上限 12°，反光用径向渐变跟随光标，透明度压到 15%：
 * 再高就会盖住卡片上的文字。
 */
export interface TiltCardProps {
  children: ReactNode;
  /** 最大倾斜角度 */
  max?: number;
  /** 是否显示跟随光标的反光 */
  glare?: boolean;
  /**
   * 反光颜色。默认取 --foreground（浅色下是黑、深色下是白），
   * 传了名片强调色后，跟随光标的那团光就是本人选的色。
   */
  glowColor?: string;
  className?: string;
}

export function TiltCard({
  children,
  max = 12,
  glare = true,
  glowColor = "var(--foreground)",
  className,
}: TiltCardProps) {
  const ref = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();
  const canHover = useHoverCapable();
  const enabled = !reduce && canHover;

  const rx = useMotionValue(0);
  const ry = useMotionValue(0);
  const gx = useMotionValue(50);
  const gy = useMotionValue(50);

  const srx = useSpring(rx, SPRING_MOUSE);
  const sry = useSpring(ry, SPRING_MOUSE);

  function onMove(event: React.MouseEvent<HTMLDivElement>) {
    const el = ref.current;
    if (!el || !enabled) return;
    const rect = el.getBoundingClientRect();
    const px = (event.clientX - rect.left) / rect.width;
    const py = (event.clientY - rect.top) / rect.height;
    ry.set((px - 0.5) * max);
    rx.set((0.5 - py) * max);
    gx.set(px * 100);
    gy.set(py * 100);
  }

  function onLeave() {
    rx.set(0);
    ry.set(0);
  }

  const transform = useMotionTemplate`perspective(1000px) rotateX(${srx}deg) rotateY(${sry}deg)`;
  const glareBg = useMotionTemplate`radial-gradient(circle at ${gx}% ${gy}%, ${glowColor}, transparent 50%)`;

  return (
    <motion.div
      ref={ref}
      onMouseMove={onMove}
      onMouseLeave={onLeave}
      style={{ transform, transformStyle: "preserve-3d" }}
      className={cn("relative overflow-hidden rounded-2xl will-change-transform", className)}
    >
      {children}
      {glare && enabled ? (
        <motion.div
          aria-hidden
          style={{ background: glareBg }}
          className="pointer-events-none absolute inset-0 opacity-20 blur-xl"
        />
      ) : null}
    </motion.div>
  );
}
