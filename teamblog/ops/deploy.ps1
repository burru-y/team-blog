# 本地一键部署到阿里云服务器
#
# 用法：
#   .\deploy.ps1              # 只重建有改动的服务（推荐先手动指定）
#   .\deploy.ps1 -Service web # 只重建前端
#   .\deploy.ps1 -Service api # 只重建后端
#   .\deploy.ps1 -All         # 三个服务全重建
#   .\deploy.ps1 -UploadOnly  # 只传代码，不构建（先看看服务器上是什么状态）
#
# 原理：把源码打成 zip 传上去解压，只重建改动的服务。Docker 会复用未变的层，
# 改前端时后端镜像直接跳过，通常 1-3 分钟完成。
param(
  [ValidateSet("web", "api", "ai")]
  [string]$Service = "",
  [switch]$All,
  [switch]$UploadOnly,
  [string]$ServerHost = "47.111.71.156",
  [string]$KeyPath = "$env:USERPROFILE\.ssh\_yuxiao.pem",
  [string]$RemoteDir = "/root/teamblog"
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$zip = Join-Path $repo "team-blog-src.zip"

function Step($text) { Write-Host "`n== $text ==" -ForegroundColor Cyan }

if (-not (Test-Path $KeyPath)) {
  throw "找不到 SSH 私钥：$KeyPath"
}

Step "打包源码"
if (Test-Path $zip) { Remove-Item $zip -Force }
Push-Location $repo
try {
  & tar.exe -a -c -f $zip `
    --exclude=node_modules --exclude=.next --exclude=target --exclude=.git `
    --exclude=__pycache__ --exclude=.venv --exclude=uploads --exclude=certs `
    teamblog docs 2>&1 | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "打包失败" }
}
finally { Pop-Location }
"包大小 {0:N1} MB" -f ((Get-Item $zip).Length / 1MB)

Step "上传到 $ServerHost"
& scp -i $KeyPath -o StrictHostKeyChecking=no -o ConnectTimeout=15 $zip "root@${ServerHost}:/root/team-blog-src.zip"
if ($LASTEXITCODE -ne 0) { throw "上传失败" }

Step "解压覆盖"
& ssh -i $KeyPath -o StrictHostKeyChecking=no root@$ServerHost "cd /root && unzip -oq team-blog-src.zip && echo 解压完成"
if ($LASTEXITCODE -ne 0) { throw "解压失败" }

if ($UploadOnly) {
  Write-Host "`n只上传，未构建。" -ForegroundColor Yellow
  exit 0
}

$compose = "docker compose -f compose.prod.yaml --env-file .env.prod"

if ($All -or -not $Service) {
  Step "重建全部服务（逐个，避免 2G 内存 OOM）"
  & ssh -i $KeyPath -o StrictHostKeyChecking=no root@$ServerHost "cd $RemoteDir/ops/compose && bash ../build-prod.sh"
} else {
  Step "只重建 $Service"
  & ssh -i $KeyPath -o StrictHostKeyChecking=no root@$ServerHost "cd $RemoteDir/ops/compose && $compose build $Service && $compose up -d $Service"
}
if ($LASTEXITCODE -ne 0) { throw "构建或启动失败，去服务器看日志" }

# 重建服务后容器的 IP 会变，而 nginx 在启动时就解析并缓存了 upstream 地址，
# 不重启它会一直把请求转发到旧 IP（表现为 502，但 api 自己健康）。
Step "重启 nginx（刷新 upstream 地址）"
& ssh -i $KeyPath -o StrictHostKeyChecking=no root@$ServerHost "cd $RemoteDir/ops/compose && $compose restart nginx"
Start-Sleep -Seconds 4

Step "健康检查"
& ssh -i $KeyPath -o StrictHostKeyChecking=no root@$ServerHost "curl -fsS -o /dev/null -w '首页 %{http_code}\n' http://127.0.0.1/ && curl -fsS http://127.0.0.1/api/v1/health"
if ($LASTEXITCODE -ne 0) {
  Write-Host "`n自检失败，看日志：" -ForegroundColor Red
  Write-Host "  ssh -i `"$KeyPath`" root@$ServerHost `"cd $RemoteDir/ops/compose && $compose logs --tail=60`""
  exit 1
}

Write-Host "`n部署完成：http://$ServerHost" -ForegroundColor Green
