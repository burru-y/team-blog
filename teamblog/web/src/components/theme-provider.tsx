"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";

/**
 * 主题上下文。
 *
 * attribute="class" 对应 globals.css 里的 `.dark` 变量块；
 * disableTransitionOnChange 关掉切换瞬间所有元素的颜色过渡——
 * 否则整页几百个元素同时做 150ms 过渡，看起来像卡了一下。
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
      storageKey="teamblog.theme"
    >
      {children}
    </NextThemesProvider>
  );
}
