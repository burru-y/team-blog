import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";

import { SiteFooter } from "@/components/site-footer";
import { StatusBanner } from "@/components/status-banner";
import { SiteHeader } from "@/components/site-header";
import { SessionProvider } from "@/lib/session";
import { Toaster } from "@/components/ui/sonner";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "TeamBlog · 团队技术博客",
  description: "团队的技术成果、学习记录与打卡热度（内测中）",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body className={`${geistSans.variable} ${geistMono.variable} antialiased`}>
        {/* SessionProvider 必须包住 Header 与页面：Header 要读登录态 */}
        <SessionProvider>
          <SiteHeader />
          <StatusBanner />
          <main className="mx-auto w-full max-w-5xl px-4 py-10">{children}</main>
          <SiteFooter />
          <Toaster position="top-center" />
        </SessionProvider>
      </body>
    </html>
  );
}
