import { Loader } from "@/components/motion/loader";

/**
 * 路由级加载态。
 *
 * 用骨架块而不是整页转圈：转圈只告诉用户"在等"，骨架能告诉用户"等的是什么"。
 * 只在需要等待较久的列表页出现，因此这里给的是通用的三行占位。
 */
export default function Loading() {
  return (
    <div className="space-y-6" role="status" aria-label="页面加载中">
      <div className="flex items-center gap-3">
        <Loader variant="bars" size={18} label="加载中" />
        <div className="h-6 w-40 animate-pulse rounded bg-muted" />
      </div>

      <div className="space-y-3">
        {[0, 1, 2].map((row) => (
          <div key={row} className="space-y-2 rounded-xl border p-4">
            <div className="h-4 w-2/3 animate-pulse rounded bg-muted" />
            <div className="h-3 w-full animate-pulse rounded bg-muted/70" />
            <div className="h-3 w-4/5 animate-pulse rounded bg-muted/70" />
          </div>
        ))}
      </div>
    </div>
  );
}
