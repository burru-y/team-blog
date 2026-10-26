"use client";

import { useState } from "react";
import { ChevronDown, Bug, Sparkles, Wrench } from "lucide-react";

import { Markdown } from "@/components/markdown";
import { Badge } from "@/components/ui/badge";
import type { Announcement } from "@/lib/types";
import { cleanVersionTitle } from "@/lib/version";
import { cn } from "@/lib/utils";

/**
 * 版本改动分类折叠（方案 B）。
 *
 * 正文按 `### 标题` 切分成组（新增 / 改进 / 修复），每组可折叠。
 * 最新版本默认展开，历史版本默认收起——打开页面时视线直接落在「这次改了什么」。
 *
 * 为什么在客户端解析而不是后端存结构化数据：发版时写一条公告就够了，
 * 多存一份结构就多一处会不一致的地方。
 */
type Group = { title: string; body: string };

const ICONS: Record<string, React.ReactNode> = {
  新增: <Sparkles className="size-3.5" />,
  改进: <Wrench className="size-3.5" />,
  修复: <Bug className="size-3.5" />,
};

const TONES: Record<string, string> = {
  新增: "text-emerald-600 dark:text-emerald-400",
  改进: "text-sky-600 dark:text-sky-400",
  修复: "text-amber-600 dark:text-amber-400",
};

/** 把 Markdown 正文按 `### ` 切成组；没有小标题时整段算一组。 */
function splitGroups(bodyMd: string): Group[] {
  const lines = bodyMd.split("\n");
  const groups: Group[] = [];
  let current: Group | null = null;

  for (const line of lines) {
    const heading = /^###\s+(.+)$/.exec(line.trim());
    if (heading) {
      if (current) {
        groups.push(current);
      }
      current = { title: heading[1].trim(), body: "" };
      continue;
    }
    if (current) {
      current.body += line + "\n";
    }
  }
  if (current) {
    groups.push(current);
  }

  if (groups.length === 0) {
    return [{ title: "本次改动", body: bodyMd }];
  }
  return groups.map((group) => ({ ...group, body: group.body.trim() }));
}

function countBullets(body: string) {
  return body.split("\n").filter((line) => line.trim().startsWith("- ")).length;
}

export function VersionChangelog({ versions }: { versions: Announcement[] }) {
  const [openIds, setOpenIds] = useState<Set<string>>(() => {
    // 只展开最新一个版本
    const latest = versions[0];
    return new Set(latest ? [latest.id] : []);
  });

  function toggle(id: string) {
    setOpenIds((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  return (
    <div className="space-y-5">
      {versions.map((version, index) => {
        const groups = splitGroups(version.bodyMd);
        const open = openIds.has(version.id);
        const total = groups.reduce((sum, group) => sum + countBullets(group.body), 0);

        return (
          <article key={version.id} className="relative border-l-2 pl-5">
            <span
              aria-hidden
              className={cn(
                "absolute top-2 -left-[7px] size-3 rounded-full border-2",
                index === 0 ? "border-brand bg-brand" : "border-border bg-background",
              )}
            />

            <button
              type="button"
              onClick={() => toggle(version.id)}
              aria-expanded={open}
              className="flex w-full flex-wrap items-baseline gap-2 text-left"
            >
              <span className="font-mono text-base font-semibold">{version.version}</span>
              <span className="text-sm font-medium">
                {cleanVersionTitle(version.version, version.title)}
              </span>
              {index === 0 ? (
                <Badge variant="secondary" className="text-xs">
                  最新
                </Badge>
              ) : null}
              <time className="text-xs text-muted-foreground">
                {new Date(version.publishedAt).toLocaleDateString("zh-CN", {
                  timeZone: "Asia/Shanghai",
                })}
              </time>
              <span className="text-xs text-muted-foreground">共 {total} 项</span>
              <ChevronDown
                aria-hidden
                className={cn(
                  "ml-auto size-4 shrink-0 text-muted-foreground transition-transform",
                  open ? "rotate-180" : "",
                )}
              />
            </button>

            {open ? (
              <div className="mt-3 space-y-3">
                {groups.map((group) => (
                  <div key={group.title} className="rounded-lg border">
                    <p
                      className={cn(
                        "flex items-center gap-1.5 border-b px-3 py-2 text-xs font-medium",
                        TONES[group.title] ?? "text-muted-foreground",
                      )}
                    >
                      {ICONS[group.title] ?? null}
                      {group.title}
                      <span className="ml-auto font-normal text-muted-foreground">
                        {countBullets(group.body)} 项
                      </span>
                    </p>
                    <div className="px-3 py-2">
                      <Markdown>{group.body}</Markdown>
                    </div>
                  </div>
                ))}
              </div>
            ) : null}
          </article>
        );
      })}
    </div>
  );
}
