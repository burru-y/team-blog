import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

const eslintConfig = [
  ...compat.extends("next/core-web-vitals", "next/typescript"),
  {
    ignores: [
      "node_modules/**",
      ".next/**",
      "out/**",
      "build/**",
      "next-env.d.ts",
      // 从 shadcn registry 安装的第三方组件：代码归项目所有，但不该让它们的
      // 写法（例如空接口）阻断整站构建。要改行为时直接改这些文件即可。
      "src/components/ui/**",
      "src/components/motion/**",
    ],
  },
];

export default eslintConfig;
