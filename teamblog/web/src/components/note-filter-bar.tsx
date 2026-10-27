"use client";

import { Search, X } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { tagChipStyle } from "@/lib/tag-colors";
import { cn } from "@/lib/utils";

/**
 * 笔记筛选条：关键词 + 作者 + 标签。
 *
 * 每次改动都写回 URL（router.replace），页面由服务端按参数重新渲染。
 * 不用客户端过滤：分页/分享/后退都要跟着地址走，交给路由更省心。
 */
export function NoteFilterBar({
  q,
  tag,
  author,
  authors,
  tags,
}: {
  q?: string;
  tag?: string;
  author?: string;
  authors: string[];
  tags: string[];
}) {
  const router = useRouter();
  const params = useSearchParams();
  const [keyword, setKeyword] = useState(q ?? "");

  function apply(next: Record<string, string | undefined>) {
    const search = new URLSearchParams(params.toString());
    for (const [key, value] of Object.entries(next)) {
      if (value) {
        search.set(key, value);
      } else {
        search.delete(key);
      }
    }
    const query = search.toString();
    router.replace(query ? `/notes?${query}` : "/notes");
  }

  const filtering = Boolean(q || tag || author);

  return (
    <div className="space-y-3 rounded-xl border p-4">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          apply({ q: keyword.trim() || undefined });
        }}
        className="flex flex-wrap items-center gap-2"
      >
        <div className="relative min-w-52 flex-1">
          <Search
            className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden
          />
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索标题或正文"
            className="pl-8"
            aria-label="搜索笔记"
          />
        </div>

        <select
          value={author ?? ""}
          onChange={(event) => apply({ author: event.target.value || undefined })}
          aria-label="按作者筛选"
          className="h-9 rounded-lg border bg-background px-2 text-sm"
        >
          <option value="">全部作者</option>
          {authors.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>

        <Button type="submit" size="sm">
          搜索
        </Button>

        {filtering ? (
          <Button
            type="button"
            size="sm"
            variant="ghost"
            onClick={() => {
              setKeyword("");
              router.replace("/notes");
            }}
          >
            <X className="mr-1 size-3.5" aria-hidden />
            清空
          </Button>
        ) : null}
      </form>

      {tags.length > 0 ? (
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-xs text-muted-foreground">标签</span>
          {tags.map((item) => {
            const active = tag === item;
            return (
              <button
                key={item}
                type="button"
                onClick={() => apply({ tag: active ? undefined : item })}
                className={cn(
                  "rounded-full border px-2 py-0.5 text-xs transition-all",
                  active ? "ring-1 ring-inset ring-foreground/25" : "hover:opacity-80",
                )}
                style={tagChipStyle(item)}
              >
                {item}
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}
