import Link from "next/link";

import { Badge } from "@/components/ui/badge";
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
    <div className="space-y-8">
      <header className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">团队成果</h1>
        <p className="text-sm text-muted-foreground">
          项目、证书与奖项。点进去可以看详细内容，作者本人与管理员可以下架。
        </p>
      </header>

      {page.items.length === 0 ? (
        <Card>
          <CardContent className="py-10 text-center text-sm text-muted-foreground">
            还没有成果。登录后可以发布第一条。
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {page.items.map((project) => (
            <Card key={project.id} className="card-lift hover:card-lift-hover hover:border-foreground/20">
              <CardHeader className="space-y-2">
                <div className="flex items-center gap-2">
                  <Badge variant="outline">{TYPE_LABEL[project.type] ?? project.type}</Badge>
                  <span className="text-xs text-muted-foreground">{project.ownerUsername}</span>
                </div>
                <CardTitle className="text-base">
                  <Link href={`/projects/${project.id}`} className="hover:underline">
                    {project.title}
                  </Link>
                </CardTitle>
              </CardHeader>
              <CardContent className="line-clamp-3 text-sm text-muted-foreground">
                {project.description}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
