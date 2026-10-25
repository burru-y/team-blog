/**
 * 共用的动效参数。
 *
 * 集中在这里而不是散在组件内部：同一种交互（鼠标跟随、面板展开）在不同组件里
 * 应该用同一条曲线，否则页面之间的"手感"会不一致。
 */

/** 鼠标跟随类：跟随要快，但不能有弹簧抖动。 */
export const SPRING_MOUSE = { type: "spring", stiffness: 260, damping: 28, mass: 0.4 } as const;

/** 布局展开/收起：稍慢一点，配合内容变化。 */
export const SPRING_LAYOUT = { type: "spring", stiffness: 320, damping: 34 } as const;

/** 通用缓动：入场淡入这类一次性动画用它。 */
export const EASE_OUT = [0.22, 1, 0.36, 1] as const;
