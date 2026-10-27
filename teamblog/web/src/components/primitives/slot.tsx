"use client";

import { Children, cloneElement, isValidElement } from "react";

/**
 * Slot 的最小实现：把 props 合并到唯一子元素上。
 *
 * 只为这些组件提供 `asChild` 能力，不追求 Radix Slot 的全部行为
 * （事件合并、ref 转发细节）。项目里没有引入 radix，多装一个包只为这点功能不划算。
 */
export function Slot({
  children,
  ...props
}: React.HTMLAttributes<HTMLElement> & { children?: React.ReactNode }) {
  const child = Children.only(children);
  if (!isValidElement(child)) {
    return null;
  }
  const childProps = child.props as Record<string, unknown>;
  return cloneElement(child as React.ReactElement<Record<string, unknown>>, {
    ...props,
    ...childProps,
    className: [props.className, childProps.className].filter(Boolean).join(" ") || undefined,
  });
}

/** Radix 的 Slottable 只是标记"可被替换的子树"，这里原样渲染。 */
export function Slottable({ children }: { children?: React.ReactNode }) {
  return <>{children}</>;
}
