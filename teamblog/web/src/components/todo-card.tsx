"use client";

import { Check, Plus, X } from "lucide-react";
import { useState } from "react";

import { TaskItem } from "@/components/motion/task-list";
import { Button } from "@/components/ui/button";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { useSession } from "@/lib/session";
import type { TodoItem } from "@/lib/types";

/**
 * 个人主页的待办清单。
 *
 * 访客只读，本人（或管理员）可增删勾选——权限判断与后端一致，
 * 前端这层只是"少显示点不动的按钮"。
 *
 * 勾选走乐观更新：先改界面再发请求，失败回滚。
 * 访问服务器一趟几百毫秒，等回来再变色会让人觉得没点上。
 *
 * 用 TaskItem 逐条渲染而不是 TaskList：需要给每行配一个删除按钮，
 * TaskList 只负责整体列表，行内操作得自己拼。
 */
export function TodoCard({
  username,
  initialTodos,
}: {
  username: string;
  initialTodos: TodoItem[];
}) {
  const { user, token, loading } = useSession();
  const [todos, setTodos] = useState<TodoItem[]>(initialTodos);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 未登录时不闪按钮：身份读出来之前按只读渲染
  const canEdit =
    !loading && Boolean(user && token) && (user!.role === "ADMIN" || user!.username === username);

  const doneCount = todos.filter((todo) => todo.done).length;

  async function toggle(id: string, checked: boolean) {
    if (!token || !canEdit) {
      return;
    }
    const snapshot = todos;
    setTodos((current) =>
      current.map((todo) => (todo.id === id ? { ...todo, done: checked } : todo)),
    );
    try {
      await api.toggleTodo(id, token);
    } catch (err) {
      setTodos(snapshot);
      setError(err instanceof ApiError ? messageFor(err.code) : "更新失败");
    }
  }

  async function add() {
    const label = draft.trim();
    if (!label || !token) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const created = await api.createTodo(label, token);
      setTodos((current) => [...current, created]);
      setDraft("");
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "添加失败");
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: string) {
    if (!token) {
      return;
    }
    const snapshot = todos;
    setTodos((current) => current.filter((todo) => todo.id !== id));
    try {
      await api.deleteTodo(id, token);
    } catch (err) {
      setTodos(snapshot);
      setError(err instanceof ApiError ? messageFor(err.code) : "删除失败");
    }
  }

  async function clearDone() {
    if (!token) {
      return;
    }
    const snapshot = todos;
    setTodos((current) => current.filter((todo) => !todo.done));
    try {
      await api.clearDoneTodos(token);
    } catch (err) {
      setTodos(snapshot);
      setError(err instanceof ApiError ? messageFor(err.code) : "清理失败");
    }
  }

  return (
    <section className="space-y-3">
      <div className="flex items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold tracking-tight">待办</h2>
        <span className="text-xs text-muted-foreground tabular-nums">
          {doneCount}/{todos.length}
        </span>
      </div>

      {canEdit ? (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void add();
          }}
          className="flex items-center gap-1.5"
        >
          <input
            value={draft}
            maxLength={120}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="加一条待办，回车保存"
            aria-label="新增待办"
            className="min-w-0 flex-1 rounded-lg border bg-background px-2.5 py-1.5 text-sm outline-none focus-visible:border-foreground/30"
          />
          <Button type="submit" size="sm" variant="outline" disabled={busy || !draft.trim()}>
            <Plus className="size-3.5" aria-hidden />
          </Button>
        </form>
      ) : null}

      {todos.length === 0 ? (
        <p className="text-xs text-muted-foreground">
          {canEdit ? "还没有待办，写一条试试。" : "这个人还没有列待办。"}
        </p>
      ) : (
        <ul className="space-y-0.5">
          {todos.map((todo) => (
            <li key={todo.id} className="group flex items-center gap-1">
              <div className="min-w-0 flex-1">
                <TaskItem
                  label={todo.label}
                  checked={todo.done}
                  size="sm"
                  onCheckedChange={(checked) => void toggle(todo.id, checked)}
                  className="w-full text-left text-sm"
                />
              </div>
              {canEdit ? (
                <button
                  type="button"
                  onClick={() => void remove(todo.id)}
                  aria-label={`删除待办 ${todo.label}`}
                  className="shrink-0 rounded p-1 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100 hover:text-destructive"
                >
                  <X className="size-3.5" aria-hidden />
                </button>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      {canEdit && doneCount > 0 ? (
        <button
          type="button"
          onClick={() => void clearDone()}
          className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
        >
          <Check className="size-3.5" aria-hidden />
          清除已完成（{doneCount}）
        </button>
      ) : null}

      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </section>
  );
}
