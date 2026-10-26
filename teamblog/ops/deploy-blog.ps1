﻿# 构建并发布个人博客到服务器的 /blog 路径
#
#   .\deploy-blog.ps1
#
# 博客是纯静态站（Astro），构建产物是几个 HTML 文件，上传后 nginx 直接读，
# 不需要重启容器、不影响主站。
param(
  [string]$BlogDir = "D:\PersonalBlog",
  [string]$ServerHost = "47.111.71.156",
  [string]$KeyPath = "$env:USERPROFILE\.ssh\_yuxiao.pem"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BlogDir)) {
  throw "找不到博客目录：$BlogDir"
}

Write-Host "`n== 1/4 构建 ==" -ForegroundColor Cyan
Push-Location $BlogDir
try {
  & pnpm build | Out-Host
  if ($LASTEXITCODE -ne 0) { throw "构建失败" }
}
finally { Pop-Location }

$dist = Join-Path $BlogDir "dist"
if (-not (Test-Path (Join-Path $dist "index.html"))) {
  throw "构建产物里没有 index.html"
}

Write-Host "`n== 2/4 打包 ==" -ForegroundColor Cyan
$zip = Join-Path $env:TEMP "blog-dist.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
& tar.exe -a -c -f $zip -C $dist . | Out-Null
"包大小 {0:N1} KB" -f ((Get-Item $zip).Length / 1KB)

Write-Host "`n== 3/4 上传并解压 ==" -ForegroundColor Cyan
& scp -i $KeyPath -o StrictHostKeyChecking=no $zip "root@${ServerHost}:/root/blog-dist.zip"
if ($LASTEXITCODE -ne 0) { throw "上传失败" }

& ssh -i $KeyPath -o StrictHostKeyChecking=no root@$ServerHost `
  "mkdir -p /root/teamblog/ops/blog && rm -rf /root/teamblog/ops/blog/* && cd /root/teamblog/ops/blog && unzip -oq /root/blog-dist.zip && echo 解压完成"
if ($LASTEXITCODE -ne 0) { throw "解压失败" }

Write-Host "`n== 4/4 验证 ==" -ForegroundColor Cyan
& ssh -i $KeyPath -o StrictHostKeyChecking=no root@$ServerHost `
  "curl -s -o /dev/null -w '8081 %{http_code}\n' http://127.0.0.1:8081/"
if ($LASTEXITCODE -ne 0) { throw "验证失败" }

Write-Host "`n已发布：http://$ServerHost:8081/" -ForegroundColor Green
