import type { NextConfig } from "next";

/**
 * 为什么用 rewrite 而不是让前端直连 http://localhost:8080：
 * 同源之后没有跨域、没有预检，httpOnly 的 refresh Cookie 也能自动带上。
 * 生产环境由 Nginx 做同样的反代（见 03 实施方案），前端代码一行都不用改。
 */
const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.API_ORIGIN ?? "http://127.0.0.1:8080"}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
