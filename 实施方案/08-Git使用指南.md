---
title: Git 使用指南
version: 1.2-companion
status: 可照做
date: 2026-10-23
based_on: ./07-开发规范.md
---

# Git 使用指南

> 面向本项目的四个人：命令怎么敲、出事了怎么救、哪些操作在这个团队里是红线。
> 与 [07-开发规范.md](./07-开发规范.md) §1 的分支/提交约定配套，冲突时以 07 为准。

---

## 0. 五分钟版（今天就能用）

```bash
git clone git@github.com:<org>/teamblog.git
cd teamblog
git switch -c feat/api-checkin        # 开一个自己的分支

# ……改代码……

git status                            # 看改了什么
git diff                              # 看具体内容
git add -p                            # 逐块确认要提交的改动
git commit -m "feat(api): 打卡支持连续天数"
git push -u origin HEAD               # 推到远程，终端会打印建 PR 的链接
```

然后在 GitHub 上点 **Compare & pull request** → 写描述 → 等 review → **Squash and merge** → **Delete branch**。

```bash
git switch main && git pull           # 回到最新主干
git branch -d feat/api-checkin        # 清理本地分支
```

日常 90% 的时间只用这 8 条命令。

---

## 1. 一次性配置

```bash
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"      # 与 GitHub 账号一致，否则提交不归你
git config --global init.defaultBranch main
git config --global pull.rebase true           # 拉取用 rebase，历史干净
git config --global fetch.prune true           # 自动清理远程已删分支
git config --global core.autocrlf input        # Windows 关键：避免 CRLF 污染
git config --global core.quotepath false       # 中文文件名正常显示
git config --global rebase.autoStash true      # rebase 前自动暂存现场
```

检查：

```bash
git config --global --list
```

### 1.1 SSH 免密（推荐，比 HTTPS + token 省心）

```powershell
ssh-keygen -t ed25519 -C "你的邮箱"     # 一路回车即可
cat $HOME\.ssh\id_ed25519.pub            # 复制输出
```

到 GitHub：**Settings → SSH and GPG keys → New SSH key**，粘贴，保存。

```powershell
# Windows 让 ssh-agent 开机自启并加载私钥（只需一次）
Get-Service ssh-agent | Set-Service -StartupType Automatic
Start-Service ssh-agent
ssh-add $HOME\.ssh\id_ed25519

ssh -T git@github.com      # 看到 "Hi <你的名字>!" 即成功
```

HTTPS 也能用，但每次要 Personal Access Token（Settings → Developer settings → Tokens，勾 `repo`），且密码不认。

---

## 2. 仓库怎么划（先定这个，否则后面很别扭）

你现在有两个目录：`D:\TeamBlog\实施方案`（文档）与 `D:\TeamBlog\teamblog`（代码）。

**建议：单仓。** 在 `D:\TeamBlog` 初始化，文档和代码同仓：

```bash
cd /d/TeamBlog
git init -b main
git add 实施方案
git commit -m "docs: 加入需求与实施方案文档"
git remote add origin git@github.com:<org>/team-blog.git
git push -u origin main
```

理由：文档里的相对链接（`./03-实施方案-v1.2.md`）在 GitHub 上能直接点开；方案改了代码跟着改，同一个 PR 里能一起看；CI 只配一次。

如果坚持**双仓**（文档公开、代码私有）：

| 做法 | 代价 |
|---|---|
| `实施方案/` 单独一个仓，`teamblog/` 单独一个仓 | 文档之间的相对链接正常，但**文档链到代码、代码链到文档会断**（需改用绝对 URL）；两个仓的 PR 无法联动 |

双仓时记得：文档仓的 `.gitignore` 不用管代码产物，代码仓的 `.gitignore` 见 06 号文档。

> 单仓目录名是中文（`实施方案`），GitHub 上 URL 会变成 `%E5%AE%9E...`，功能不受影响。介意的话建仓时改成 `docs/`，但要同步改 README 里的链接。

---

## 3. 每天的标准动作

### 3.1 开工前

```bash
git switch main
git pull --rebase          # 拿最新代码
git switch -c feat/web-heatmap
```

### 3.2 写代码时

