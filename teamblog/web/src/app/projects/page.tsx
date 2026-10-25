import Link from "next/link";

import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { TiltCard } from "@/components/motion/tilt-card";
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
        <div className="grid gap-4 sm:grid-cols-2">
          {page.items.map((project) => (
            <TiltCard key={project.id} className="border bg-card" max={6}>
              <Link href={`/projects/${project.id}`} className="flex h-full flex-col gap-3 p-5">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs tracking-wide text-muted-foreground uppercase">
                    {TYPE_LABEL[project.type] ?? project.type}
                  </span>
                  <span className="text-xs text-muted-foreground">{project.ownerUsername}</span>
                </div>

                <p className="font-medium">{project.title}</p>

                <p className="line-clamp-3 flex-1 text-sm leading-relaxed text-muted-foreground">
                  {project.description}
                </p>
              </Link>
            </TiltCard>
          ))}
        </div>
      )}
    </div>
  );
}
