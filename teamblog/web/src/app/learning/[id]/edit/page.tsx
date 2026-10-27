import { notFound } from "next/navigation";

import { NoteEditForm } from "@/components/note-edit-form";
import { ApiError } from "@/lib/api";
import { serverApi } from "@/lib/server-api";

export const metadata = { title: "编辑笔记 · 欲晓" };

// 需要读取真实内容，不能静态化
export const dynamic = "force-dynamic";

export default async function EditNotePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  let log;
  try {
    log = await serverApi.getLearningLog(id);
  } catch (error) {
    if (error instanceof ApiError && error.code === "NOT_FOUND") {
      notFound();
    }
    throw error;
  }

  return (
    <NoteEditForm
      noteId={log.id}
      ownerUsername={log.ownerUsername}
      initialTitle={log.title}
      initialContent={log.contentMd ?? ""}
      initialSummary={log.summaryMd ?? ""}
      initialTags={log.tags}
    />
  );
}
