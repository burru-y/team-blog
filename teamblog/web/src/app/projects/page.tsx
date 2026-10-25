import Link from "next/link";

import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { serverApi } from "@/lib/server-api";

export const dynamic = "force-dynamic";

const TYPE_LABEL: Record<string, string> = {
  PROJECT: "项目",
  CERTIFICATE: "证书",
  AWARD: "奖项",
};

/** 成果流（公开）。已下架的不会出现——后端列表本身就过滤了。 */
export default async function ProjectsPage() {
  const page = await serverApi.listProjects(undefined, 20);

  return (
    <div className="space-y-10">
      <PageHeader
        size="large"
        title="团队成果"
        description="项目、证书与奖项。点进去可以看详细内容，作者本人与管理员可以下架。"
      />

      {page.items.length === 0 ? (
        <EmptyState
          title="还没有成果"
          description="登录后可以发布第一条项目或奖项。"
          action={
            <Link href="/publish" className={buttonVariants({ size: "sm" })}>
              去发布
            </Link>
          }
        />
      ) : (
        <ul className="divide-y border-t">
          {page.items.map((project) => (
            <li key={project.id}>
              <Link
                href={`/projects/${project.id}`}
                className="group flex flex-col gap-2 px-1 py-5 transition-colors hover:bg-muted/30 sm:flex-row sm:items-baseline sm:gap-6"
              >
                <div className="flex shrink-0 items-center gap-2 sm:w-32">
                  <span className="text-xs tracking-wide text-muted-foreground uppercase">
                    {TYPE_LABEL[project.type] ?? project.type}
                  </span>
                </div>

                <div className="min-w-0 flex-1 space-y-1">
                  <p className="font-medium group-hover:underline">{project.title}</p>
                  <p className="line-clamp-2 text-sm leading-relaxed text-muted-foreground">
                    {project.description}
                  </p>
                </div>

                <div className="shrink-0 text-xs text-muted-foreground sm:w-24 sm:text-right">
                  {project.ownerUsername}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
