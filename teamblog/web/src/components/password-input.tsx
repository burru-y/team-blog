"use client";

import { useEffect, useId, useRef, useState, type ComponentProps } from "react";
import { AnimatePresence, motion, useReducedMotion, type Transition } from "motion/react";

import { cn } from "@/lib/utils";

/**
 * 密码输入框：可见性切换 + 强度条。
 *
 * 来自社区实现，改了两处：
 *   1. 颜色全部换成主题 token（原来写死 #F4F4F9 / #FC4C01 / #262626）
 *   2. 文案改成中文
 *
 * 为什么要自己画字形而不是直接用 type=password：浏览器原生密码框无法做
 * "逐字切换明文/圆点"的过渡，只能整个切换；这里把 input 文字设为透明，
 * 在上面用一层 div 逐字符渲染。
 */
const LEVELS = [
  { label: "弱", color: "var(--color-red-500)" },
  { label: "一般", color: "var(--color-amber-500)" },
  { label: "好", color: "var(--color-yellow-400)" },
  { label: "强", color: "var(--color-emerald-500)" },
] as const;

const LID_OPEN = { top: 5.5, bottom: 18.5 };
const LID_CLOSED = { top: 17, bottom: 17 };
const LASHES = ["M4.4 14.6 3 16.4", "M9 16.6 8.5 19", "M15 16.6l.5 2.4", "M19.6 14.6l1.4 1.8"];

const INSTANT: Transition = { duration: 0 };
const SPRING: Transition = { type: "spring", stiffness: 520, damping: 32 };
const LID_SPRING: Transition = { type: "spring", stiffness: 500, damping: 34 };
const SWAP: Transition = { duration: 0.12, ease: "easeOut" };
const REVEAL: Transition = { duration: 0.32, ease: [0.22, 1, 0.36, 1] };
const BLINK: Transition = {
  duration: 1.1,
  times: [0, 0.5, 0.5, 1],
  repeat: Infinity,
  ease: "linear",
};

const corner = (y: number) => 12 + (y - 12) * 0.64;

const eyePath = ({ top, bottom }: { top: number; bottom: number }) =>
  `M2 12C4 ${corner(top)} 7.5 ${top} 12 ${top}C16.5 ${top} 20 ${corner(top)} 22 12C20 ${corner(bottom)} 16.5 ${bottom} 12 ${bottom}C7.5 ${bottom} 4 ${corner(bottom)} 2 12Z`;

/** 0 = 空；1..4 = 强度等级。与后端的 8 位下限保持一致。 */
export function passwordStrength(value: string) {
  if (!value) return 0;
  const score = [
    value.length >= 8,
    value.length >= 12,
    /[a-z]/.test(value) && /[A-Z]/.test(value),
    /\d/.test(value),
    /[^a-zA-Z0-9]/.test(value),
  ].filter(Boolean).length;
  return Math.min(Math.max(score, 1), LEVELS.length);
}

function Glyph({ char, visible, reduceMotion }: { char: string; visible: boolean; reduceMotion: boolean }) {
  const transition = reduceMotion ? INSTANT : SWAP;

  return (
    <span className="relative grid h-full w-[1ch] shrink-0 place-items-center">
      <motion.span
        initial={false}
        animate={{ opacity: visible ? 0 : 1 }}
        transition={transition}
        className="absolute size-[0.36em] rounded-full bg-current"
      />
      <motion.span
        initial={false}
        animate={{ opacity: visible ? 1 : 0 }}
        transition={transition}
        className="whitespace-pre"
      >
        {char}
      </motion.span>
    </span>
  );
}

