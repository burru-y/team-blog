"use client";

import { X } from "lucide-react";
import { useState } from "react";

import { Input } from "@/components/ui/input";
import { COMMON_TAGS, tagChipStyle } from "@/lib/tag-colors";
import { cn } from "@/lib/utils";

/**
 * 标签选择器。
 *
 * 三种入口并存：点常用标签、输入自定义、退格删最后一个。
 * 只让从固定列表里选会限制表达，只给输入框又会每次手打常用词。
 *
 * 上限 5 个，和后端一致（后端也会去重截断，这里只是提前给反馈）。
 */
export function TagPicker({
  value,
  onChange,
  max = 5,
  id = "tags",
}: {
  value: string[];
  onChange: (next: string[]) => void;
  max?: number;
  id?: string;
}) {
  const [draft, setDraft] = useState("");

  function add(raw: string) {
    const tag = raw.trim().replace(/^#/, "").slice(0, 20);
    if (!tag) {
      return;
    }
    if (value.includes(tag)) {
      setDraft("");
      return;
    }
    if (value.length >= max) {
      return;
    }
    onChange([...value, tag]);
    setDraft("");
  }

  function remove(tag: string) {
    onChange(value.filter((item) => item !== tag));
  }

  function toggle(tag: string) {
    if (value.includes(tag)) {
      remove(tag);
    } else {
      add(tag);
    }
  }

  return (
    <div className="space-y-2">
      {/* 已选标签 */}
      <div className="flex min-h-9 flex-wrap items-center gap-1.5 rounded-lg border px-2 py-1.5">
        {value.length === 0 ? (
          <span className="px-1 text-xs text-muted-foreground">还没有选择标签</span>
        ) : null}

        {value.map((tag) => (
          <span
            key={tag}
            className="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs"
            style={tagChipStyle(tag)}
          >
            {tag}
            <button
              type="button"
              onClick={() => remove(tag)}
              aria-label={`移除标签 ${tag}`}
              className="opacity-60 transition-opacity hover:opacity-100"
            >
              <X className="size-3" aria-hidden />
            </button>
          </span>
        ))}

        <input
          id={id}
          value={draft}
          maxLength={20}
          placeholder={value.length >= max ? `最多 ${max} 个` : "输入后回车添加"}
          disabled={value.length >= max}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === ",") {
              event.preventDefault();
              add(draft);
            } else if (event.key === "Backspace" && draft === "" && value.length > 0) {
              remove(value[value.length - 1]);
            }
          }}
          className="min-w-24 flex-1 border-0 bg-transparent px-1 text-sm outline-none placeholder:text-muted-foreground/70"
        />
      </div>

      {/* 常用标签 */}
      <div className="flex flex-wrap items-center gap-1.5">
        <span className="text-xs text-muted-foreground">常用</span>
        {COMMON_TAGS.map((tag) => {
          const picked = value.includes(tag);
          return (
            <button
              key={tag}
              type="button"
              onClick={() => toggle(tag)}
              aria-pressed={picked}
              disabled={!picked && value.length >= max}
              className={cn(
                "rounded-full border px-2 py-0.5 text-xs transition-all",
                picked ? "opacity-100" : "opacity-60 hover:opacity-100",
                "disabled:cursor-not-allowed disabled:opacity-30",
              )}
              style={picked ? tagChipStyle(tag) : { borderColor: "var(--border)" }}
            >
              {tag}
            </button>
          );
        })}
      </div>

      <p className="text-xs text-muted-foreground">
        最多 {max} 个，可用逗号或回车添加；已选的标签点一下可以取消。
      </p>
    </div>
  );
}

/** 把逗号分隔的字符串转成数组，供表单初始化用。 */
export function parseTags(raw: string | null | undefined): string[] {
  return (raw ?? "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

/** 数组转回后端要的逗号分隔字符串。 */
export function joinTags(tags: string[]): string | null {
  const cleaned = tags.map((tag) => tag.trim()).filter(Boolean);
  return cleaned.length > 0 ? cleaned.join(",") : null;
}
