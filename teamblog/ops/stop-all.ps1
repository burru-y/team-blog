# 一键停止：关隧道、停容器。数据保留在 Docker 卷里。

$ErrorActionPreference = "Continue"
$composeDir = Join-Path $PSScriptRoot "compose"

Write-Host ""
Write-Host ("=" * 52)
Write-Host "  TeamBlog 停止"
Write-Host ("=" * 52)
Write-Host ""

Write-Host "[1/2] 关闭公网隧道"
$tunnel = Get-Process -Name cloudflared -ErrorAction SilentlyContinue
if ($tunnel) {
    $tunnel | Stop-Process -Force
    Write-Host "      已关闭"
} else {
    Write-Host "      隧道没有在运行"
}

Write-Host ""
Write-Host "[2/2] 停止容器（数据保留）"
Push-Location $composeDir
try {
    docker compose down
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "完成。下次启动双击 start.bat。"
Write-Host "数据不会丢；要清空数据请手动执行 docker compose down -v。" -ForegroundColor DarkGray
Write-Host ""
