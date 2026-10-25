"use client";

import { useEffect, useState } from "react";

/**
 * 当前设备是否支持"悬停"。
 *
 * 触摸屏上 mouseenter/mousemove 会在点击时以幽灵事件形式触发，
 * 导致卡片在手机上莫名其妙地倾斜。所以这类纯装饰的鼠标跟随效果要先问一句。
 *
 * 初始值用 false：服务端渲染时无从得知，先不启用，挂载后再判断。
 * 这样也不会出现"水合前后样式不一致"的警告。
 */
export function useHoverCapable() {
  const [canHover, setCanHover] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined" || !window.matchMedia) {
      return;
    }
    const query = window.matchMedia("(hover: hover) and (pointer: fine)");
    setCanHover(query.matches);

    const onChange = (event: MediaQueryListEvent) => setCanHover(event.matches);
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);

  return canHover;
}
