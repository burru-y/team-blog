import Link from "next/link";
import { notFound } from "next/navigation";

import { AiChat } from "@/components/ai-chat";
import { Comments } from "@/components/comments";
import { Markdown } from "@/components/markdown";
import { SummaryGenerator } from "@/components/summary-generator";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { serverApi } from "@/lib/server-api";

export const dynamic = "force-dynamic";

/**
 * 学习内容详情 `/learning/{id}`（公开）。
 *
 * 正文走 {@link Markdown} 渲染——它默认不解析 HTML，所以正文里的 `<script>`
 * 只会以文字形式显示，不会执行（T-16）。
 */
export default async function LearningLogPage({ params }: { params: Promise<{ id: string }> }) {
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

  const comments = await serverApi
    .listComments("LEARNING_LOG", log.id)
    .catch(() => []);

  return (
    <article className="space-y-6">
      <header className="space-y-3">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Badge variant={log.type === "CODE" ? "secondary" : "outline"}>
            {log.type === "CODE" ? "代码" : "笔记"}
          </Badge>
          <Link href={`/u/${log.ownerUsername}`} className="hover:underline">
            {log.ownerUsername}
          </Link>
          <span>·</span>
          <time>{log.logDate}</time>
        </div>
        <h1 className="text-3xl font-semibold tracking-tight">{log.title}</h1>
        {log.summaryMd ? <p className="text-muted-foreground">{log.summaryMd}</p> : null}
      </header>

      <SummaryGenerator
        logId={log.id}
        ownerUsername={log.ownerUsername}
        title={log.title}
        contentMd={log.contentMd}
        initialSummary={log.summaryMd ?? null}
      />

      <Markdown>{log.contentMd}</Markdown>

      {/* 笔记也能问 AI：与项目详情页保持同一个入口位置（正文后、评论前） */}
      <AiChat noteId={log.id} />

      <Comments targetType="LEARNING_LOG" targetId={log.id} initial={comments} />

      <div>
        <Link href={`/u/${log.ownerUsername}`} className={buttonVariants({ variant: "ghost" })}>
          ← 回到 {log.ownerUsername} 的主页
        </Link>
      </div>
    </article>
  );
}