```bash
git status                 # 随时看状态，比猜快
git diff                   # 未暂存的改动
git diff --staged          # 已暂存的改动
git add -p                 # 逐块挑选，避免把调试代码一起提交
```

`git add -p` 的交互键：`y` 要这块、`n` 不要、`s` 拆得更细、`q` 退出。

### 3.3 提交

```bash
git commit -m "feat(api): 打卡支持连续天数与账本发放"
```

想写多行说明（推荐，写"为什么"）：

```bash
git commit          # 打开编辑器，第一行标题，空一行写正文
```

### 3.4 推送与提 PR

```bash
git push -u origin HEAD        # 第一次带 -u，之后 git push 即可
```

GitHub 会在终端输出创建 PR 的链接；也可以直接去仓库页面点 **Compare & pull request**。

### 3.5 合并后清理

```bash
git switch main
git pull --rebase
git branch -d feat/web-heatmap     # 已合并的分支可以安全删除
```

### 3.6 每天下班前的三个检查

```bash
git status                 # 有没有忘记提交的半成品
git log --oneline -5       # 今天的提交信息是否讲得清
git branch -vv             # 自己的分支是否落后 origin/main
```

---

## 4. 本项目定的规矩（摘自 07，此处只列结论）

| 项 | 规则 |
|---|---|
| 分支名 | `feat/`、`fix/`、`chore/`、`docs/` + 简短英文，如 `feat/api-checkin` |
| 分支寿命 | ≤ 2 天；超过 5 天算设计问题 |
| 提交格式 | `type(scope): 中文描述`，`scope` ∈ `web/api/ai/db/ops/docs` |
| 一次提交 | 只做一件事；"顺便格式化"要单独提交 |
| 合并方式 | **Squash merge**，一个 PR 一条提交 |
| 保护 | `main` 禁止直接 push，CI 绿 + 1 人 approve 才能合 |
| 不允许 | `git push --force` 到 `main`；rebase 已推送的公共分支 |

---

## 5. 冲突怎么解

冲突不是错误，是两个人改了同一处。

```bash
git switch main && git pull
git switch feat/api-checkin
git rebase main            # 把 main 的新提交垫到自己分支下面

# 冲突 → 打开文件，会看到：
<<<<<<< HEAD              （main 上的内容）
const LIMIT = 20;
=======
const LIMIT = 50;         （你分支上的内容）
>>>>>>> feat/api-checkin
```

处理步骤：

1. 编辑文件，**删掉三行标记**，保留正确的那版（或合并两者）；
2. `git add <文件>`；
3. `git rebase --continue`；
4. 重复直到完成；
5. 因为改写了分支历史，推送要 `git push --force-with-lease`（**只对自己的分支**）。

想放弃重来：

```bash
git rebase --abort
```

建议：每次冲突解决后跑一次测试，冲突的语义错误往往不报错但会算错。

---

## 6. 撤销与救援手册

> 先记住：**只要提交过，几乎不会真丢**。`git reflog` 记着这一切。

### 6.1 改坏了还没提交

| 场景 | 命令 |
|---|---|
| 放弃某个文件的修改 | `git restore <file>` |
| 放弃全部未暂存修改 | `git restore .` |
| 已 `git add`，想取消暂存（保留改动） | `git restore --staged <file>` |
| 想把改动暂存起来去处理别的事 | `git stash push -m "wip"` → 回来 `git stash pop` |
| 想丢弃所有未跟踪的新文件 | `git clean -nd`（先看）→ `git clean -fd` |

### 6.2 提交写错了

| 场景 | 命令 | 注意 |
|---|---|---|
| 提交信息写错，还没 push | `git commit --amend -m "新信息"` | 改写了历史 |
| 忘了加一个文件，还没 push | `git add <file>` → `git commit --amend --no-edit` | |
| 想撤回这次提交但保留改动 | `git reset --soft HEAD~1` | |
| 想彻底扔掉这次提交 | `git reset --hard HEAD~1` | **改动会丢**，先 stash |
| 已经 push 到自己分支，要改 | `git commit --amend` → `git push --force-with-lease` | 只对自己分支用 |
| 已经 push 到 main | **不要 force**，用 `git revert <sha>` 反向提交 | 保护分支的历史不能改写 |

### 6.3 误删分支 / 误 reset

