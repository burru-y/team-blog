"use client";

import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { streamProjectChat } from "@/lib/ai";
import { useSession } from "@/lib/session";

type Turn = { question: string; answer: string; error?: string };

/**
 * 项目问答抽屉（批次 7.4）。
 *
 * 三个必须做对的地方：
 * 1. **关闭时中止**：`AbortController.abort()` → 服务端 `is_disconnected()` 为真 → 停止调用模型。
 *    不做这一步，用户关掉抽屉后 token 还在烧。
 * 2. **流式渲染**：每收到一个 delta 就 setState，用户看到的是逐字出现。
 * 3. **降级文案**：429 / 503 / 502 / 断网 各自不同——用户该做的动作不一样。
 */
export function AiChat({ projectId }: { projectId: string }) {
  // 没有 token 也能用（访客档）；登录后自动带 token 升到成员档
  const { token } = useSession();
  const [open, setOpen] = useState(false);
  const [question, setQuestion] = useState("");
  const [turns, setTurns] = useState<Turn[]>([]);
  const [busy, setBusy] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  // 卸载时一定要中止：否则路由跳走后请求还在跑
  useEffect(() => {
    return () => controllerRef.current?.abort();
  }, []);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
  }, [turns]);

  function close() {
    controllerRef.current?.abort();
    controllerRef.current = null;
    setBusy(false);
    setOpen(false);
  }

  async function ask() {
    const text = question.trim();
    if (!text || busy) {
      return;
    }

    const controller = new AbortController();
    controllerRef.current = controller;
    setQuestion("");
    setBusy(true);
    setTurns((prev) => [...prev, { question: text, answer: "" }]);

    await streamProjectChat(projectId, text, {
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
      <Button variant="outline" onClick={() => setOpen(true)}>
        问问 AI
      </Button>
    );
  }

  return (
    <div className="fixed inset-x-0 bottom-0 z-50 mx-auto w-full max-w-2xl p-3 sm:p-4">
      <div className="flex max-h-[70vh] flex-col rounded-xl border bg-background shadow-lg">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <div>
            <p className="text-sm font-medium">问问 AI</p>
            <p className="text-xs text-muted-foreground">
              {token ? "已登录：不限次数，可问通用问题" : "访客模式：每天 8 次，仅限本项目内容"}
            </p>
          </div>
          <Button variant="ghost" size="sm" onClick={close}>
            关闭
          </Button>
        </div>

        <div ref={scrollRef} className="min-h-[120px] flex-1 space-y-4 overflow-y-auto px-4 py-3">
          {turns.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              可以问：这个项目解决了什么问题？用了哪些技术？怎么部署的？
            </p>
          ) : (
            turns.map((turn, index) => (
              <div key={index} className="space-y-2">
                <p className="text-sm font-medium">问：{turn.question}</p>
                <p className="whitespace-pre-wrap text-sm text-muted-foreground">
                  {turn.answer || (busy && index === turns.length - 1 ? "思考中…" : "")}
                </p>
                {turn.error ? <p className="text-sm text-destructive">{turn.error}</p> : null}
              </div>
            ))
          )}
        </div>

        <div className="space-y-2 border-t px-4 py-3">
          <Textarea
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                void ask();
              }
            }}
            rows={2}
            maxLength={500}
            placeholder="输入问题，Enter 发送，Shift+Enter 换行"
            disabled={busy}
          />
          <div className="flex items-center gap-2">
            <Button onClick={() => void ask()} disabled={busy || !question.trim()}>
              {busy ? "回答中…" : "发送"}
            </Button>
            {busy ? (
              <Button variant="ghost" size="sm" onClick={close}>
                停止
              </Button>
            ) : null}
            <span className="text-xs text-muted-foreground">
              {token ? "成员不限次数" : "访客每天 8 次"}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
