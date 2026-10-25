import type { Metadata } from "next";
import "./globals.css";

import { AppShell } from "@/components/app-shell";
import { SiteFooter } from "@/components/site-footer";
import { StatusBanner } from "@/components/status-banner";
import { Toaster } from "@/components/ui/sonner";
import { SessionProvider } from "@/lib/session";

export const metadata: Metadata = {
  title: "TeamBlog（名称待定）· 团队技术博客",
  description: "团队的技术成果、学习记录与打卡热度（内测中）",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body className="antialiased">
        {/* SessionProvider 必须包住外壳与页面：侧栏要读登录态决定显示哪些入口 */}
        <SessionProvider>
          <AppShell>
            <StatusBanner />
            {children}
            <SiteFooter />
          </AppShell>
          <Toaster position="top-center" />
        </SessionProvider>
      </body>
    </html>
  );
}