function Eye({ open, reduceMotion }: { open: boolean; reduceMotion: boolean }) {
  const clipId = useId();
  const lid = { d: eyePath(open ? LID_OPEN : LID_CLOSED) };
  const transition = reduceMotion ? INSTANT : LID_SPRING;

  return (
    <svg
      aria-hidden
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-5 overflow-visible"
    >
      <clipPath id={clipId}>
        <motion.path initial={false} animate={lid} transition={transition} />
      </clipPath>
      <circle cx={12} cy={12} r={3} clipPath={`url(#${clipId})`} />
      <motion.path initial={false} animate={lid} transition={transition} />
      {LASHES.map((d) => (
        <motion.path
          key={d}
          d={d}
          initial={false}
          animate={{ pathLength: open ? 0 : 1, opacity: open ? 0 : 1 }}
          transition={reduceMotion ? INSTANT : { duration: 0.2, delay: open ? 0 : 0.08 }}
        />
      ))}
    </svg>
  );
}

export type PasswordInputProps = Omit<
  ComponentProps<"input">,
  "type" | "value" | "defaultValue" | "onChange" | "size"
> & {
  value?: string;
  defaultValue?: string;
  onChange?: (value: string) => void;
  defaultVisible?: boolean;
  onVisibleChange?: (visible: boolean) => void;
  strength?: boolean;
  inputClassName?: string;
};

