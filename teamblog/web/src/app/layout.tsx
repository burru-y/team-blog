import type { Metadata } from "next";
import "./globals.css";

import { SiteFooter } from "@/components/site-footer";
import { StatusBanner } from "@/components/status-banner";
import { SiteHeader } from "@/components/site-header";
import { SessionProvider } from "@/lib/session";
import { Toaster } from "@/components/ui/sonner";


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
        {/* SessionProvider 必须包住 Header 与页面：Header 要读登录态 */}
        <SessionProvider>
          <SiteHeader />
          <StatusBanner />
          <main className="mx-auto w-full max-w-6xl px-6 py-12">{children}</main>
          <SiteFooter />
          <Toaster position="top-center" />
        </SessionProvider>
      </body>
    </html>
  );
}
