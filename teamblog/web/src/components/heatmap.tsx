import type { HeatmapResponse } from "@/lib/types";

/**
 * 打卡热力图（纯展示，服务端组件）。
 *
 * <p>格子按"周"排：每列 7 天，第一列用空白补齐，这样同一行永远是同一个星期几——
 * 否则格子会随起始日期错位，看起来像乱码。
 *
 * <p>颜色分 4 档而不是连续渐变：人眼分不清相邻色阶，4 档足够看出"最近很勤"还是"断了两周"。
 */
export function Heatmap({
  data,
  emptyLabel = "这一天没有记录",
}: {
  data: HeatmapResponse;
  emptyLabel?: string;
}) {
  const cells = buildCells(data);
  const filled = cells.filter((cell) => cell.value > 0).length;

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1">
        {cells.map((cell, index) =>
          cell.date === null ? (
            <div key={`pad-${index}`} className="size-3 rounded-sm bg-transparent" />
          ) : (
            <div
              key={cell.date}
              title={`${cell.date}：${cell.value > 0 ? cell.value : emptyLabel}`}
              className={`size-3 rounded-sm ${colorOf(cell.value)}`}
            />
          ),
        )}
      </div>

      <div className="flex items-center gap-3 text-xs text-muted-foreground">
        <span>共 {filled} 天有记录</span>
        <span className="flex items-center gap-1">
          少
          <span className="size-3 rounded-sm bg-muted" />
          <span className="size-3 rounded-sm bg-emerald-200" />
          <span className="size-3 rounded-sm bg-emerald-400" />
          <span className="size-3 rounded-sm bg-emerald-600" />
          多
        </span>
      </div>
    </div>
  );
}

type Cell = { date: string | null; value: number };

function buildCells(data: HeatmapResponse): Cell[] {
  const start = new Date(`${data.from}T00:00:00Z`);
  const end = new Date(`${data.to}T00:00:00Z`);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || start > end) {
    return [];
  }

  const cells: Cell[] = [];
  // 周一作为每列第一天：JS 的 getUTCDay() 里周日是 0，换算成 0..6（周一=0）
  const leading = (start.getUTCDay() + 6) % 7;
  for (let i = 0; i < leading; i++) {
    cells.push({ date: null, value: 0 });
  }

  for (let cursor = new Date(start); cursor <= end; cursor.setUTCDate(cursor.getUTCDate() + 1)) {
    const key = cursor.toISOString().slice(0, 10);
    cells.push({ date: key, value: data.days[key] ?? 0 });
  }
  return cells;
}

function colorOf(value: number): string {
  if (value <= 0) {
    return "bg-muted";
  }
  if (value === 1) {
    return "bg-emerald-200";
  }
  if (value <= 3) {
    return "bg-emerald-400";
  }
  return "bg-emerald-600";
}
