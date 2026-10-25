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
import { PageHeader } from "@/components/page-header";
import type {
  AdminFeedback,
  AdminProject,
  InviteCreated,
  InviteSummary,
  UserResponse,
} from "@/lib/types";

type Tab = "members" | "invites" | "content" | "feedback";

/** 邀请状态的中文标签。与后端 AdminInvite.Status 一一对应。 */
const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "有效",
  USED: "已使用",
  EXPIRED: "已过期",
  REVOKED: "已撤销",
};

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
    <div className="space-y-8">
      <PageHeader
        title="后台"
        description="成员与角色、邀请链接、内容下架、访客留言。"
      />

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

  async function resetPassword(target: UserResponse) {
    const next = window.prompt(`为 ${target.username} 设置新密码（至少 8 位）`);
    if (!next) {
      return;
    }
    setBusyId(target.id);
    try {
      await api.resetUserPassword(target.id, next, token);
      window.alert(`已重置。${target.username} 的所有登录已失效，请用新密码重新登录。`);
    } catch (err) {
      onError(err instanceof ApiError ? messageFor(err.code) : "重置失败");
    } finally {
      setBusyId(null);
    }
  }

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
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                disabled={busyId === item.id}
                onClick={() => resetPassword(item)}
              >
                重置密码
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={busyId === item.id}
                onClick={() => toggleRole(item)}
              >
                {item.role === "ADMIN" ? "降为成员" : "升为管理员"}
              </Button>
            </div>
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
  const [invites, setInvites] = useState<InviteSummary[]>([]);

  // 本机地址拼出来的邀请链接，发给同学是打不开的，所以要显式提醒
  const isLocalOrigin =
    typeof window !== "undefined" &&
    /^(https?:\/\/)?(localhost|127\.0\.0\.1|0\.0\.0\.0)(:|\/|$)/i.test(window.location.origin);

  const loadInvites = useCallback(async () => {
    try {
      setInvites(await api.listInvites(token));
    } catch (err) {
      onError(err instanceof ApiError ? messageFor(err.code) : "加载邀请列表失败");
    }
  }, [token, onError]);

  useEffect(() => {
    loadInvites();
  }, [loadInvites]);

  async function revoke(id: string) {
    setBusy(true);
    try {
      await api.revokeInvite(id, token);
      await loadInvites();
    } catch (err) {
      onError(err instanceof ApiError ? err.message || messageFor(err.code) : "撤销失败");
    } finally {
      setBusy(false);
    }
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      setCreated(await api.createInvite({ email: email.trim() || null, role }, token));
      setEmail("");
      await loadInvites();
    } catch (err) {
      // 后端会给出具体原因（例如"当前不允许签发管理员邀请"），
      // 用通用文案覆盖掉会让用户以为是"没有权限"，排查方向就错了
      onError(err instanceof ApiError ? err.message || messageFor(err.code) : "生成失败");
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

            {/* 链接用当前浏览器地址拼出来：在 localhost 上生成时，发给别人是打不开的 */}
            {isLocalOrigin ? (
              <p className="text-xs text-amber-600 dark:text-amber-400">
                注意：你现在用的是本机地址，这个链接别人打不开。
                请改用隧道或域名地址访问后台，再生成一次。
              </p>
            ) : null}
          </div>
        ) : null}
      </CardContent>

      <CardContent className="space-y-3 border-t pt-4">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">已签发的邀请</CardTitle>
            <span className="text-xs text-muted-foreground">最近 100 张 · 链接只在生成时显示一次</span>
          </div>

          {invites.length === 0 ? (
            <p className="text-sm text-muted-foreground">还没有签发过邀请。</p>
          ) : (
            <div className="divide-y rounded-lg border">
              {invites.map((invite) => (
                <div key={invite.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm">
                  <div className="flex items-center gap-2">
                    <Badge variant={invite.status === "ACTIVE" ? "default" : "secondary"}>
                      {STATUS_LABEL[invite.status] ?? invite.status}
                    </Badge>
                    <span className="text-muted-foreground">{invite.email ?? "未指定邮箱"}</span>
                    <Badge variant="outline">{invite.role === "ADMIN" ? "管理员" : "成员"}</Badge>
                  </div>

                  <div className="flex items-center gap-3 text-xs text-muted-foreground">
                    <span>创建 {new Date(invite.createdAt).toLocaleDateString("zh-CN")}</span>
                    {invite.status === "ACTIVE" ? (
                      <Button size="sm" variant="ghost" disabled={busy} onClick={() => revoke(invite.id)}>
                        撤销
                      </Button>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          )}
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
