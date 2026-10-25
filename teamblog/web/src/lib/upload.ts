/** 带进度的上传。fetch 拿不到上传进度，这里用 XHR。 */

export type UploadProgress = {
  /** 0–100 */
  percent: number;
};

export type UploadResult = {
  ok: boolean;
  /** 成功时的响应体（后端统一信封里的 data） */
  data?: unknown;
  /** 失败原因，直接给用户看 */
  message?: string;
  /** 后端错误码，如 VALIDATION_FAILED */
  code?: string;
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "";

/**
 * 上传单个文件并回调进度。
 *
 * 为什么用 XHR：`fetch` 没有上传进度事件（只有下载侧的 ReadableStream），
 * 而"上传 5MB 图片要等 3 秒且界面毫无变化"是最容易被当成卡死的情形。
 */
export function uploadFile(
  path: "/api/v1/uploads/images" | "/api/v1/uploads/code",
  file: File,
  token: string,
  onProgress?: (progress: UploadProgress) => void,
): Promise<UploadResult> {
  return new Promise((resolve) => {
    const request = new XMLHttpRequest();
    request.open("POST", `${API_BASE}${path}`);
    request.setRequestHeader("Authorization", `Bearer ${token}`);
    request.withCredentials = false;

    request.upload.addEventListener("progress", (event) => {
      if (!event.lengthComputable) {
        return;
      }
      onProgress?.({ percent: Math.round((event.loaded / event.total) * 100) });
    });

    request.addEventListener("load", () => {
      let body: { ok?: boolean; data?: unknown; error?: { code?: string; message?: string } } = {};
      try {
        body = JSON.parse(request.responseText);
      } catch {
        resolve({ ok: false, message: `上传失败（HTTP ${request.status}）` });
        return;
      }

      if (request.status >= 200 && request.status < 300 && body.ok !== false) {
        onProgress?.({ percent: 100 });
        resolve({ ok: true, data: body.data });
        return;
      }
      resolve({
        ok: false,
        code: body.error?.code,
        message: body.error?.message ?? `上传失败（HTTP ${request.status}）`,
      });
    });

    request.addEventListener("error", () => {
      resolve({ ok: false, message: "网络异常，上传中断" });
    });

    request.addEventListener("abort", () => {
      resolve({ ok: false, message: "已取消上传" });
    });

    const form = new FormData();
    form.append("file", file);
    request.send(form);
  });
}
