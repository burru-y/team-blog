import type { Contribution, ContributionLevel } from "@/components/activity-card";
import type { HeatmapResponse } from "@/lib/types";

/**
 * 把后端的 `days: Record<date, count>` 补齐成连续日期序列，并换算成 0–4 档。
 *
 * 为什么要补齐：接口只返回有记录的日子，而格子图需要每一天都在，
 * 缺的那天要画成空格子，否则整张图会错位。
 *
 * 分档为什么是 0/1/2/3/4 而不是按最大值等比：人眼分不清相邻色阶，
 * 固定阈值更稳定，也不会因为某天打了 10 次卡就把其余日子都压成最低档。
 */
export function toContributions(data: HeatmapResponse): Contribution[] {
  const days = data.days ?? {};
  const start = new Date(`${data.from}T00:00:00Z`);
  const end = new Date(`${data.to}T00:00:00Z`);

  // 起点对齐到周日：格子按列排，每列必须是同一周的七天
  while (start.getUTCDay() !== 0) {
    start.setUTCDate(start.getUTCDate() - 1);
  }

  const result: Contribution[] = [];
  const cursor = new Date(start);
  while (cursor <= end) {
    const date = cursor.toISOString().slice(0, 10);
    const count = days[date] ?? 0;
    result.push({ date, count, level: levelOf(count) });
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return result;
}

function levelOf(count: number): ContributionLevel {
  if (count <= 0) return 0;
  if (count === 1) return 1;
  if (count === 2) return 2;
  if (count === 3) return 3;
  return 4;
}
