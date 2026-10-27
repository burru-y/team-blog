"use client";

import { AnimatePresence, motion, useReducedMotion } from "motion/react";

import { SPRING_SWAP } from "@/lib/ease";
import { cn } from "@/lib/utils";

/**
 * 状态切换时的图标交换：旧图标淡出下移，新图标淡入上移。
 *
 * 用 AnimatePresence + key 触发，不手动管时序——
 * 手写 timeout 在快速连点时容易错位。
 */
export function ActionSwapIcon({
  value,
  animation = "slide",
  className,
  children,
}: {
  value: string;
  animation?: "slide" | "blur" | "scale";
  className?: string;
  children: React.ReactNode;
}) {
  const reduce = useReducedMotion();

  const variants =
    animation === "blur"
      ? {
          initial: { opacity: 0, filter: "blur(4px)" },
          animate: { opacity: 1, filter: "blur(0px)" },
          exit: { opacity: 0, filter: "blur(4px)" },
        }
      : animation === "scale"
        ? {
            initial: { opacity: 0, scale: 0.7 },
            animate: { opacity: 1, scale: 1 },
            exit: { opacity: 0, scale: 0.7 },
          }
        : {
            initial: { opacity: 0, y: 6 },
            animate: { opacity: 1, y: 0 },
            exit: { opacity: 0, y: -6 },
          };

  return (
    <span className={cn("relative grid place-items-center", className)} aria-hidden>
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span
          key={value}
          initial={reduce ? false : variants.initial}
          animate={variants.animate}
          exit={reduce ? undefined : variants.exit}
          transition={SPRING_SWAP}
          className={cn("grid place-items-center", className)}
        >
          {children}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}
