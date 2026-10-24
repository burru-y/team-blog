# 一键起栈（Windows 本地）
#
#   powershell -ExecutionPolicy Bypass -File teamblog/ops/compose/up.ps1
#
# 做四件事：
#   1. 需要时启动 Docker Desktop，并等引擎就绪
#   2. 首次运行自动生成 compose/.env（随机密码与 JWT 密钥）
#   3. docker compose up -d --build
#   4. 等健康检查通过，然后打一次 /api/v1/health

param(
    # 强制重建镜像（改了 Dockerfile 或怀疑镜像过期时用）
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$composeDir = $PSScriptRoot
$repoRoot = Resolve-Path (Join-Path $composeDir "..\..\..")
$opsDir = Split-Path $composeDir -Parent
$envFile = Join-Path $composeDir ".env"    # compose 自动读取同目录下的 .env
$dockerExe = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
$dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"

function Test-Engine {
    # 只看输出，不看退出码：docker info 在引擎没起来时退出码仍是 0，
    # 只把错误写到 stderr（这个坑实际踩过一次，误判成"引擎已就绪"）
    $out = (& $dockerExe info --format "{{.ServerVersion}}" 2>&1 | Out-String)
    return ($out -match '^\s*\d+\.\d+')
}

function Wait-Engine {
    param([int]$Seconds = 240)
    for ($i = 0; $i -lt $Seconds; $i += 5) {
        if (Test-Engine) { return $true }
        Start-Sleep -Seconds 5
    }
    return $false
}

Write-Host "[up] 1/5 检查 Docker 引擎"
if (-not (Test-Path $dockerExe)) {
    throw "找不到 docker.exe，请先安装 Docker Desktop"
}

if (-not (Test-Engine)) {
    Write-Host "[up]      引擎未运行，启动 Docker Desktop（首次启动可能要几分钟）"
    if (-not (Test-Path $dockerDesktop)) { throw "找不到 Docker Desktop.exe" }
    Start-Process -FilePath $dockerDesktop | Out-Null
}

if (-not (Wait-Engine)) {
    throw "Docker 引擎 240 秒内没有就绪。可能是 WSL 还没生效——需要重启后重试。"
}
Write-Host "[up]      引擎就绪"

Write-Host "[up] 2/5 检查 .env"
if (-not (Test-Path $envFile)) {
    $bytes = New-Object byte[] 36
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $jwt = [Convert]::ToBase64String($bytes)
    $pw = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })

    $content = @"
# 由 up.ps1 自动生成；生产环境请自行更换
POSTGRES_DB=blog
POSTGRES_USER=blog
POSTGRES_PASSWORD=$pw
JWT_SECRET=$jwt
COOKIE_SECURE=true
HTTP_PORT=8088
HTTPS_PORT=8443

# 让所有 docker compose 命令自动带上 TLS 覆盖文件
COMPOSE_FILE=compose.yaml;compose.tls.yaml
"@
    [IO.File]::WriteAllText($envFile, $content, (New-Object Text.UTF8Encoding $false))
    Write-Host "[up]      已生成 compose/.env（随机密码与密钥）"
} else {
    Write-Host "[up]      compose/.env 已存在，沿用"
}

