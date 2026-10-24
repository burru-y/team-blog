import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /**
   * 容器部署用 standalone：产物只包含运行必需的文件（含最小 node_modules），
   * 镜像从 ~1GB 降到 ~200MB。Dockerfile 直接拷 .next/standalone。
   */
  output: "standalone",

  async rewrites() {
    return [
      {
        source: "/api/:path*",
        // 本地开发默认直连 8080；容器里由 compose 注入 http://api:8080
        destination: `${process.env.API_ORIGIN ?? "http://127.0.0.1:8080"}/api/:path*`,
      },
      {
        // AI 服务：开发环境经 nginx 容器（8088）转发，生产环境由 nginx 直接路由，
        // 这条 rewrite 在容器里其实用不到——但 pnpm dev 时没有它就跑不通
        source: "/ai/:path*",
        destination: `${process.env.AI_ORIGIN ?? "http://127.0.0.1:8088"}/ai/:path*`,
      },
    ];
  },
};

export default nextConfig;
