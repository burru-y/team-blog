# 启动 Cloudflare 快速隧道（免费、免注册），把本机的 HTTPS 服务暴露到公网。
#
#   powershell -ExecutionPolicy Bypass -File teamblog/ops/tunnel/start-tunnel.ps1
#
# 前提：docker compose 已经起来（https://localhost:8443 能访问）。
#
# 三个必须知道的限制：
#   1. 本机不能关机/睡眠——隧道在你电脑上
#   2. 每次启动地址都会变（免费版没有固定域名）
#   3. 没有 SLA，Cloudflare 随时可能断；断了重跑这个脚本即可

param(
    [int]$LocalPort = 8443,
    [int]$WaitSeconds = 40
)

$ErrorActionPreference = "Stop"

$candidates = @(
    "C:\Program Files (x86)\cloudflared\cloudflared.exe",
    "C:\Program Files\cloudflared\cloudflared.exe"
)
$cloudflared = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $cloudflared) {
    $cloudflared = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter 'cloudflared.exe' -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $cloudflared) {
    throw "找不到 cloudflared。先装：winget install --id Cloudflare.cloudflared -e"
}

# 本地服务先确认活着，否则隧道起来了也是 502
try {
    $health = (& curl.exe -sk -m 5 "https://127.0.0.1:$LocalPort/api/v1/health" 2>$null | Out-String)
    if ($health -notmatch '"UP"') { throw "本地健康检查不通过" }
    Write-Host "[tunnel] 本地服务正常"
} catch {
    throw "本地 https://127.0.0.1:$LocalPort 不可用。先跑 up.ps1 起栈。"
}

# 已有的隧道进程先清掉，避免同时开两条
Get-Process -Name cloudflared -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "[tunnel] 停掉旧隧道 pid=$($_.Id)"
    $_.Kill()
}
Start-Sleep -Seconds 2

$outLog = Join-Path $env:TEMP "cf-tunnel-out.log"
$rawLog = Join-Path $env:TEMP "cf-tunnel-raw.log"
Remove-Item $outLog, $rawLog -Force -ErrorAction SilentlyContinue

Write-Host "[tunnel] 启动隧道 -> https://localhost:$LocalPort"
# --no-tls-verify：本地是自签证书，Cloudflare 到你这端需要忽略校验。
# 公网那一侧 Cloudflare 会自动提供正式证书，同学不会看到警告。
$process = Start-Process -FilePath $cloudflared `
    -ArgumentList 'tunnel', '--url', "https://localhost:$LocalPort", '--no-tls-verify', '--logfile', $rawLog, '--loglevel', 'info' `
    -RedirectStandardOutput $outLog -RedirectStandardError (Join-Path $env:TEMP "cf-tunnel-err.log") `
    -PassThru -WindowStyle Hidden

Write-Host "[tunnel] 等待分配公网地址…"
$url = $null
for ($i = 0; $i -lt $WaitSeconds; $i++) {
    Start-Sleep -Seconds 1
    $all = (Get-Content $outLog, $rawLog -ErrorAction SilentlyContinue | Out-String)
    $match = [regex]::Matches($all, 'https://[a-z0-9-]+\.trycloudflare\.com') |
        ForEach-Object { $_.Value } | Select-Object -Unique -First 1
    if ($match) { $url = $match; break }
}

if (-not $url) {
    throw "没拿到公网地址。看日志：$rawLog"
}

Write-Host ""
Write-Host "  公网地址：$url" -ForegroundColor Green
Write-Host ""
Write-Host "  发给同学就能用（HTTPS，无证书警告）"
Write-Host "  验证：python teamblog\ops\compose\verify-public.py <地址>"
Write-Host ""
Write-Host "  注意：本机不能关机；重启后地址会变，重跑本脚本即可"
Write-Host "  停止：Get-Process cloudflared | Stop-Process"

$url | Set-Content (Join-Path $env:TEMP "cf-tunnel-url.txt")
