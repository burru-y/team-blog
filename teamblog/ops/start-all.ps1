# 一键启动：起容器栈，可选再开公网隧道。
#
# 为什么中文提示放在这里而不是 start.bat：cmd.exe 按系统 ANSI 代码页解析批处理，
# 用 UTF-8 存中文会把行切坏（报 ".bat 中 'L' 不是内部或外部命令" 这种错）。
# 批处理只留 ASCII，中文由 PowerShell 输出，两边都不踩编码坑。

param(
    [switch]$NoTunnel
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

function Write-Banner($text) {
    Write-Host ""
    Write-Host ("=" * 52)
    Write-Host "  $text"
    Write-Host ("=" * 52)
    Write-Host ""
}

Write-Banner "TeamBlog 一键启动"

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Host "[错误] 没找到 docker 命令。" -ForegroundColor Red
    Write-Host "        请先安装并启动 Docker Desktop：https://www.docker.com/products/docker-desktop/"
    exit 1
}

Write-Host "[1/2] 启动服务（首次会构建镜像，约 3 到 8 分钟）" -ForegroundColor Cyan
Write-Host ""
& (Join-Path $PSScriptRoot "compose\up.ps1")
if ($LASTEXITCODE -ne 0 -and $LASTEXITCODE -ne $null) {
    Write-Host ""
    Write-Host "[错误] 服务启动失败，看上面的输出。" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[2/2] 公网访问（临时隧道）" -ForegroundColor Cyan
Write-Host "      开启后本机必须保持开机；每次重启地址都会变。"

if ($NoTunnel) {
    Write-Host "      已跳过（-NoTunnel）"
} else {
    $answer = Read-Host "      输入 y 开启，直接回车跳过"
    if ($answer -eq "y") {
        Write-Host ""
        & (Join-Path $PSScriptRoot "tunnel\start-tunnel.ps1")
    } else {
        Write-Host "      已跳过"
    }
}

Write-Host ""
Write-Banner "完成"
Write-Host "  本地地址：https://localhost:8443"
Write-Host "  停止服务：双击 stop.bat"
Write-Host ""