Write-Host "[up] 3/5 检查 TLS 证书"
$certDir = Join-Path (Split-Path $composeDir -Parent) "nginx\certs"
$certFile = Join-Path $certDir "fullchain.pem"
$keyFile = Join-Path $certDir "privkey.pem"
if (-not (Test-Path $certFile) -or -not (Test-Path $keyFile)) {
    Write-Host "[up]      证书不存在，生成自签证书（1 年有效期）"
    New-Item -ItemType Directory -Force -Path $certDir | Out-Null
    & $dockerExe run --rm -v "${certDir}:/certs" alpine/openssl req -x509 -nodes -newkey rsa:2048 -days 365 `
        -keyout /certs/privkey.pem -out /certs/fullchain.pem `
        -subj "/C=CN/O=TeamBlog/CN=localhost" `
        -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" | Out-Null
    if (-not (Test-Path $certFile)) { throw "证书生成失败" }
} else {
    Write-Host "[up]      证书已存在"
}

# 每个服务参与构建的源码路径（相对仓库根）
$buildSources = @{
    "teamblog-api" = @("teamblog\api\src", "teamblog\api\pom.xml", "teamblog\api\Dockerfile", "teamblog\api\mvnw")
    "teamblog-web" = @("teamblog\web\src", "teamblog\web\package.json", "teamblog\web\next.config.ts", "teamblog\web\Dockerfile")
    "teamblog-ai"  = @("teamblog\ai\app", "teamblog\ai\requirements.txt", "teamblog\ai\Dockerfile")
}

function Get-NewestWriteTime($paths) {
    $newest = [datetime]::MinValue
    foreach ($relative in $paths) {
        $full = Join-Path $repoRoot $relative
        if (Test-Path $full) {
            $item = Get-Item $full
            if ($item.PSIsContainer) {
                Get-ChildItem $full -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
                    if ($_.LastWriteTimeUtc -gt $newest) { $newest = $_.LastWriteTimeUtc }
                }
            } elseif ($item.LastWriteTimeUtc -gt $newest) {
                $newest = $item.LastWriteTimeUtc
            }
        }
    }
    return $newest
}

function Get-BuildDecision($service, $paths) {
    $image = & $dockerExe image inspect "${service}:latest" --format "{{.Created}}" 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $image) {
        return @{ Build = $true; Reason = "镜像不存在" }
    }
    # docker 的 Created 形如 2026-10-24T13:42:32.123456789Z，截到秒再解析
    $createdText = ([string]$image).Trim()
    if ($createdText.Length -gt 19) { $createdText = $createdText.Substring(0, 19) }
    $created = [datetime]::Parse($createdText, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::AdjustToUniversal)

    $newest = Get-NewestWriteTime $paths
    if ($newest -eq [datetime]::MinValue) {
        return @{ Build = $false; Reason = "没有找到源码，跳过" }
    }
    # 留 2 秒余量：源码与镜像的时间戳常常只差几百毫秒
    if ($created -gt $newest.AddSeconds(-2)) {
        return @{ Build = $false; Reason = "镜像比源码新" }
    }
    return @{ Build = $true; Reason = "源码比镜像新" }
}

Write-Host "[up] 4/5 构建并启动"
$needBuild = $false
foreach ($service in $buildSources.Keys) {
    if ($Force) {
        Write-Host ("      {0,-14} 强制重建（-Force）" -f $service)
        $needBuild = $true
        continue
    }
    $decision = Get-BuildDecision $service $buildSources[$service]
    Write-Host ("      {0,-14} {1}" -f $service, $decision.Reason)
    if ($decision.Build) { $needBuild = $true }
}

Push-Location $composeDir
try {
    if ($needBuild) {
        Write-Host "      执行构建（首次约 3 到 8 分钟，增量构建通常 1 分钟内）"
        & $dockerExe compose up -d --build
    } else {
        Write-Host "      源码没有变化，跳过构建（约 10 秒）"
        & $dockerExe compose up -d
    }
    if ($LASTEXITCODE -ne 0) { throw "compose up 失败" }
} finally {
    Pop-Location
}

Write-Host "[up] 5/5 等待健康检查"
$port = ((Get-Content $envFile | Where-Object { $_ -match '^HTTP_PORT=' }) -split '=')[1]
if (-not $port) { $port = "8088" }

$httpsPort = ((Get-Content $envFile | Where-Object { $_ -match '^HTTPS_PORT=' }) -split '=')[1]
if (-not $httpsPort) { $httpsPort = "8443" }

for ($i = 0; $i -lt 60; $i++) {
    # 用 curl 而不是 Invoke-WebRequest：自签证书会让 .NET 直接抛异常，
    # 而 -k 正好跳过校验（本地开发就该这样，生产用正式证书后自然校验通过）
    $body = (& curl.exe -sk -m 3 "https://127.0.0.1:$httpsPort/api/v1/health" 2>$null | Out-String)
    if ($body -match '"status"\s*:\s*"UP"') {
            Write-Host "[up]      健康检查通过：$($body.Trim())"
            Write-Host ""
            Write-Host "[up] 打开 https://localhost:$httpsPort"
            Write-Host "[up] 查看状态：docker compose ps"
            exit 0
    }
    Start-Sleep -Seconds 5
}

Write-Host "[up] 健康检查未通过，查看日志："
# COMPOSE_FILE 里是相对路径，必须在 compose 目录下执行
Push-Location $composeDir
try { & $dockerExe compose logs --tail 40 } finally { Pop-Location }
exit 1
