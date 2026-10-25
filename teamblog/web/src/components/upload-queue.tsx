"use client";

import { useCallback, useRef, useState } from "react";

import {
  FileUpload,
  type FileUploadItem,
  type FileUploadVariant,
} from "@/components/motion/file-upload";
import { uploadFile } from "@/lib/upload";
import { useSession } from "@/lib/session";

/**
 * 上传队列：拖拽或点选文件，逐个上传并显示进度。
 *
 * 组件本身只负责"排队与状态"，怎么传由这里决定；
 * 进度来自 XHR 的 upload.progress，用 fetch 是拿不到的。
 *
 * 一次只传一个文件：本项目上传的都是单张图片或单个代码文件，
 * 并发上传对后端存储没有意义，反而让进度条互相抢带宽、看起来乱。
 */
export type UploadKind = "image" | "code";

export type UploadedFile = {
  name: string;
  /** 后端返回的 data，字段随 kind 不同 */
  data: unknown;
};

export function UploadQueue({
  kind,
  accept,
  title,
  description,
  variant = "centered",
  onUploaded,
}: {
  kind: UploadKind;
  accept: string;
  title: string;
  description?: string;
  variant?: FileUploadVariant;
  onUploaded: (file: UploadedFile) => void;
}) {
  const { token } = useSession();
  const [items, setItems] = useState<FileUploadItem[]>([]);
  const runningRef = useRef(false);

  const path = kind === "image" ? "/api/v1/uploads/images" : "/api/v1/uploads/code";

  const uploadOne = useCallback(
    async (item: FileUploadItem) => {
      if (!token || !item.file) {
        return;
      }
      setItems((current) =>
        current.map((entry) =>
          entry.id === item.id ? { ...entry, status: "uploading", progress: 0 } : entry,
        ),
      );

      const result = await uploadFile(path, item.file, token, ({ percent }) => {
        setItems((current) =>
          current.map((entry) => (entry.id === item.id ? { ...entry, progress: percent } : entry)),
        );
      });

      if (!result.ok) {
        setItems((current) =>
          current.map((entry) =>
            entry.id === item.id
              ? { ...entry, status: "error", error: result.message ?? "上传失败" }
              : entry,
          ),
        );
        return;
      }

      setItems((current) =>
        current.map((entry) =>
          entry.id === item.id ? { ...entry, status: "success", progress: 100 } : entry,
        ),
      );
      onUploaded({ name: item.name, data: result.data });
    },
    [onUploaded, path, token],
  );

  const runQueue = useCallback(
    async (queue: FileUploadItem[]) => {
      if (runningRef.current) {
        return;
      }
      runningRef.current = true;
      try {
        for (const item of queue) {
          await uploadOne(item);
        }
      } finally {
        runningRef.current = false;
      }
    },
    [uploadOne],
  );

  return (
    <FileUpload
      value={items}
      variant={variant}
      onValueChange={setItems}
      accept={accept}
      maxFiles={1}
      title={title}
      description={description}
      onFilesAdded={(next, files) => {
        // 新加的文件带上 File 对象，组件自己建 items 时不会带
        const withFile = next.map((item, index) => ({ ...item, file: files[index] ?? item.file }));
        setItems(withFile);
        void runQueue(withFile);
      }}
      onRetry={(item) => void uploadOne(item)}
      onRemove={() => setItems([])}
    />
  );
}
