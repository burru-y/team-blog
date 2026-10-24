# 备份恢复演练（Windows 本地版）
#
# 与 ops/backup/backup.sh + restore.sh 做同样的事，只是直接调用本机的 pg_dump/pg_restore，
# 不依赖 Docker。用途：在没有容器环境的机器上验证"备份能不能还原"。
#
#   pwsh -File teamblog/ops/backup/drill.ps1
#
# 演练内容：导出 blog → 还原到 blog_drill → 逐表比对行数 → 删除临时库。

$ErrorActionPreference = "Stop"

$pgBin = "C:\Program Files\PostgreSQL\16\bin"
$psql = Join-Path $pgBin "psql.exe"
$pgDump = Join-Path $pgBin "pg_dump.exe"
$pgRestore = Join-Path $pgBin "pg_restore.exe"

$sourceDb = "blog"
$drillDb = "blog_drill"
$backupDir = Join-Path $PSScriptRoot "..\..\backup\db"
$stamp = Get-Date -Format "yyyy-MM-dd"
$dumpFile = Join-Path $backupDir "$stamp.dump"

$env:PGPASSWORD = "blog_dev_pw"
$env:PGCLIENTENCODING = "UTF8"

New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

Write-Host "[drill] 1/5 导出 $sourceDb -> $dumpFile"
& $pgDump -h localhost -U blog -d $sourceDb -Fc -Z6 -f "$dumpFile.tmp"
Move-Item -Force "$dumpFile.tmp" $dumpFile

$size = (Get-Item $dumpFile).Length
if ($size -le 0) { throw "备份文件为空" }
Write-Host ("[drill]      备份大小 {0:N0} 字节" -f $size)

Write-Host "[drill] 2/5 重建临时库 $drillDb"
& $psql -h localhost -U blog -d postgres -q -c "DROP DATABASE IF EXISTS $drillDb;"
& $psql -h localhost -U blog -d postgres -q -c "CREATE DATABASE $drillDb;"

Write-Host "[drill] 3/5 还原"
& $pgRestore -h localhost -U blog -d $drillDb --no-owner --clean --if-exists "$dumpFile"

$tables = @("users", "checkins", "point_events", "projects", "learning_logs", "comments", "feedbacks", "admin_invites")
Write-Host "[drill] 4/5 行数比对"
$mismatch = 0
foreach ($t in $tables) {
    $src = (& $psql -h localhost -U blog -d $sourceDb -t -A -c "select count(*) from $t;").Trim()
    $dst = (& $psql -h localhost -U blog -d $drillDb -t -A -c "select count(*) from $t;").Trim()
    $ok = ($src -eq $dst)
    if (-not $ok) { $mismatch++ }
    Write-Host ("          {0,-16} 源 {1,5}  还原 {2,5}  {3}" -f $t, $src, $dst, $(if ($ok) { "OK" } else { "不一致" }))
}

Write-Host "[drill] 5/5 清理临时库"
& $psql -h localhost -U blog -d postgres -q -c "DROP DATABASE IF EXISTS $drillDb;"

Remove-Item Env:\PGPASSWORD, Env:\PGCLIENTENCODING -ErrorAction SilentlyContinue

if ($mismatch -gt 0) {
    throw "演练失败：$mismatch 张表行数不一致"
}
Write-Host "[drill] 通过：备份可以还原，行数完全一致"
