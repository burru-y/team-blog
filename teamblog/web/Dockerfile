# ---- 依赖与构建 ----
FROM node:22-alpine AS build
WORKDIR /app

# 版本必须和本地一致：pnpm 11 会重写 lockfile，导致 --frozen-lockfile 失败
# corepack prepare 要从 npm registry 下 pnpm 本体，先指到国内源再装
RUN npm config set registry https://registry.npmmirror.com && \
    corepack enable && \
    corepack prepare pnpm@10.34.5 --activate

# 同样先拷 lockfile，让依赖层可缓存
COPY package.json pnpm-lock.yaml ./

# 构建时走国内镜像源：直连 registry.npmjs.org 在容器里经常 ECONNRESET，
# 而依赖层一旦失败整次构建就中断。只影响镜像构建，不改开发机的 .npmrc。
RUN pnpm config set registry https://registry.npmmirror.com

RUN pnpm install --frozen-lockfile

COPY . .
# standalone 产物：只带运行需要的文件，镜像小一个数量级（见 next.config.ts）
RUN pnpm build

# ---- 运行阶段 ----
FROM node:22-alpine AS runtime
ENV NODE_ENV=production \
    TZ=Asia/Shanghai \
    PORT=3000 \
    HOSTNAME=0.0.0.0

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

USER app
EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=3s --start-period=25s --retries=5 \
    CMD node -e "fetch('http://127.0.0.1:3000/').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "server.js"]
