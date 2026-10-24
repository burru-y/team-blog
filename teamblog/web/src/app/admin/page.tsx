"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { useSession } from "@/lib/session";
import type { AdminFeedback, AdminProject, InviteCreated, UserResponse } from "@/lib/types";

type Tab = "members" | "invites" | "content" | "feedback";

const TABS: { key: Tab; label: string }[] = [
  { key: "members", label: "成员" },
  { key: "invites", label: "邀约" },
  { key: "content", label: "内容" },
  { key: "feedback", label: "留言" },
];

/**
 * 后台（管理员）。
 *
 * <p>只是所有管理接口的另一个视图——接口稳定之后再写 UI，否则就是给会变的字段做界面。
 * 每个 tab 的数据都在切换时按需加载，不一次性拉四个列表。
 */
export default function AdminPage() {
  const { user, token, loading } = useSession();
  const [tab, setTab] = useState<Tab>("members");
  const [error, setError] = useState<string | null>(null);

  if (loading) {
    return <p className="py-10 text-sm text-muted-foreground">正在确认登录状态…</p>;
  }
  if (!user || user.role !== "ADMIN") {
    return (
      <p className="py-10 text-sm text-muted-foreground">
        这个页面只对管理员开放。
      </p>
    );
  }
  if (!token) {
    return null;
  }

  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">后台</h1>
        <p className="text-sm text-muted-foreground">
          成员与角色、邀请链接、内容下架、访客留言。
        </p>
      </header>

      <div className="flex gap-2 border-b pb-3">
        {TABS.map((item) => (
          <Button
            key={item.key}
            size="sm"
            variant={tab === item.key ? "default" : "ghost"}
            onClick={() => setTab(item.key)}
          >
            {item.label}
          </Button>
        ))}
      </div>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      {tab === "members" ? <MembersTab token={token} onError={setError} /> : null}
      {tab === "invites" ? <InvitesTab token={token} onError={setError} /> : null}
      {tab === "content" ? <ContentTab token={token} onError={setError} /> : null}
      {tab === "feedback" ? <FeedbackTab token={token} onError={setError} /> : null}
    </div>
  );
}

