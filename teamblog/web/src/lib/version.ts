/**
 * 版本号与标题的处理。
 *
 * 发版时标题经常写成「v1.1 测试版」，而版本号本身单独显示，
 * 不做处理就会出现「v1.1 v1.1 测试版」这种重复。这里统一剥掉前缀。
 */

/** 去掉标题里已经等于当前版本号的前缀。 */
export function cleanVersionTitle(version: string | null, title: string): string {
  if (!version) {
    return title;
  }
  const escaped = version.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const stripped = title.replace(new RegExp(`^\\s*${escaped}\\s*[·:：-]?\\s*`, "i"), "").trim();
  return stripped.length > 0 ? stripped : title;
}

/** 「v1.1 测试版」→「v1.1 · 测试版」；标题为空时只返回版本号。 */
export function versionHeading(version: string | null, title: string): string {
  const rest = cleanVersionTitle(version, title);
  return rest === title ? title : `${version} · ${rest}`;
}
