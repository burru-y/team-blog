"use client";

import { useEffect, type RefObject } from "react";

/**
 * 点击外部或按 Esc 关闭弹层。
 *
 * ignore 用来放行触发按钮：点触发器时应该由触发器自己切换开关，
 * 否则会先被这里关一次、再被触发器打开，出现"点了没反应"。
 */
export function useDismiss(
  open: boolean,
  onDismiss: () => void,
  ref: RefObject<HTMLElement | null>,
  options: { ignore?: (target: Node) => boolean } = {},
) {
  useEffect(() => {
    if (!open) {
      return;
    }

    function handlePointerDown(event: PointerEvent) {
      const target = event.target as Node | null;
      if (!target) {
        return;
      }
      if (ref.current?.contains(target) || options.ignore?.(target)) {
        return;
      }
      onDismiss();
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onDismiss();
      }
    }

    document.addEventListener("pointerdown", handlePointerDown, true);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown, true);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, onDismiss, ref, options]);
}
