"use client";

import { Button as BaseButton, buttonVariants } from "@/components/ui/button";

/**
 * 组件库内部用的按钮。
 *
 * 只做一层包装：把项目已有的按钮样式暴露到 beUI 组件的期望路径，
 * 免得为了几个组件再维护一套独立的按钮实现。
 */
export const Button = BaseButton;
export { buttonVariants };
export default BaseButton;
