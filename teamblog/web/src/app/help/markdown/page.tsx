import Link from "next/link";
import { AlertTriangle, Download, FileText } from "lucide-react";

import { Markdown } from "@/components/markdown";
import { PageHeader } from "@/components/page-header";
import { buttonVariants } from "@/components/ui/button";
import { serverApi } from "@/lib/server-api";
import type { HelpFile } from "@/lib/types";

export const metadata = {
  title: "Markdown 速查 · 欲晓",
  description: "写学习笔记时常用的 Markdown 语法，含本站的特殊用法。",
};

/**
 * Markdown 语法速查（帮助页）。
 *
 * 内容用 Markdown 写、再用站内的渲染器显示——等于顺便验证了渲染器本身：
 * 这一页显示正常，说明用户写同样的语法也会正常。
 *
 * 用字符串数组拼接而不是模板字符串：正文里全是反引号和 ${}，
 * 写在反引号里要层层转义，很容易出错（第一次就是这么失败在语法解析上）。
 */
const GUIDE = [
  "## 一句话",
  "",
  "Markdown 用几个符号代替排版按钮：`#` 是标题，`**` 是加粗，`-` 是列表。",
  "写的时候只管内容，格式由符号决定。",
  "",
  "## 最常用的七个",
  "",
  "| 想要 | 写法 | 效果 |",
  "|---|---|---|",
  "| 大标题 | `# 标题` | 一级标题（一篇只用一个） |",
  "| 小标题 | `## 小节`、`### 小点` | 二级、三级标题 |",
  "| 加粗 | `**重要**` | **重要** |",
  "| 斜体 | `*强调*` | *强调* |",
  "| 删除线 | `~~删掉~~` | ~~删掉~~ |",
  "| 链接 | `[文字](https://example.com)` | 可点击的链接 |",
  "| 图片 | `![说明](图片地址)` | 显示一张图 |",
  "",
  "行内代码用反引号包起来：`const x = 1`。",
  "",
  "## 列表",
  "",
  "无序用 `-`，有序用数字加英文句点：",
  "",
  "```markdown",
  "- 第一项",
  "- 第二项",
  "  - 缩进两格是子项",
  "",
  "1. 第一步",
  "2. 第二步",
  "```",
  "",
  "- 第一项",
  "- 第二项",
  "  - 缩进两格是子项",
  "",
  "1. 第一步",
  "2. 第二步",
  "",
  "## 代码块",
  "",
  "三个反引号开头，紧跟语言名，会按语言高亮：",
  "",
  "````text",
  "```java",
  "public class Demo {",
  "    public static void main(String[] args) {",
  '        System.out.println("hi");',
  "    }",
  "}",
  "```",
  "````",
  "",
  "效果：",
  "",
  "```java",
  "public class Demo {",
  "    public static void main(String[] args) {",
  '        System.out.println("hi");',
  "    }",
  "}",
  "```",
  "",
  "**在本站不用手打代码块**：点编辑器里的「代码文件」上传按钮，选一个 `.java` / `.py` / `.ts` 文件，",
  "系统会自动把它转成带语言标注的代码块插进正文。",
  "",
  "## 引用与分割线",
  "",
  "```markdown",
  "> 引用一段话",
  "",
  "---",
  "```",
  "",
  "> 引用一段话",
  "",
  "---",
  "",
  "## 表格",
  "",
  "```markdown",
  "| 列一 | 列二 |",
  "|---|---|",
  "| 内容 | 内容 |",
  "```",
  "",
  "| 列一 | 列二 |",
  "|---|---|",
  "| 内容 | 内容 |",
  "",
  "第一行是表头，第二行的 `---` 分隔表头和内容，不能省。",
  "",
  "## 本站的两个特殊用法",
  "",
  "**插图**：点编辑器里的「图片」上传按钮，选图后自动插入：",
  "",
  "```markdown",
  "![图片](/uploads/xxxx.png)",
  "```",
  "",
  "图片存在我们自己的服务器上，不是外链，不会因为别人删图而失效。",
  "",
  "**贴代码文件**：点「代码文件」上传，自动插入带语言标注的代码块。",
  "",
  "## 写不出来时的三个办法",
  "",
  "1. **先写纯文本**。Markdown 兼容纯文本，段落之间空一行就会分段。",
  "2. **不要手动对齐**。表格和列表的对齐交给渲染器，敲空格没用。",
  "3. **符号用英文半角**。`，` 和 `,`、`（）` 和 `()` 是不同字符，中文符号不会被识别。",
  "",
  "## 如果想在本地写",
  "",
  "推荐 **Typora**：所见即所得，不用记符号。三个和本站有关的配置建议：",
  "",
  "| 配置项 | 建议 |",
  "|---|---|",
  "| 图片 | 选「复制到 ./文件名.assets 文件夹」 |",
  "| 语法偏好 | 全部勾选（Mermaid 除外，本站不渲染图表） |",
  "| 导出 | 配好 PDF 页眉页脚，导出时自动带标题和作者 |",
  "",
  "**图片那条最重要**：Typora 默认存绝对路径，换台电脑图片全裂。",
  "改成相对路径后，把 `.md` 和 `.assets` 文件夹一起发给别人，对方也能正常看到图。",
  "",
  "> 本站不支持 Mermaid 图表和数学公式，写进正文会显示成纯文本。需要图表时用截图代替。",
].join("\n");