```bash
git reflog                      # 列出最近所有 HEAD 移动
git switch -c recovered <sha>   # 从丢失的提交拉回来
```

### 6.4 想单独拿别的分支的一个提交

```bash
git cherry-pick <sha>           # 常用在"线上急修，补回主干"
```

### 6.5 只想看某行是谁改的

```bash
git blame -L 40,60 src/main/java/com/teamblog/checkin/CheckinService.java
git log -p -- src/main/java/.../CheckinService.java     # 看这个文件的变更史
```

---

## 7. 团队红线

| # | 红线 | 后果 |
|---|---|---|
| 1 | 往 `main` 直接 push | 绕过评审与 CI，出事无法定位 |
| 2 | 对 `main` 用 `--force` | 别人拉到的一半历史会冲突甚至丢失提交 |
| 3 | 提交 `.env`、密钥、数据库导出 | 一旦进历史，删除也要重写历史，且通知全员重新 clone |
| 4 | 提交 `node_modules/`、`target/`、`uploads/` | 仓库体积爆炸，clone 变慢 |
| 5 | 一个 PR 塞三个不相关功能 | 评审无效，回滚也无法只回一个 |

已经提交了大文件怎么办（破坏性操作，需全员知会）：

```bash
# 首选 git-filter-repo（比 BFG 更现代）
pip install git-filter-repo
git filter-repo --path uploads/ --invert-paths
# 之后必须 force push，并通知所有人重新 clone
```

---

## 8. 常见报错对照

| 报错 | 原因 | 处理 |
|---|---|---|
| `rejected (fetch first)` | 远程有你没有的提交 | `git pull --rebase` 后再 push |
| `fatal: refusing to merge unrelated histories` | 两个独立初始化的仓库合并 | 确认要合并再加 `--allow-unrelated-histories` |
| `Your local changes would be overwritten` | 工作区有未提交改动 | `git stash` → 操作 → `git stash pop` |
| `Permission denied (publickey)` | SSH key 没配或没加载 | `ssh -T git@github.com`，检查 `ssh-add -l` |
| `CONFLICT (content): Merge conflict in ...` | 正常冲突 | 按 §5 解决 |
| `detached HEAD` | 切到了某个提交而不是分支 | `git switch -c <新分支名>` 把当前状态接住 |
| 提交作者不是自己 | email 配置不对 | `git config user.email ...`，历史提交需 rebase 重写 |
| 中文文件名显示成 `\344\270\255` | quotepath | `git config --global core.quotepath false` |
| 每次都要输密码 | 用了 HTTPS | 改用 SSH，或配 credential helper |

---

## 9. 可选但值得配

### 9.1 别名（少敲几个字）

```bash
git config --global alias.st "status -sb"
git config --global alias.lg "log --oneline --graph --decorate -20"
git config --global alias.last "log -1 --stat"
git config --global alias.unstage "restore --staged"
```

之后 `git st`、`git lg` 就能用。

### 9.2 提交前自动检查（团队统一用 lefthook 或 husky）

```yaml
# lefthook.yml（示例，放仓库根）
pre-commit:
  commands:
    format-java:
      glob: "api/**/*.java"
      run: ./api/mvnw -q spotless:apply && git add {staged_files}
    lint-web:
      glob: "web/**/*.{ts,tsx}"
      run: pnpm -C web lint
```

没有 hook 时，靠 CI 兜底也行——但本地跑快得多。

### 9.3 有用的只读命令

```bash
git log --oneline --graph --all          # 全局历史
git diff main...HEAD                     # 我的分支相对 main 改了什么
git show <sha> --stat                    # 某次提交动了哪些文件
git shortlog -sn                         # 每人提交了多少
git log --since="2 days ago" --author="你"
```

---

## 10. 一页速查

```bash
# 状态与差异
git status / git diff / git diff --staged / git lg

# 提交
git add -p / git commit -m "feat(api): ..." / git commit --amend

# 分支
git switch -c feat/x / git switch main / git branch -d feat/x / git branch -vv

# 同步
git pull --rebase / git fetch --prune / git push -u origin HEAD

# 撤销
git restore <f> / git restore --staged <f> / git reset --soft HEAD~1 / git revert <sha>

# 救援
git reflog / git stash list / git cherry-pick <sha> / git rebase --abort
```