export function PasswordInput({
  value,
  defaultValue = "",
  onChange,
  defaultVisible = false,
  onVisibleChange,
  strength = true,
  placeholder,
  disabled,
  autoComplete = "current-password",
  className,
  inputClassName,
  onFocus,
  onBlur,
  ...props
}: PasswordInputProps) {
  const [uncontrolled, setUncontrolled] = useState(defaultValue);
  const [visible, setVisible] = useState(defaultVisible);
  const [focused, setFocused] = useState(false);
  const [selection, setSelection] = useState({ start: 0, end: 0, scroll: 0 });
  const input = useRef<HTMLInputElement>(null);
  const frame = useRef(0);
  const reduceMotion = useReducedMotion() ?? false;

  const password = value ?? uncontrolled;
  const chars = Array.from(password);
  const level = passwordStrength(password);
  const animated = (transition: Transition) => (reduceMotion ? INSTANT : transition);

  useEffect(() => () => cancelAnimationFrame(frame.current), []);

  const read = () => {
    const el = input.current;
    if (!el) return;
    // 选区偏移按 utf-16 计，字形按码点计
    const start = Array.from(el.value.slice(0, el.selectionStart ?? 0)).length;
    const end = Array.from(el.value.slice(0, el.selectionEnd ?? 0)).length;
    const scroll = el.scrollLeft;
    setSelection((current) =>
      current.start === start && current.end === end && current.scroll === scroll
        ? current
        : { start, end, scroll },
    );
  };

  const sync = () => {
    read();
    cancelAnimationFrame(frame.current);
    frame.current = requestAnimationFrame(read);
  };

  const toggle = () => {
    const el = input.current;
    const start = el?.selectionStart ?? null;
    const end = el?.selectionEnd ?? null;
    setVisible(!visible);
    onVisibleChange?.(!visible);
    // 切换 input 类型时部分浏览器会丢掉选区
    cancelAnimationFrame(frame.current);
    frame.current = requestAnimationFrame(() => {
      if (el && start !== null && end !== null && document.activeElement === el)
        el.setSelectionRange(start, end);
      read();
    });
  };

  return (
    <div
      data-visible={visible}
      className={cn("relative flex w-full flex-col", disabled && "pointer-events-none opacity-50", className)}
    >
      <div className="relative flex h-11 items-center rounded-lg border bg-background focus-within:border-foreground/30">
        <div className="relative h-full min-w-0 flex-1">
          <input
            ref={input}
            {...props}
            type={visible ? "text" : "password"}
            value={password}
            placeholder={placeholder}
            disabled={disabled}
            autoComplete={autoComplete}
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            onChange={(event) => {
              if (value === undefined) setUncontrolled(event.target.value);
              onChange?.(event.target.value);
              sync();
            }}
            onSelect={sync}
            onScroll={sync}
            onFocus={(event) => {
              setFocused(true);
              sync();
              onFocus?.(event);
            }}
            onBlur={(event) => {
              setFocused(false);
              onBlur?.(event);
            }}
            className={cn(
              "absolute inset-0 size-full bg-transparent pl-3 pr-2 font-mono text-base text-transparent caret-transparent outline-none [-webkit-text-fill-color:transparent] selection:bg-transparent placeholder:text-transparent",
              inputClassName,
            )}
          />

          <div
            aria-hidden
            className="pointer-events-none absolute inset-y-0 right-2 left-3 overflow-hidden font-mono text-base"
          >
            {!password && placeholder && (
              <span className="absolute inset-y-0 left-0 flex items-center font-sans text-sm text-muted-foreground">
                {placeholder}
              </span>
            )}

            <div
              className="relative flex h-full items-center"
              style={{ transform: `translateX(${-selection.scroll}px)` }}
            >
              {focused && selection.end > selection.start && (
                <span
                  className="absolute top-1/2 h-6 -translate-y-1/2 rounded bg-brand/25"
                  style={{
                    left: `${selection.start}ch`,
                    width: `${selection.end - selection.start}ch`,
                  }}
                />
              )}

              {chars.map((char, index) => (
                <Glyph key={index} char={char} visible={visible} reduceMotion={reduceMotion} />
              ))}

              {focused && selection.start === selection.end && (
                <motion.span
                  key={`${selection.end}:${chars.length}`}
                  animate={{ opacity: [1, 1, 0, 0] }}
                  transition={BLINK}
                  style={{ left: `${selection.end}ch` }}
                  className="absolute top-1/2 -ml-px h-5 w-0.5 -translate-y-1/2 rounded-full bg-foreground"
                />
              )}
            </div>
          </div>
        </div>

        <button
          type="button"
          aria-label={visible ? "隐藏密码" : "显示密码"}
          aria-pressed={visible}
          disabled={disabled}
          onPointerDown={(event) => event.preventDefault()}
          onClick={toggle}
          className="mr-1 grid size-9 shrink-0 place-items-center rounded-lg text-muted-foreground outline-none transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
        >
          <Eye open={visible} reduceMotion={reduceMotion} />
        </button>
      </div>

      <AnimatePresence initial={false}>
        {strength && level > 0 && (
          <motion.div
            initial={{ opacity: 0, y: -4, filter: "blur(2px)" }}
            animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
            exit={{ opacity: 0, y: -4, filter: "blur(2px)" }}
            transition={animated(REVEAL)}
            // 脱离文档流：强度条出现时输入框不该被顶下去
            className="absolute inset-x-0 top-full"
          >
            <div className="flex flex-col gap-2 px-1 pt-3">
              <div className="flex gap-1.5">
                {LEVELS.map((entry, index) => (
                  <span key={entry.label} className="h-1 flex-1 overflow-hidden rounded-full bg-muted">
                    <motion.span
                      initial={{ scaleX: 0 }}
                      animate={{
                        scaleX: index < level ? 1 : 0,
                        backgroundColor: LEVELS[level - 1].color,
                      }}
                      transition={animated({ ...SPRING, delay: index * 0.04 })}
                      className="block h-full origin-left rounded-full"
                    />
                  </span>
                ))}
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="text-muted-foreground">密码强度</span>
                <span
                  aria-live="polite"
                  className="relative flex h-4 justify-end overflow-hidden font-medium text-muted-foreground"
                >
                  <AnimatePresence initial={false} mode="popLayout">
                    <motion.span
                      key={level}
                      initial={{ y: "100%", opacity: 0 }}
                      animate={{ y: 0, opacity: 1 }}
                      exit={{ y: "-100%", opacity: 0 }}
                      transition={animated(SPRING)}
                    >
                      {LEVELS[level - 1].label}
                    </motion.span>
                  </AnimatePresence>
                </span>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
