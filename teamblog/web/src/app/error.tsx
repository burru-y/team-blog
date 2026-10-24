"use client";

import { useEffect } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

/**
 * 路由级错误态（T-14）。
 *
 * 最常见的原因是后端没起来——`lib/api.ts` 会把网络错误转成 `NETWORK` 的 ApiError，
 * 这里给出一条能行动的提示，而不是把堆栈甩给用户（S-09）。
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // 真实项目里这里上报到监控；本地开发直接看控制台
    console.error(error);
  }, [error]);

  return (
    <div className="mx-auto max-w-lg space-y-4 py-16">
      <Alert variant="destructive">
        <AlertTitle>页面没能加载出来</AlertTitle>
        <AlertDescription>
          可能是后端没有启动，或者网络不通。确认 8080 端口的服务在跑之后再重试。
        </AlertDescription>
      </Alert>
      <div className="flex gap-3">
        <Button onClick={reset}>重试</Button>
        <Button variant="outline" onClick={() => window.location.reload()}>
          刷新页面
        </Button>
      </div>
    </div>
  );
}
