import { messageFor } from "./messages";

/**
 * SSE 客户端。
 *
 * 为什么不用 `EventSource`：它只能发 GET，而问答必须 POST（问题在 body 里）。
 * 所以手写 `fetch` + `reader.read()` 解析流。
 *
 * 服务端只发三种事件（02 方案 §7.3）：`delta` / `done` / `error`，前端只认这三种。
 */

export type StreamHandlers = {
  signal: AbortSignal;
  /** 登录用户的 access token：带上它 AI 服务才会给"成员档"（更强模型 + 更高额度）。 */
  token?: string | null;
  onDelta: (text: string) => void;
  onDone: () => void;
  onError: (message: string) => void;
};

export async function streamProjectChat(
  projectId: string,
  question: string,
  handlers: StreamHandlers,
): Promise<void> {
  let response: Response;
  try {
    response = await fetch("/ai/v1/project-chat", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(handlers.token ? { Authorization: `Bearer ${handlers.token}` } : {}),
      },
      body: JSON.stringify({ projectId, question }),
      signal: handlers.signal,
    });
  } catch {
    if (handlers.signal.aborted) {
      return; // 用户主动中止，不是错误
    }
    handlers.onError(messageFor("NETWORK"));
    return;
  }

  if (!response.ok) {
    handlers.onError(mapStatus(response.status));
    return;
  }
  if (!response.body) {
    handlers.onError("浏览器不支持流式响应");
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });

      // SSE 以空行分隔事件；一个 TCP 包里可能含多个事件，也可能只有半个
      let boundary = buffer.indexOf("\n\n");
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        handleBlock(block, handlers);
        boundary = buffer.indexOf("\n\n");
      }
    }
  } catch {
    if (!handlers.signal.aborted) {
      handlers.onError(messageFor("NETWORK"));
    }
  } finally {
    reader.releaseLock();
  }
}

function handleBlock(block: string, handlers: StreamHandlers): void {
  // 注释行（心跳）以冒号开头，直接忽略
  if (!block || block.startsWith(":")) {
    return;
  }

  let event = "";
  let data = "";
  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      data += line.slice(5).trim();
    }
  }
  if (!event) {
    return;
  }

  try {
    const payload = data ? JSON.parse(data) : {};
    if (event === "delta") {
      handlers.onDelta(payload.text ?? "");
    } else if (event === "done") {
      handlers.onDone();
    } else if (event === "error") {
      handlers.onError(payload.message ?? "AI 服务暂时不可用");
    }
  } catch {
    // 半截 JSON 不应该弄崩界面：丢弃这一块，等下一个完整事件
  }
}

/**
 * HTTP 状态 → 给人看的话。
 *
 * 分开写而不是统一"出错了"，是因为这几种情况的**用户动作完全不同**：
 * 额度用完要等明天、功能关闭要找管理员、网络断要检查连接。
 */
function mapStatus(status: number): string {
  switch (status) {
    case 429:
      return "今天的提问次数已用完，明天再来吧";
    case 503:
      return "AI 功能暂时关闭了";
    case 404:
      return "这个项目不存在或已下架";
    case 502:
      return "AI 服务暂时不可用，请稍后再试";
    default:
      return `请求失败（HTTP ${status}）`;
  }
}
