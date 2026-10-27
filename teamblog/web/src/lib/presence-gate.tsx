"use client";

import { useCallback, useEffect, useState, type CSSProperties, type ReactNode } from "react";

/**
 * 退出动画的挂载门控。
 *
 * AnimatePresence 只在子树被移除时播放 exit，但有些组件需要在退场期间继续
 * 保持可访问性状态（aria-hidden）和布局占位。这里把"是否还该渲染"抽出来，
 * 由子组件自己决定怎么用。
 *
 * 用法：
 * ```tsx
 * <PresenceGate>{(gate, isPresent) => <motion.div {...gate} />}</PresenceGate>
 * ```
 */
export type PresenceGateRenderProps = {
  gate: {
    onAnimationStart?: () => void;
    onAnimationEnd?: () => void;
    style: CSSProperties;
    "data-present"?: string;
  };
  isPresent: boolean;
};

export function PresenceGate({
  children,
}: {
  children: (props: PresenceGateRenderProps) => ReactNode;
}) {
  const [isPresent, setPresent] = useState(true);

  const onAnimationStart = useCallback(() => setPresent(true), []);
  const onAnimationEnd = useCallback(() => setPresent(true), []);

  return (
    <>
      {children({
        gate: {
          onAnimationStart,
          onAnimationEnd,
          style: {},
          "data-present": isPresent ? "true" : "false",
        },
        isPresent,
      })}
    </>
  );
}

/** 常用的"延迟卸载"：让节点在关闭后多留 durationMs，用于手写退场动画。 */
export function useDelayedUnmount(open: boolean, durationMs = 200) {
  const [mounted, setMounted] = useState(open);

  useEffect(() => {
    if (open) {
      setMounted(true);
      return;
    }
    const timer = window.setTimeout(() => setMounted(false), durationMs);
    return () => window.clearTimeout(timer);
  }, [open, durationMs]);

  return mounted;
}
