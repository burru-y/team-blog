"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { api, ApiError } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { useSession } from "@/lib/session";
import type { UserResponse } from "@/lib/types";

/**
 * 编辑本人资料。
 *
 * 只在"看自己的主页"时出现：服务端组件读不到登录态，所以由这个客户端组件自行比对
 * username。比对放在组件内，页面本身仍是服务端渲染。
 *
 * 弹窗用原生 div 实现而不是引入 dialog 组件库：这里只需要一个遮罩加一块面板，
 * 为此增加一个依赖不划算。
 */
export function ProfileEditor({ profile }: { profile: UserResponse }) {
  const { user, token } = useSession();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [displayName, setDisplayName] = useState(profile.displayName ?? "");
  const [username, setUsername] = useState(profile.username);
  const [bio, setBio] = useState(profile.bio ?? "");
  const [githubUrl, setGithubUrl] = useState(profile.githubUrl ?? "");
  const [avatarUrl, setAvatarUrl] = useState(profile.avatarUrl ?? "");
  const [emailPublic, setEmailPublic] = useState(profile.emailPublic ?? false);

  useEffect(() => {
    if (!open) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open]);

  // 不是本人就不渲染。管理员也不显示：改他人资料不在本期范围。
  if (!user || user.username !== profile.username) {
    return null;
  }

  async function save() {
    if (!token) return;
    setSaving(true);
    setError(null);
    try {
      await api.updateProfile({ displayName, username, bio, githubUrl, avatarUrl, emailPublic }, token);
      setOpen(false);
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <Button variant="outline" size="sm" onClick={() => setOpen(true)}>
        编辑资料
      </Button>

      {open ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
          role="dialog"
          aria-modal="true"
          aria-label="编辑资料"
          onClick={() => setOpen(false)}
        >
          <div
            className="w-full max-w-lg space-y-4 rounded-lg border bg-background p-6 shadow-lg"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="space-y-1">
              <h2 className="text-lg font-semibold">编辑资料</h2>
              <p className="text-sm text-muted-foreground">修改后立即显示在个人主页上。</p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="displayName">显示名</Label>
                <Input
                  id="displayName"
                  maxLength={30}
                  value={displayName}
                  placeholder="小张"
                  onChange={(event) => setDisplayName(event.target.value)}
                />
                <p className="text-xs text-muted-foreground">页头与主页显示这个，可以中文</p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="username">用户名</Label>
                <Input
                  id="username"
                  maxLength={20}
                  value={username}
                  placeholder="zhang_san"
                  onChange={(event) => setUsername(event.target.value)}
                />
                <p className="text-xs text-muted-foreground">
                  3 到 20 位字母、数字、下划线；也是主页地址，改动后旧链接失效
                </p>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="bio">简介</Label>
              <Textarea
                id="bio"
                rows={4}
                maxLength={500}
                value={bio}
                placeholder="介绍一下你自己，比如在学什么、做什么方向"
                onChange={(event) => setBio(event.target.value)}
              />
              <p className="text-xs text-muted-foreground">{bio.length}/500</p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="github">GitHub 或个人主页</Label>
              <Input
                id="github"
                value={githubUrl}
                placeholder="https://github.com/your-name"
                onChange={(event) => setGithubUrl(event.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="avatar">头像地址</Label>
              <Input
                id="avatar"
                value={avatarUrl}
                placeholder="https://example.com/avatar.png"
                onChange={(event) => setAvatarUrl(event.target.value)}
              />
            </div>

            <label className="flex cursor-pointer items-center justify-between rounded-lg border p-3">
              <span className="space-y-0.5">
                <span className="block text-sm font-medium">公开邮箱</span>
                <span className="block text-xs text-muted-foreground">
                  开启后其他人能在你的主页看到邮箱
                </span>
              </span>
              <input
                type="checkbox"
                className="size-4 accent-foreground"
                checked={emailPublic}
                onChange={(event) => setEmailPublic(event.target.checked)}
              />
            </label>

            {error ? <p className="text-sm text-destructive">{error}</p> : null}

            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setOpen(false)} disabled={saving}>
                取消
              </Button>
              <Button onClick={save} disabled={saving}>
                {saving ? "保存中…" : "保存"}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
