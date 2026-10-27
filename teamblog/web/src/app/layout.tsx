import type { Metadata } from "next";
import "./globals.css";

import { ThemeProvider } from "@/components/theme-provider";
import { AppShell } from "@/components/app-shell";
import { SiteFooter } from "@/components/site-footer";
import { StatusBanner } from "@/components/status-banner";
import { Toaster } from "@/components/ui/sonner";
import { SessionProvider } from "@/lib/session";

export const metadata: Metadata = {
  title: "欲晓 · 团队技术博客",
  description: "东方欲晓，莫道君行早。团队的技术成果、学习记录与打卡热度（内测中）",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className="antialiased">
        {/* SessionProvider 必须包住外壳与页面：侧栏要读登录态决定显示哪些入口 */}
        {/* ThemeProvider 要在最外层：深色类名必须挂在 html 上，越晚挂越容易闪 */}
        <ThemeProvider>
          <SessionProvider>
          <AppShell>
            <StatusBanner />
            {children}
            <SiteFooter />
          </AppShell>
            <Toaster position="top-center" />
          </SessionProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
