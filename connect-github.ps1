# connect-github.ps1
# ASCII only on purpose: Windows PowerShell 5.1 reads BOM-less UTF-8 files as ANSI,
# which corrupts non-ASCII strings and breaks parsing.
# Run: powershell -ExecutionPolicy Bypass -File .\connect-github.ps1

# Do not use "Stop": PowerShell 5.1 turns native-command stderr into a terminating
# error, so a harmless "No such remote: origin" would abort the script.
# Exit codes are checked explicitly via $LASTEXITCODE instead.
$ErrorActionPreference = "Continue"

function Step([int]$n, [string]$t) {
    Write-Host ""
    Write-Host ("[" + $n + "] " + $t) -ForegroundColor Cyan
}

# ---------- 0. preflight ----------
Step 0 "Check git"
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host "git not found. Install it first: winget install --id Git.Git -e" -ForegroundColor Red
    exit 1
}
git --version

Set-Location "D:\TeamBlog"
Write-Host ("Working dir: " + (Get-Location))

# ---------- 1. identity ----------
Step 1 "Git identity"
$name = Read-Host "  GitHub user name"
if ([string]::IsNullOrWhiteSpace($name)) { Write-Host "name is required" -ForegroundColor Red; exit 1 }
$email = Read-Host "  GitHub email"
if ([string]::IsNullOrWhiteSpace($email)) { Write-Host "email is required" -ForegroundColor Red; exit 1 }

git config --global user.name "$name"
git config --global user.email "$email"
git config --global core.autocrlf input
git config --global core.quotepath false
Write-Host ("  set: " + $name + " <" + $email + ">")

# ---------- 2. ssh key ----------
Step 2 "SSH key"
$keyPath = Join-Path $HOME ".ssh\id_ed25519"
$pub = $keyPath + ".pub"
if (Test-Path $pub) {
    Write-Host ("  existing key: " + $pub)
} else {
    Write-Host "  generating key. Press Enter twice when asked for a passphrase."
    ssh-keygen -t ed25519 -C "$email" -f "$keyPath"
}

Write-Host ""
Write-Host "=== copy the single line below ===" -ForegroundColor Yellow
Get-Content $pub
Write-Host "=== into GitHub: Settings -> SSH and GPG keys -> New SSH key ===" -ForegroundColor Yellow
Read-Host "Press Enter after the key is saved on GitHub"

# ---------- 3. verify ssh ----------
Step 3 "Verify GitHub connectivity"
ssh -T git@github.com
if ($LASTEXITCODE -ne 1) {
    Write-Host "SSH failed (expected exit code 1 on success)." -ForegroundColor Red
    Write-Host "Check: public key added to the right account; port 22 reachable." -ForegroundColor Red
    exit 1
}
Write-Host "  SSH ok"

# ---------- 4. init and commit ----------
Step 4 "Init repo and commit"
if (-not (Test-Path ".git")) {
    git init -b main
    Write-Host "  initialized"
} else {
    Write-Host "  .git already exists, skipping init"
}

git add -A
$changes = git status --porcelain
Write-Host ("  files staged: " + ($changes | Measure-Object).Count)
Write-Host "  first 15 entries (must NOT contain target / node_modules / .env):" -ForegroundColor DarkGray
$changes | Select-Object -First 15

$msg = Read-Host "  commit message (Enter for default)"
if ([string]::IsNullOrWhiteSpace($msg)) { $msg = "chore: bootstrap repo with docs and api skeleton" }

if ($changes) {
    git commit -m "$msg"
} else {
    Write-Host "  nothing to commit"
}

# ---------- 5. remote and push ----------
Step 5 "Add remote and push"
Write-Host "  Create an EMPTY repo on GitHub first: New repository -> name team-blog"
Write-Host "  Do NOT tick README / .gitignore / license" -ForegroundColor Yellow
$url = Read-Host "  repo url (git@github.com:USER/team-blog.git)"
if ([string]::IsNullOrWhiteSpace($url)) { Write-Host "url is required" -ForegroundColor Red; exit 1 }

$remotes = git remote
if ($remotes -contains "origin") {
    git remote set-url origin "$url"
} else {
    git remote add origin "$url"
}
git remote -v
git branch -M main
git push -u origin main

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "DONE. Refresh the GitHub page to verify." -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "Push failed. If the remote already has commits:" -ForegroundColor Red
    Write-Host "  git pull --rebase origin main" -ForegroundColor Red
    Write-Host "  git push -u origin main" -ForegroundColor Red
}
