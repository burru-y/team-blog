"use client";

import * as React from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";

import { cn } from "@/lib/utils";

/**
 * 活跃度格子（打卡热力图）。
 *
 * 视觉来自社区的 GitHub 活跃度卡片，但**不连 GitHub、不请求任何第三方接口**：
 * 数据由调用方以 `contributions` 传入，来源是我们自己的打卡记录。
 *
 * 与旧版 Heatmap 的区别：按周分列、入场逐列淡入、悬停有 tooltip、颜色跟品牌色。
 * 旧的 `Heatmap` 保留在团队首页，这个用在其个人主页。
 */

export type ContributionLevel = 0 | 1 | 2 | 3 | 4;

export type Contribution = {
  /** YYYY-MM-DD */
  date: string;
  count: number;
  level: ContributionLevel;
};

const DEFAULT_CELL_SIZE = 11;
const DEFAULT_MONTHS = 12;
const WEEKS_PER_MONTH = 365.25 / 12 / 7;
const MIN_LABEL_WEEKS = 3;
const CARD_PADDING = 32;
const COLUMN_STAGGER = 0.012;
const CELL_FADE = { duration: 0.2, ease: [0.22, 1, 0.36, 1] as const };
const LABEL_BLUR = 6;
const LABEL_REVEAL = { duration: 0.45, ease: [0.22, 1, 0.36, 1] as const };
const TOOLTIP_EDGE = 8;

const LEVELS = [0, 1, 2, 3, 4] as const;
const LEVEL_OPACITY: Record<ContributionLevel, number> = {
  0: 0,
  1: 0.3,
  2: 0.52,
  3: 0.76,
  4: 1,
};

const MONTH_NAMES = [
  "1月", "2月", "3月", "4月", "5月", "6月",
  "7月", "8月", "9月", "10月", "11月", "12月",
];

const gapFor = (cellSize: number) => Math.max(2, Math.round(cellSize / 4));
const weeksFor = (months: number) => Math.max(1, Math.ceil(months * WEEKS_PER_MONTH));

const useIsoLayoutEffect =
  typeof window !== "undefined" ? React.useLayoutEffect : React.useEffect;

function toMonthLabels(weeks: Contribution[][]) {
  const labels: (string | null)[] = weeks.map(() => null);
  const monthAt = (index: number) => weeks[index]?.[0]?.date.slice(5, 7);

  let start = 0;
  for (let i = 1; i <= weeks.length; i++) {
    if (i < weeks.length && monthAt(i) === monthAt(start)) continue;
    // 太短的月份段放不下标签，会和下一个重叠，直接不画
    if (i - start >= MIN_LABEL_WEEKS) {
      labels[start] = MONTH_NAMES[Number(monthAt(start)) - 1] ?? null;
    }
    start = i;
  }
  return labels;
}

/** 单一颜色 + 不同透明度，避免为 4 个色阶各定义一个变量。 */
function toScale(accent: string) {
  return LEVELS.map((level) => ({
    backgroundColor: accent,
    opacity: LEVEL_OPACITY[level],
  }));
}

function toWeeks(contributions: Contribution[]) {
  const weeks: Contribution[][] = [];
  for (let i = 0; i < contributions.length; i += 7) {
    weeks.push(contributions.slice(i, i + 7));
  }
  return weeks;
}

function useFittedColumns(cellSize: number, gap: number) {
  const ref = React.useRef<HTMLDivElement>(null);
  const [columns, setColumns] = React.useState<number>();

  useIsoLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () =>
      setColumns(Math.max(1, Math.floor((el.clientWidth + gap) / (cellSize + gap))));
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [cellSize, gap]);

  return [ref, columns] as const;
}

type HoveredDay = { day: Contribution; x: number; y: number };

const DATE_FORMAT = new Intl.DateTimeFormat("zh-CN", {
  month: "long",
  day: "numeric",
  year: "numeric",
});

function describeDay({ count, date }: Contribution) {
  const text = DATE_FORMAT.format(new Date(`${date}T00:00:00`));
  return count > 0 ? `${text}：打卡 ${count} 次` : `${text}：没有打卡`;
}

