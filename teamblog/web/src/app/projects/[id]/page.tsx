import Link from "next/link";
import { notFound } from "next/navigation";

import { Comments } from "@/components/comments";
import { Markdown } from "@/components/markdown";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { serverApi } from "@/lib/server-api";

import { RemoveProjectButton } from "./remove-button";

export const dynamic = "force-dynamic";

const TYPE_LABEL: Record<string, string> = {
  PROJECT: "项目",
  CERTIFICATE: "证书",
  AWARD: "奖项",
};

/** 成果详情（公开）。已下架 = 404，不是"提示已下架"——不暴露"存在过"。 */
export default async function ProjectDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  let project;
  try {
    project = await serverApi.getProject(id);
  } catch (error) {
    if (error instanceof ApiError && error.code === "NOT_FOUND") {
      notFound();
    }
    throw error;
  }

  const comments = await serverApi
    .listComments("PROJECT", project.id)
    .catch(() => []);

  return (
    <article className="space-y-6">
      <header className="space-y-3">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Badge variant="outline">{TYPE_LABEL[project.type] ?? project.type}</Badge>
          <Link href={`/u/${project.ownerUsername}`} className="hover:underline">
            {project.ownerUsername}
          </Link>
          <span>·</span>
          <time>{new Date(project.createdAt).toLocaleDateString("zh-CN")}</time>
        </div>
        <h1 className="text-3xl font-semibold tracking-tight">{project.title}</h1>
        <p className="text-muted-foreground">{project.description}</p>
      </header>

      {project.imageUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={project.imageUrl} alt={project.title} className="max-h-96 rounded-lg border" />
      ) : null}

      {project.githubUrl ? (
        <a
          href={project.githubUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-block text-sm text-primary underline-offset-4 hover:underline"
        >
          {project.githubUrl}
        </a>
      ) : null}

      {project.contentMd ? <Markdown>{project.contentMd}</Markdown> : null}

      <Comments targetType="PROJECT" targetId={project.id} initial={comments} />

      <div className="flex items-center gap-3">
        <Link href="/projects" className={buttonVariants({ variant: "ghost" })}>
          ← 返回成果列表
        </Link>
        <RemoveProjectButton projectId={project.id} ownerUsername={project.ownerUsername} />
      </div>
    </article>
  );
}
