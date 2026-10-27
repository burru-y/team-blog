import Link from "next/link";

import { PageHeader } from "@/components/page-header";
import { NoteFilterBar } from "@/components/note-filter-bar";
import { Badge } from "@/components/ui/badge";
import { tagChipStyle } from "@/lib/tag-colors";
import { serverApi } from "@/lib/server-api";
import type { LearningLogResponse } from "@/lib/types";

export const metadata = {
  title: "笔记 · 欲晓",
  description: "全部学习笔记，支持按关键词、标签、作者筛选。",
};

// 筛选条件来自 URL，必须动态渲染
export const dynamic = "force-dynamic";

/**
 * 全部笔记。
 *
 * 筛选走 URL 查询参数而不是组件状态：这样结果可以被分享、被浏览器前进后退，
 * 刷新也不会丢——列表页的筛选本来就该是地址的一部分。
 */
export default async function NotesPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; tag?: string; author?: string }>;
}) {
  const { q, tag, author } = await searchParams;

  const notes = await serverApi
    .listLearningLogsFiltered({ q, tag, author })
    .catch(() => [] as LearningLogResponse[]);

  // 作者与标签的可选值从结果里汇总，省一次请求
  const authors = [...new Set(notes.map((note) => note.ownerUsername))].sort();
  const tags = [
    ...new Set(
      notes.flatMap((note) =>
        (note.tags ?? "")
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean),
      ),
    ),
  ].sort();

  const filtering = Boolean(q || tag || author);

  return (
    <div className="space-y-6">
      <PageHeader
        size="large"
        title="笔记"
        description="成员写下的学习内容。按关键词、标签或作者筛选。"
        meta={<span>{notes.length} 篇</span>}
      />

      <NoteFilterBar q={q} tag={tag} author={author} authors={authors} tags={tags} />

      {notes.length === 0 ? (
        <div className="rounded-xl border border-dashed px-6 py-14 text-center">
          <p className="text-sm font-medium">{filtering ? "没有符合条件的笔记" : "还没有笔记"}</p>
          <p className="mt-1 text-xs text-muted-foreground">
            {filtering ? (
              <Link href="/notes" className="underline underline-offset-4">
                清空筛选条件
              </Link>
            ) : (
              "成员发布的学习内容会显示在这里。"
            )}
          </p>
        </div>
      ) : (
        <ul className="divide-y">
          {notes.map((note) => (
            <li key={note.id}>
              <Link
                href={`/learning/${note.id}`}
                className="group relative block rounded-lg px-3 py-5 transition-all duration-200 hover:translate-x-1 hover:bg-muted/40"
              >
                <span
                  aria-hidden
                  className="absolute top-5 bottom-5 left-0 w-0.5 origin-top scale-y-0 rounded bg-brand transition-transform duration-200 group-hover:scale-y-100"
                />
                <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                  <Link
                    href={`/notes?author=${encodeURIComponent(note.ownerUsername)}`}
                    className="hover:text-foreground hover:underline"
                  >
                    {note.ownerUsername}
                  </Link>
                  <span>·</span>
                  <time className="tabular-nums">{note.logDate}</time>
                  <Badge variant={note.type === "CODE" ? "secondary" : "outline"} className="text-xs">
                    {note.type === "CODE" ? "代码" : "笔记"}
                  </Badge>
                  {(note.tags ?? "")
                    .split(",")
                    .map((item) => item.trim())
                    .filter(Boolean)
                    .map((item) => (
                      <span
                        key={item}
                        className="rounded-full border px-2 py-0.5"
                        style={tagChipStyle(item)}
                      >
                        {item}
                      </span>
                    ))}
                </div>
                <h2 className="mt-1.5 text-base font-medium group-hover:text-brand">{note.title}</h2>
                {note.summaryMd ? (
                  <p className="mt-1 line-clamp-2 text-sm leading-relaxed text-muted-foreground">
                    {note.summaryMd}
                  </p>
                ) : null}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
