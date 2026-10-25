"use client";

import { ArrowUp, Sparkles, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { streamNoteChat, streamProjectChat } from "@/lib/ai";
import { useSession } from "@/lib/session";

type Turn = { question: string; answer: string; error?: string };

/**
 * 项目 AI 问答。
 *
 * 交互按"聊天面板"来做：入口是一张显眼的卡片，点开后在右下角升起面板，
 * 用户消息靠右、回答靠左，输入框固定在底部。
 *
 * 三个状态都要有明确反馈，否则用户会以为卡住了：
 *   1. 已发送、等首字  → 三点动画 + 已用秒数
 *   2. 正在输出        → 文本后跟闪烁光标
 *   3. 出错            → 气泡内的错误文案，按 429/503/断网分别给不同建议
 */
export function AiChat({ projectId, noteId }: { projectId?: string; noteId?: string }) {
  // 同一个面板服务两种对象：项目问答与笔记问答。差别只有接口与文案，
  // 流式解析、思考态、光标、额度提示全部共用，避免两处实现逐渐跑偏。
  const isNote = Boolean(noteId);
  const subject = isNote ? "这篇笔记" : "这个项目";

  const { token } = useSession();
  const [open, setOpen] = useState(false);
  const [question, setQuestion] = useState("");
  const [turns, setTurns] = useState<Turn[]>([]);
  const [busy, setBusy] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const controllerRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  // 卸载时一定要中止：否则路由跳走后请求还在跑
  useEffect(() => {
    return () => controllerRef.current?.abort();
  }, []);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [turns, busy]);

  // 等首字期间计时：用户看到数字在动，就不会以为卡死
  useEffect(() => {
    if (!busy) {
      setElapsed(0);
      return;
    }
    const startedAt = Date.now();
    const timer = window.setInterval(() => {
      setElapsed(Math.floor((Date.now() - startedAt) / 1000));
    }, 500);
    return () => window.clearInterval(timer);
  }, [busy]);

  function close() {
    controllerRef.current?.abort();
    controllerRef.current = null;
    setBusy(false);
    setOpen(false);
  }

  async function ask(preset?: string) {
    const text = (preset ?? question).trim();
    if (!text || busy) {
      return;
    }

    const controller = new AbortController();
    controllerRef.current = controller;
    setQuestion("");
    setBusy(true);
    setTurns((prev) => [...prev, { question: text, answer: "" }]);

    const stream = isNote
      ? (q: string, h: Parameters<typeof streamProjectChat>[2]) => streamNoteChat(noteId!, q, h)
      : (q: string, h: Parameters<typeof streamProjectChat>[2]) => streamProjectChat(projectId!, q, h);

    await stream(text, {
      signal: controller.signal,
      // 带上 token：访客用受限档，登录成员自动升到成员档
      token,
      onDelta: (chunk) => {
        setTurns((prev) => {
          const next = [...prev];
          const last = next[next.length - 1];
          next[next.length - 1] = { ...last, answer: last.answer + chunk };
          return next;
        });
      },
      onDone: () => {
        setBusy(false);
        controllerRef.current = null;
      },
      onError: (message) => {
        setTurns((prev) => {
          const next = [...prev];
          const last = next[next.length - 1];
          next[next.length - 1] = { ...last, error: message };
          return next;
        });
        setBusy(false);
        controllerRef.current = null;
      },
    });
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="card-lift group flex w-full items-center gap-4 rounded-xl border bg-gradient-to-br from-brand/10 via-transparent to-transparent px-5 py-4 text-left hover:card-lift-hover"
      >
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand text-brand-foreground">
          <Sparkles className="size-5" aria-hidden />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block font-medium">
            {isNote ? "就这篇笔记问 AI" : "就这个项目问 AI"}
          </span>
          <span className="block text-xs text-muted-foreground">
            回答只依据{isNote ? "笔记内容" : "项目资料"}，不编造；{token ? "成员不限次数" : "访客每天 8 次"}
          </span>
        </span>
        <span className="shrink-0 text-sm text-brand">开始对话 →</span>
      </button>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/30 sm:items-stretch sm:justify-end sm:bg-transparent sm:p-6">
      <div className="flex h-[85vh] w-full flex-col overflow-hidden rounded-t-2xl border bg-background shadow-xl sm:h-auto sm:max-h-[80vh] sm:w-[420px] sm:rounded-2xl">
        {/* 头部：标题 + 档位说明 + 关闭 */}
        <div className="flex items-start justify-between gap-3 border-b px-4 py-3">
          <div className="min-w-0">
            <p className="flex items-center gap-1.5 text-sm font-medium">
              <Sparkles className="size-3.5 text-brand" aria-hidden />
              {isNote ? "笔记问答" : "项目问答"}
            </p>
            <p className="truncate text-xs text-muted-foreground">
              {token ? "已登录：不限次数，可问通用问题" : `访客模式：每天 8 次，仅限${subject}内容`}
            </p>
          </div>
          <Button variant="ghost" size="sm" onClick={close} aria-label="关闭">
            <X className="size-4" aria-hidden />
          </Button>
        </div>

        {/* 消息区 */}
        <div ref={scrollRef} className="min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-4">
          {turns.length === 0 ? (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">问点什么？回答只依据{isNote ? "这篇笔记" : "这个项目"}的资料。</p>
              <div className="flex flex-wrap gap-2">
                {(isNote
                  ? ["这篇笔记讲了什么？", "里面的方法怎么用？", "有哪里可能出错？"]
                  : ["这个项目解决了什么问题？", "用了哪些技术？", "怎么部署的？"]
                ).map((item) => (
                  <button
                    key={item}
                    type="button"
                    onClick={() => void ask(item)}
                    className="rounded-full border px-3 py-1.5 text-xs text-muted-foreground transition-colors hover:border-foreground/30 hover:text-foreground"
                  >
                    {item}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            turns.map((turn, index) => {
              const isLast = index === turns.length - 1;
              const streaming = busy && isLast && Boolean(turn.answer);
              return (
                <div key={index} className="space-y-2">
                  {/* 用户消息靠右 */}
                  <div className="flex justify-end">
                    <p className="max-w-[85%] rounded-2xl rounded-br-sm bg-brand px-3.5 py-2 text-sm text-brand-foreground">
                      {turn.question}
                    </p>
                  </div>

                  {/* 回答靠左 */}
                  <div className="flex justify-start">
                    <div className="max-w-[92%] space-y-1.5 rounded-2xl rounded-bl-sm border bg-muted/40 px-3.5 py-2.5">
                      {turn.answer ? (
                        <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground/90">
                          {turn.answer}
                          {streaming ? (
                            <span
                              aria-hidden
                              className="animate-caret ml-0.5 inline-block h-3.5 w-0.5 translate-y-0.5 bg-foreground"
                            />
                          ) : null}
                        </p>
                      ) : busy && isLast ? (
                        <ThinkingRow elapsed={elapsed} />
                      ) : null}
                      {turn.error ? <p className="text-sm text-destructive">{turn.error}</p> : null}
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* 输入区 */}
        <div className="border-t p-3">
          <div className="flex items-end gap-2 rounded-2xl border bg-background px-3 py-2 focus-within:border-foreground/30">
            <textarea
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  void ask();
                }
              }}
              rows={1}
              maxLength={500}
              placeholder="问点什么…（Enter 发送，Shift+Enter 换行）"
              disabled={busy}
              className="max-h-32 min-h-6 flex-1 resize-none bg-transparent text-sm outline-none placeholder:text-muted-foreground"
            />
            <Button
              size="sm"
              onClick={() => void ask()}
              disabled={busy || !question.trim()}
              aria-label="发送"
              className="size-8 shrink-0 rounded-full p-0"
            >
              <ArrowUp className="size-4" aria-hidden />
            </Button>
          </div>
          <p className="mt-1.5 text-center text-xs text-muted-foreground">
            {token ? "成员不限次数" : "访客每天 8 次"} · 回答由 AI 生成，请核对关键信息
          </p>
        </div>
      </div>
    </div>
  );
}

/**
 * 等待首字的思考态。
 *
 * 只放三个点在动不够，用户不知道等了多久；带上秒数就能自己判断要不要继续等。
 */
function ThinkingRow({ elapsed }: { elapsed: number }) {
  return (
    <div className="flex items-center gap-2 text-sm text-muted-foreground">
      <span className="flex gap-1" aria-hidden>
        {[0, 0.15, 0.3].map((delay) => (
          <span
            key={delay}
            className="animate-thinking size-1.5 rounded-full bg-current"
            style={{ animationDelay: `${delay}s` }}
          />
        ))}
      </span>
      <span>正在查阅资料</span>
      {elapsed > 0 ? <span className="tabular-nums">{elapsed}s</span> : null}
    </div>
  );
}
