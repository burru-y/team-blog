"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { ApiError, api } from "@/lib/api";
import { messageFor } from "@/lib/messages";
import { useSession } from "@/lib/session";

/**
 * 下架按钮。
 *
 * <p>只在"作者本人或管理员"时渲染——但这只是**体验**，不是安全边界：
 * 真正的判断在后端 Service（S-01 第三重）。前端藏起按钮挡不住直接调接口。
 */
export function RemoveProjectButton({
  projectId,
  ownerUsername,
}: {
  projectId: string;
  ownerUsername: string;
}) {
  const router = useRouter();
  const { user, token } = useSession();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canRemove = Boolean(token) && (user?.role === "ADMIN" || user?.username === ownerUsername);
  if (!canRemove) {
    return null;
  }

  async function handleRemove() {
    if (!token) {
      return;
    }
    if (!window.confirm("下架后前台将看不到这条成果（数据仍保留）。确定吗？")) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.removeProject(projectId, token);
      // 下架后详情接口返回 404，所以不能留在当前页
      router.push("/projects");
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? messageFor(err.code) : "操作失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-2">
      <Button variant="destructive" size="sm" onClick={handleRemove} disabled={busy}>
        {busy ? "下架中…" : "下架"}
      </Button>
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  );
}