// 下载区来自接口，禁止静态预渲染
export const dynamic = "force-dynamic";

function formatSize(bytes: number) {
  if (bytes >= 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

export default async function MarkdownHelpPage() {
  // 只取 Typora 相关文件：这个页面负责本地编辑工具，别的资料不该混进来
  const all = await serverApi.listHelpFiles().catch(() => [] as HelpFile[]);
  const files = all.filter((file) => /typora/i.test(file.name));
  return (
    <div className="space-y-8">
      <PageHeader
        size="large"
        title="Markdown 速查"
        description="写学习笔记用的语法。不用全记，写的时候回来查就行。"
        actions={
          <Link href="/publish/learning" className={buttonVariants({ size: "sm" })}>
            去写笔记
          </Link>
        }
        meta={<span>帮助 · 约 3 分钟读完</span>}
      />

      <Markdown>{GUIDE}</Markdown>

      <section id="downloads" className="space-y-4 border-t pt-8">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-lg font-semibold tracking-tight">本地编辑工具下载</h2>
          <span className="text-xs text-muted-foreground">
            {files.length > 0 ? `${files.length} 个文件` : "暂无文件"}
          </span>
        </div>

        {files.length === 0 ? (
          <div className="rounded-xl border border-dashed px-5 py-8 text-center text-sm text-muted-foreground">
            还没有上传文件，管理员可在「后台 → 帮助资料」添加。
          </div>
        ) : (
          <ul className="divide-y rounded-xl border">
            {files.map((file) => (
              <li key={file.name} className="flex flex-wrap items-center gap-3 px-4 py-3">
                <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
                  {file.name.endsWith(".pdf") ? (
                    <FileText className="size-4" />
                  ) : (
                    <Download className="size-4" />
                  )}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{file.name}</p>
                  <p className="text-xs text-muted-foreground">{formatSize(file.size)}</p>
                </div>
                <a
                  href={file.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="shrink-0 text-sm text-brand hover:underline"
                >
                  下载 ↓
                </a>
              </li>
            ))}
          </ul>
        )}

        {/* 声明必须显眼：这里面有非官方分发的软件 */}
        <div className="space-y-2 rounded-xl border border-amber-500/40 bg-amber-500/10 px-5 py-4 text-sm leading-relaxed text-amber-900 dark:text-amber-200">
          <p className="flex items-center gap-2 font-medium">
            <AlertTriangle className="size-4 shrink-0" aria-hidden />
            使用前请阅读
          </p>
          <ul className="list-disc space-y-1.5 pl-5">
            <li>
              <span className="font-medium">Typora 安装包为第三方激活版本</span>
              ，并非官方分发。仅供个人学习交流使用，请勿用于商业用途或再次传播。
            </li>
            <li>
              使用正版请前往官网
              <a
                href="https://typora.io/"
                target="_blank"
                rel="noopener noreferrer"
                className="mx-1 underline underline-offset-4"
              >
                typora.io
              </a>
              （约 ¥89，可激活 3 台设备）。这是对开发者最直接的支持。
            </li>
            <li>
              激活补丁可能被杀毒软件报毒或拦截，这是此类文件的常见现象。
              <span className="font-medium">是否使用由你自行判断，本站不承担由此产生的任何后果。</span>
            </li>
            <li>
              文件由管理员从网络收集，未做安全审计；如需彻底规避风险，请改用
              VS Code、Obsidian 等免费编辑器，或购买正版。
            </li>
            <li>
              若版权方或平台要求，本站会立即移除相关文件。
            </li>
          </ul>
        </div>
      </section>

      <div className="border-t pt-6">
        <Link href="/" className={buttonVariants({ variant: "ghost" })}>
          ← 返回首页
        </Link>
      </div>
    </div>
  );
}
