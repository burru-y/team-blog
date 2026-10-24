import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

/**
 * Markdown 渲染（T-16）。
 *
 * <p>安全性来自"不用 rehype-raw"：react-markdown 默认**不解析 HTML**，
 * 正文里的 `<script>alert(1)</script>` 会被当成普通文本转义成 `&lt;script&gt;`，
 * 根本不会进入 DOM，自然谈不上执行。
 *
 * <p>如果哪天为了支持"在 Markdown 里写 HTML"而加上 rehype-raw，就必须同时接
 * rehype-sanitize，并且白名单里绝不能有 iframe / script / style。这两件事必须成对出现，
 * 单独加 rehype-raw 等于开了一个存储型 XSS。
 *
 * <p>链接统一 `rel="noopener noreferrer"` + `target="_blank"`：
 * - `noopener`：防止新页面通过 `window.opener` 反向控制本站
 * - `noreferrer`：不泄漏来源地址
 *
 * <p>URL 协议由 react-markdown 的默认 urlTransform 过滤，`javascript:` 会被清空。
 */
export function Markdown({ children }: { children: string }) {
  return (
    <div className="prose-teamblog space-y-4 text-sm leading-7">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ href, children: label }) => (
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary underline underline-offset-4"
            >
              {label}
            </a>
          ),
          pre: ({ children: code }) => (
            <pre className="overflow-x-auto rounded-lg bg-muted p-4 text-xs leading-6">{code}</pre>
          ),
          code: ({ children: text, className }) => (
            <code className={className ?? "rounded bg-muted px-1 py-0.5 text-xs"}>{text}</code>
          ),
          h1: ({ children: t }) => <h1 className="mt-6 text-2xl font-semibold">{t}</h1>,
          h2: ({ children: t }) => <h2 className="mt-6 text-xl font-semibold">{t}</h2>,
          h3: ({ children: t }) => <h3 className="mt-4 text-lg font-semibold">{t}</h3>,
          ul: ({ children: t }) => <ul className="list-disc space-y-1 pl-6">{t}</ul>,
          ol: ({ children: t }) => <ol className="list-decimal space-y-1 pl-6">{t}</ol>,
          blockquote: ({ children: t }) => (
            <blockquote className="border-l-2 border-border pl-4 text-muted-foreground">{t}</blockquote>
          ),
          table: ({ children: t }) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">{t}</table>
            </div>
          ),
          th: ({ children: t }) => (
            <th className="border-b px-2 py-1 text-left font-medium">{t}</th>
          ),
          td: ({ children: t }) => <td className="border-b px-2 py-1">{t}</td>,
        }}
      >
        {children}
      </ReactMarkdown>
    </div>
  );
}
