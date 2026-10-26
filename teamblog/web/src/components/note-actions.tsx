"use client";

import Link from "next/link";

import { buttonVariants } from "@/components/ui/button";
import { useSession } from "@/lib/session";

/**
 * 笔记页右上角的操作区：作者本人与管理员能看到「编辑」。
 *
 * 判断逻辑与后端一致（作者或管理员），但前端这层只是"少显示一个点不动的按钮"，
 * 真正的拦截在后端。
 */
export function NoteActions({
  noteId,
  ownerUsername,
}: {
  noteId: string;
  ownerUsername: string;
}) {
  const { user, loading } = useSession();

  if (loading || !user) {
    return null;
  }

  const canEdit = user.role === "ADMIN" || user.username === ownerUsername;
  if (!canEdit) {
    return null;
  }

  // Base UI 版 Button 没有 asChild，链接按钮直接用 buttonVariants 拼样式
  return (
    <Link
      href={`/learning/${noteId}/edit`}
      className={buttonVariants({ variant: "outline", size: "sm" })}
    >
      编辑
    </Link>
  );
}
