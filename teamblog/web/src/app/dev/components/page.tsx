"use client";

import { useState } from "react";

import { Alert, AlertClose, AlertContent, AlertDescription, AlertIcon, AlertTitle } from "@/components/motion/animated-alert";
import {
  AdaptiveStepper,
  AdaptiveStepperDecrement,
  AdaptiveStepperIncrement,
  AdaptiveStepperValue,
} from "@/components/motion/adaptive-stepper";
import ApprovalCard from "@/components/motion/approval-card";
import { ColorPicker } from "@/components/motion/color-picker";
import { DeleteButton } from "@/components/motion/delete-button";
import { Loader } from "@/components/motion/loader";
import { MessageScroller } from "@/components/motion/message-scroller";
import { NotificationBell } from "@/components/motion/notification-bell";
import { DigitSwap } from "@/components/motion/number-animation";
import { InlineSlider } from "@/components/motion/range-slider";
import StreamingText from "@/components/motion/streaming-text";
import { TaskList, type Task } from "@/components/motion/task-list";
import { ThemeToggle } from "@/components/motion/theme-toggle";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";

/**
 * 组件预览页：把引入的 13 个动效组件逐个摆出来。
 *
 * 存在的意义是"能看见"：这些组件分散到各页面后，很难判断某个动效是组件本身
 * 还是被页面布局影响了。集中在一页里对着看，改动前后差异一目了然。
 *
 * 生产环境不需要它，但它只有静态内容，构建成本可以忽略。
 */
export default function ComponentsPage() {
  const [bellCount, setBellCount] = useState(3);
  const [stepper, setStepper] = useState(2);
  const [slider, setSlider] = useState(40);
  const [color, setColor] = useState("#5b8def");
  const [tasks, setTasks] = useState<Task[]>([
    { id: "1", label: "写完上线复盘", done: true },
    { id: "2", label: "补部署文档", done: false },
    { id: "3", label: "整理 1.1 更新说明", done: false },
  ]);

  return (
    <div className="space-y-8">
      <PageHeader
        size="large"
        title="组件预览"
        description="引入的动效组件在此逐项展示，便于对照效果与排查样式冲突。"
      />

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">加载动画 · Loader</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap items-center gap-6">
            <Loader variant="spinner" size={28} />
            <Loader variant="dots" size={28} />
            <Loader variant="bars" size={28} />
            <Loader variant="comet" size={28} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">主题切换 · ThemeToggle</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <ThemeToggle variant="circle-blur" start="top-right" />
            <p className="text-xs text-muted-foreground">
              点一下看整页切换的圆形扩散效果（四种变体：rectangle / circle / circle-blur / blinds）。
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">通知铃铛 · NotificationBell</CardTitle>
          </CardHeader>
          <CardContent className="flex items-center gap-6">
            <NotificationBell count={bellCount} size={32} />
            <NotificationBell count={0} size={32} variant="dot" />
            <div className="flex gap-2">
              <button
                type="button"
                className="rounded border px-2 py-1 text-xs"
                onClick={() => setBellCount((value) => value + 1)}
              >
                加一条
              </button>
              <button
                type="button"
                className="rounded border px-2 py-1 text-xs"
                onClick={() => setBellCount(0)}
              >
                清零
              </button>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">数字动画 · DigitSwap</CardTitle>
          </CardHeader>
          <CardContent className="flex items-center gap-4 text-2xl font-semibold">
            <DigitSwap value={String(bellCount * 128)} />
            <span className="text-sm text-muted-foreground">（随铃铛数字变化）</span>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">提示条 · Alert</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {(["info", "success", "warning", "destructive"] as const).map((variant) => (
              <Alert key={variant} variant={variant}>
                <AlertIcon>
                  <span aria-hidden>•</span>
                </AlertIcon>
                <AlertContent>
                  <AlertTitle>{variant}</AlertTitle>
                  <AlertDescription>这是一条 {variant} 类型的提示。</AlertDescription>
                </AlertContent>
                <AlertClose />
              </Alert>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">步进器 · AdaptiveStepper</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <AdaptiveStepper
              value={stepper}
              onValueChange={setStepper}
              min={0}
              max={10}
              aria-label="邀请名额"
            >
              <AdaptiveStepperDecrement />
              <AdaptiveStepperValue />
              <AdaptiveStepperIncrement />
            </AdaptiveStepper>
            <p className="text-xs text-muted-foreground">当前：{stepper}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">滑块 · RangeSlider</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <InlineSlider defaultValue={40} onValueCommit={setSlider} min={0} max={100} step={1} label="进度" />
            <p className="text-xs text-muted-foreground">当前：{slider}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">取色器 · ColorPicker</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <ColorPicker value={color} onValueChange={setColor} />
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              当前色
              <span className="inline-block size-4 rounded border" style={{ background: color }} />
              {color}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">删除按钮 · DeleteButton</CardTitle>
          </CardHeader>
          <CardContent>
            <DeleteButton onConfirm={() => setBellCount(0)} />
            <p className="mt-2 text-xs text-muted-foreground">点一下展开确认，确认后清零上方铃铛数字。</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">待办清单 · TaskList</CardTitle>
          </CardHeader>
          <CardContent>
            <TaskList tasks={tasks} onTasksChange={setTasks} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">审批卡片 · ApprovalCard</CardTitle>
          </CardHeader>
          <CardContent>
            <ApprovalCard />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">流式文字 · StreamingText</CardTitle>
          </CardHeader>
          <CardContent>
            <StreamingText />
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">消息滚动 · MessageScroller</CardTitle>
          </CardHeader>
          <CardContent style={{ height: 320 }}>
            <MessageScroller>
              {Array.from({ length: 12 }, (_, index) => (
                <div
                  key={index}
                  className={`max-w-[80%] rounded-xl px-3 py-2 text-sm ${
                    index % 2 === 0 ? "bg-muted" : "ml-auto bg-brand/10"
                  }`}
                >
                  第 {index + 1} 条消息：用来验证自动跟随滚动与右侧缩略导航。
                </div>
              ))}
            </MessageScroller>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
