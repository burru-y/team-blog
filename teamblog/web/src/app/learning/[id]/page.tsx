import Link from "next/link";
import { notFound } from "next/navigation";

import { AiChat } from "@/components/ai-chat";
import { ArticleToc } from "@/components/article-toc";
import { Comments } from "@/components/comments";
import { Markdown } from "@/components/markdown";
import { NoteActions } from "@/components/note-actions";
import { SummaryGenerator } from "@/components/summary-generator";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { extractToc, firstImage } from "@/lib/markdown-outline";
import { serverApi } from "@/lib/server-api";

export const dynamic = "force-dynamic";

/**
 * 学习内容详情 `/learning/{id}`（公开）。
 *
 * 布局是「正文 + 右侧目录」：目录由正文的 h2/h3 生成，
 * 没有小标题时不显示，避免出现空侧栏。
 *
 * 顶部封面用正文里的第一张图；没有图就用一层渐变，不留空白。
 * 正文走 {@link Markdown} 渲染——它默认不解析 HTML，所以 `<script>` 只会显示成文字（T-16）。
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

  const comments = await serverApi.listComments("LEARNING_LOG", log.id).catch(() => []);

  const toc = extractToc(log.contentMd);
  const cover = firstImage(log.contentMd);

  return (
    <article className="space-y-6">
      {/* 封面：有图用图，没图用渐变 */}
      <div className="relative -mx-2 h-40 overflow-hidden rounded-xl sm:h-56">
        {cover ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={cover} alt="" className="size-full object-cover" />
        ) : (
          <div className="size-full bg-gradient-to-br from-brand/15 via-brand/5 to-transparent" />
        )}
        <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-background/90 to-transparent" />
      </div>

      <header className="space-y-3">
        <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <Badge variant={log.type === "CODE" ? "secondary" : "outline"}>
            {log.type === "CODE" ? "代码" : "笔记"}
          </Badge>
          <Link href={`/u/${log.ownerUsername}`} className="hover:underline">
            {log.ownerUsername}
          </Link>
          <span>·</span>
          <time>{log.logDate}</time>
          {log.tags ? (
            <span className="flex flex-wrap gap-1">
              {log.tags.split(",").map((tag) => (
                <Link
                  key={tag}
                  href={`/u/${log.ownerUsername}?tag=${encodeURIComponent(tag.trim())}`}
                  className="rounded-full border px-2 py-0.5 text-xs hover:border-foreground/30 hover:text-foreground"
                >
                  #{tag.trim()}
                </Link>
              ))}
            </span>
          ) : null}
        </div>

        <div className="flex flex-wrap items-start justify-between gap-3">
          <h1 className="text-3xl font-semibold tracking-tight">{log.title}</h1>
          <NoteActions noteId={log.id} ownerUsername={log.ownerUsername} />
        </div>
        {log.summaryMd ? <p className="text-muted-foreground">{log.summaryMd}</p> : null}
      </header>

      <SummaryGenerator
        logId={log.id}
        ownerUsername={log.ownerUsername}
        title={log.title}
        contentMd={log.contentMd}
        initialSummary={log.summaryMd ?? null}
      />

      <div className="grid gap-10 xl:grid-cols-[1fr_14rem]">
        <div className="min-w-0 space-y-6">
          <Markdown>{log.contentMd}</Markdown>

          {/* 笔记也能问 AI：与项目详情页保持同一个入口位置（正文后、评论前） */}
          <AiChat noteId={log.id} />
        </div>

        {toc.length > 1 ? (
          <aside className="hidden xl:block">
            <div className="sticky top-20">
              <ArticleToc items={toc} />
            </div>
          </aside>
        ) : null}
      </div>

      <Comments targetType="LEARNING_LOG" targetId={log.id} initial={comments} />

      <div>
        <Link href={`/u/${log.ownerUsername}`} className={buttonVariants({ variant: "ghost" })}>
          ← 回到 {log.ownerUsername} 的主页
        </Link>
      </div>
    </article>
  );
}
