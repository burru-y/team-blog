"use client";

import { useCallback, useMemo, useRef, useState } from "react";

/**
 * 滑块的数值与无障碍属性。
 *
 * 只负责三件事：当前值、把值限制在区间与步长上、以及给 DOM 的 props。
 * 拖动的手势与几何计算由组件自己做——那部分和具体排布强相关，塞进 hook 反而更难读。
 */
export type SliderOptions = {
  defaultValue?: number;
  value?: number;
  min?: number;
  max?: number;
  step?: number;
  disabled?: boolean;
  onChange?: (value: number) => void;
  onValueCommit?: (value: number) => void;
  "aria-label"?: string;
  formatValueText?: (value: number) => string;
};

export function snapSliderValue(value: number, min: number, max: number, step: number) {
  const clamped = Math.min(max, Math.max(min, value));
  if (step <= 0) {
    return clamped;
  }
  const snapped = Math.round((clamped - min) / step) * step + min;
  // 浮点累加会出 0.30000000000000004，按步长的小数位收敛
  const decimals = (String(step).split(".")[1] ?? "").length;
  return Number(Math.min(max, Math.max(min, snapped)).toFixed(decimals));
}

export function useSlider(options: SliderOptions) {
  const { min = 0, max = 100, step = 1 } = options;

  const [innerValue, setInnerValue] = useState(() =>
    snapSliderValue(options.defaultValue ?? options.value ?? min, min, max, step),
  );

  const isControlled = options.value !== undefined;
  const current = isControlled ? snapSliderValue(options.value!, min, max, step) : innerValue;

  const latest = useRef(current);
  latest.current = current;

  const setValue = useCallback(
    (next: number) => {
      const snapped = snapSliderValue(next, min, max, step);
      if (snapped === latest.current) {
        return;
      }
      latest.current = snapped;
      if (!isControlled) {
        setInnerValue(snapped);
      }
      options.onChange?.(snapped);
    },
    [isControlled, min, max, step, options],
  );

  const commit = useCallback(
    (next?: number) => {
      const value = next === undefined ? latest.current : snapSliderValue(next, min, max, step);
      latest.current = value;
      if (!isControlled) {
        setInnerValue(value);
      }
      options.onChange?.(value);
      options.onValueCommit?.(value);
    },
    [isControlled, min, max, step, options],
  );

  const sliderProps = useMemo(
    () => ({
      role: "slider" as const,
      tabIndex: options.disabled ? -1 : 0,
      "aria-valuemin": min,
      "aria-valuemax": max,
      "aria-valuenow": current,
      "aria-valuetext": options.formatValueText?.(current),
      "aria-label": options["aria-label"],
      "aria-disabled": options.disabled || undefined,
      onKeyDown: (event: React.KeyboardEvent) => {
        if (options.disabled) {
          return;
        }
        const big = event.shiftKey ? 10 : 1;
        if (event.key === "ArrowRight" || event.key === "ArrowUp") {
          event.preventDefault();
          setValue(current + step * big);
        } else if (event.key === "ArrowLeft" || event.key === "ArrowDown") {
          event.preventDefault();
          setValue(current - step * big);
        } else if (event.key === "Home") {
          event.preventDefault();
          commit(min);
        } else if (event.key === "End") {
          event.preventDefault();
          commit(max);
        }
      },
    }),
    [current, min, max, step, options, setValue, commit],
  );

  const trackProps = useMemo(
    () => ({
      ref: { current: null } as React.RefObject<HTMLDivElement | null>,
    }),
    [],
  );

  return { current, min, max, step, setValue, commit, trackProps, sliderProps };
}