function MembersTab({ token, onError }: { token: string; onError: (msg: string | null) => void }) {
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setUsers(await api.adminUsers(token));
    } catch (err) {
      onError(err instanceof ApiError ? messageFor(err.code) : "加载失败");
    }
  }, [token, onError]);

  useEffect(() => {
    void load();
  }, [load]);

  async function toggleRole(target: UserResponse) {
    const next = target.role === "ADMIN" ? "MEMBER" : "ADMIN";
    if (!window.confirm(`把 ${target.username} 改成${next === "ADMIN" ? "管理员" : "成员"}？对方的登录会立刻失效。`)) {
      return;
    }
    setBusyId(target.id);
    try {
      await api.changeRole(target.id, next, token);
      await load();
    } catch (err) {
      onError(err instanceof ApiError ? messageFor(err.code) : "修改失败");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">成员（{users.length}）</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {users.map((item) => (
          <div key={item.id} className="flex items-center justify-between rounded-lg border px-3 py-2">
            <div className="flex items-center gap-2">
              <Link href={`/u/${item.username}`} className="font-medium hover:underline">
                {item.username}
              </Link>
              <Badge variant={item.role === "ADMIN" ? "default" : "secondary"}>
                {item.role === "ADMIN" ? "管理员" : "成员"}
              </Badge>
              <span className="text-xs text-muted-foreground">
                {item.totalPoints} 分 · Lv{item.level}
              </span>
            </div>
            <Button
              size="sm"
              variant="outline"
              disabled={busyId === item.id}
              onClick={() => toggleRole(item)}
            >
              {item.role === "ADMIN" ? "降为成员" : "升为管理员"}
            </Button>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function InvitesTab({ token, onError }: { token: string; onError: (msg: string | null) => void }) {
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<"MEMBER" | "ADMIN">("MEMBER");
  const [created, setCreated] = useState<InviteCreated | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      setCreated(await api.createInvite({ email: email.trim() || null, role }, token));
      setEmail("");
    } catch (err) {
      onError(err instanceof ApiError ? messageFor(err.code) : "生成失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">生成邀请链接</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label htmlFor="invite-email">邮箱（可选，填了就锁定）</Label>
            <Input
              id="invite-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="member@example.com"
            />
          </div>
          <div className="space-y-1">
            <Label>角色</Label>
            <div className="flex gap-2">
              <Button type="button" size="sm" variant={role === "MEMBER" ? "default" : "outline"} onClick={() => setRole("MEMBER")}>
                成员
              </Button>
              <Button type="button" size="sm" variant={role === "ADMIN" ? "default" : "outline"} onClick={() => setRole("ADMIN")}>
                管理员
              </Button>
            </div>
          </div>
          <Button type="submit" disabled={busy}>
            {busy ? "生成中…" : "生成"}
          </Button>
        </form>

        {created ? (
          <div className="space-y-2 rounded-lg border bg-muted/40 p-3 text-sm">
            <p className="text-muted-foreground">一次性链接，7 天内有效，发给他即可注册：</p>
            <code className="block break-all rounded bg-background px-2 py-1 text-xs">
              {typeof window === "undefined" ? "" : window.location.origin}/join/{created.token}
            </code>
            <p className="text-xs text-muted-foreground">角色：{created.role}</p>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function ContentTab({ token, onError }: { token: string; onError: (msg: string | null) => void }) {
  const [projects, setProjects] = useState<AdminProject[]>([]);
  const [status, setStatus] = useState<"ACTIVE" | "REMOVED" | undefined>(undefined);

  const load = useCallback(async () => {
    try {
      setProjects(await api.adminProjects(status, token));
    } catch (err) {
      onError(err instanceof ApiError ? messageFor(err.code) : "加载失败");
    }
  }, [status, token, onError]);

  useEffect(() => {
    void load();
  }, [load]);

  async function remove(item: AdminProject) {
    if (!window.confirm(`下架「${item.title}」？前台将看不到，数据保留。`)) {
      return;
    }
    try {
      await api.removeProject(item.id, token);
      await load();
    } catch (err) {
      onError(err instanceof ApiError ? messageFor(err.code) : "下架失败");
    }
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle className="text-base">内容（{projects.length}）</CardTitle>
        <div className="flex gap-2">
          {([undefined, "ACTIVE", "REMOVED"] as const).map((value) => (
            <Button
              key={value ?? "all"}
              size="sm"
              variant={status === value ? "default" : "ghost"}
              onClick={() => setStatus(value)}
            >
              {value === undefined ? "全部" : value === "ACTIVE" ? "上架" : "已下架"}
            </Button>
          ))}
        </div>
      </CardHeader>
      <CardContent className="space-y-2">
        {projects.length === 0 ? (
          <p className="text-sm text-muted-foreground">没有内容。</p>
        ) : (
          projects.map((item) => (
            <div key={item.id} className="flex items-center justify-between rounded-lg border px-3 py-2">
              <div className="flex items-center gap-2">
                <Link href={`/projects/${item.id}`} className="font-medium hover:underline">
                  {item.title}
                </Link>
                <Badge variant={item.status === "REMOVED" ? "secondary" : "outline"}>
                  {item.status === "REMOVED" ? "已下架" : "上架"}
                </Badge>
                <span className="text-xs text-muted-foreground">{item.ownerUsername}</span>
              </div>
              {item.status === "ACTIVE" ? (
                <Button size="sm" variant="destructive" onClick={() => remove(item)}>
                  下架
                </Button>
              ) : null}
            </div>
          ))
        )}
      </CardContent>
    </Card>
  );
}

function FeedbackTab({ token, onError }: { token: string; onError: (msg: string | null) => void }) {
  const [items, setItems] = useState<AdminFeedback[]>([]);
  const [onlyNew, setOnlyNew] = useState(true);

  const load = useCallback(async () => {
    try {
      setItems(await api.adminFeedback(onlyNew ? "NEW" : undefined, token));
    } catch (err) {
      onError(err instanceof ApiError ? messageFor(err.code) : "加载失败");
    }
  }, [onlyNew, token, onError]);

  useEffect(() => {
    void load();
  }, [load]);

  async function markRead(item: AdminFeedback) {
    try {
      await api.markFeedbackRead(item.id, token);
      await load();
    } catch (err) {
      onError(err instanceof ApiError ? messageFor(err.code) : "操作失败");
    }
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle className="text-base">留言（{items.length}）</CardTitle>
        <Button size="sm" variant="ghost" onClick={() => setOnlyNew((value) => !value)}>
          {onlyNew ? "查看全部" : "只看未读"}
        </Button>
      </CardHeader>
      <CardContent className="space-y-2">
        {items.length === 0 ? (
          <p className="text-sm text-muted-foreground">没有留言。</p>
        ) : (
          items.map((item) => (
            <div key={item.id} className="space-y-1 rounded-lg border px-3 py-2">
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span className="font-medium text-foreground">{item.name}</span>
                {item.email ? <span>{item.email}</span> : null}
                <Badge variant={item.status === "NEW" ? "default" : "secondary"}>
                  {item.status === "NEW" ? "未读" : "已读"}
                </Badge>
                <time>{new Date(item.createdAt).toLocaleString("zh-CN")}</time>
                {item.status === "NEW" ? (
                  <Button size="sm" variant="ghost" onClick={() => markRead(item)}>
                    标记已读
                  </Button>
                ) : null}
              </div>
              <p className="whitespace-pre-wrap text-sm">{item.content}</p>
            </div>
          ))
        )}
      </CardContent>
    </Card>
  );
}