function Tooltip({ hovered, reduceMotion }: { hovered: HoveredDay; reduceMotion: boolean | null }) {
  const ref = React.useRef<HTMLDivElement>(null);
  const [left, setLeft] = React.useState(hovered.x);

  useIsoLayoutEffect(() => {
    const half = (ref.current?.offsetWidth ?? 0) / 2;
    const edge = TOOLTIP_EDGE + half;
    setLeft(Math.min(Math.max(hovered.x, edge), window.innerWidth - edge));
  }, [hovered]);

  return createPortal(
    <div
      className="pointer-events-none fixed z-50"
      style={{ left, top: hovered.y, transform: "translate(-50%, calc(-100% - 8px))" }}
    >
      <motion.div
        ref={ref}
        className="whitespace-nowrap rounded-lg bg-foreground px-2 py-1 text-xs font-medium text-background shadow-md"
        initial={reduceMotion ? false : { opacity: 0, scale: 0.94 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={reduceMotion ? { opacity: 0 } : { opacity: 0, scale: 0.94 }}
        transition={reduceMotion ? { duration: 0 } : { duration: 0.14 }}
      >
        {describeDay(hovered.day)}
      </motion.div>
    </div>,
    document.body,
  );
}

function ContributionGrid({
  contributions,
  scale,
  cellSize,
  months,
  showMonths,
  label,
  reduceMotion,
}: {
  contributions: Contribution[];
  scale: { backgroundColor: string; opacity: number }[];
  cellSize: number;
  months: number;
  showMonths: boolean;
  label: string;
  reduceMotion: boolean | null;
}) {
  const weeks = React.useMemo(() => toWeeks(contributions), [contributions]);
  const gap = gapFor(cellSize);
  const [ref, columns] = useFittedColumns(cellSize, gap);
  const [hovered, setHovered] = React.useState<HoveredDay>();

  const cap = Math.min(weeks.length, weeksFor(months));
  const visible = weeks.slice(-Math.min(cap, columns ?? cap));
  const sweepEnd = (visible.length - 1) * COLUMN_STAGGER + CELL_FADE.duration;

  const hover = (day: Contribution) => (event: React.PointerEvent) => {
    const cell = event.currentTarget.getBoundingClientRect();
    setHovered({ day, x: cell.left + cell.width / 2, y: cell.top });
  };

  return (
    <div ref={ref} role="img" aria-label={label} className="relative">
      {showMonths && (
        <motion.div
          className="flex justify-center"
          style={{ gap, marginBottom: gap }}
          initial={reduceMotion ? false : { opacity: 0, filter: `blur(${LABEL_BLUR}px)` }}
          animate={{ opacity: 1, filter: "blur(0px)" }}
          transition={{ ...LABEL_REVEAL, delay: reduceMotion ? 0 : sweepEnd }}
        >
          {toMonthLabels(visible).map((month, index) => (
            <div key={index} className="relative h-3 shrink-0" style={{ width: cellSize }}>
              {month && (
                <span className="absolute top-0 left-0 text-xs leading-none text-muted-foreground/70">
                  {month}
                </span>
              )}
            </div>
          ))}
        </motion.div>
      )}

      <div
        className="flex justify-center overflow-hidden"
        style={{ gap }}
        onPointerLeave={() => setHovered(undefined)}
      >
        {visible.map((week, weekIndex) => (
          <div key={weekIndex} className="flex flex-col" style={{ gap }}>
            {week.map((day) => (
              <motion.div
                key={day.date}
                onPointerEnter={hover(day)}
                className="shrink-0 rounded-sm bg-muted"
                style={{ width: cellSize, height: cellSize }}
                initial={reduceMotion ? false : { opacity: 0, scale: 0.4 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{
                  ...CELL_FADE,
                  delay: reduceMotion ? 0 : weekIndex * COLUMN_STAGGER,
                }}
              >
                <div
                  className="h-full w-full rounded-sm"
                  style={scale[day.level] ?? scale[0]}
                />
              </motion.div>
            ))}
          </div>
        ))}
      </div>

      <AnimatePresence>
        {hovered && <Tooltip key="tooltip" hovered={hovered} reduceMotion={reduceMotion} />}
      </AnimatePresence>
    </div>
  );
}

export type ActivityCardProps = {
  contributions: Contribution[];
  /** 强调色，默认走品牌色变量 */
  accent?: string;
  cellSize?: number;
  months?: number;
  showMonths?: boolean;
  label?: string;
  className?: string;
};

/**
 * 打卡活跃度卡片。
 *
 * 与原来的 GitHub 组件相比去掉了：第三方请求、仓库排行面板、硬编码的绿白黑配色。
 * 保留的是格子布局与入场动效。
 */
export function ActivityCard({
  contributions,
  accent = "var(--brand)",
  cellSize = DEFAULT_CELL_SIZE,
  months = DEFAULT_MONTHS,
  showMonths = true,
  label = "打卡活跃度",
  className,
}: ActivityCardProps) {
  const reduceMotion = useReducedMotion();
  const scale = React.useMemo(() => toScale(accent), [accent]);

  const total = React.useMemo(
    () => contributions.reduce((sum, day) => sum + day.count, 0),
    [contributions],
  );
  const activeDays = contributions.filter((day) => day.count > 0).length;

  const gap = gapFor(cellSize);
  const columns = Math.min(Math.ceil(contributions.length / 7), weeksFor(months));
  const width = Math.max(280, columns * (cellSize + gap) - gap + CARD_PADDING);

  return (
    <div className={cn("max-w-full overflow-hidden rounded-2xl border bg-card p-4", className)}>
      <div className="mb-4 flex items-baseline justify-between gap-3 px-1.5">
        <p className="text-base font-medium">{label}</p>
        <p className="text-xs text-muted-foreground tabular-nums">
          打卡 {total} 次 · 活跃 {activeDays} 天
        </p>
      </div>

      <div style={{ width }}>
        <ContributionGrid
          contributions={contributions}
          scale={scale}
          cellSize={cellSize}
          months={months}
          showMonths={showMonths}
          label={`${label}：${total} 次打卡，${activeDays} 天有记录`}
          reduceMotion={reduceMotion}
        />
      </div>

      <div className="mt-3 flex items-center justify-end gap-1.5 px-1.5 text-xs text-muted-foreground">
        <span>少</span>
        {LEVELS.map((level) => (
          <span
            key={level}
            className="size-2.5 rounded-sm"
            style={scale[level]}
            aria-hidden
          />
        ))}
        <span>多</span>
      </div>
    </div>
  );
}
