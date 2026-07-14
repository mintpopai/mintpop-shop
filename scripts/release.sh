#!/usr/bin/env bash
# 组件发版：校验 → 同步版本号文件 → 打 tag → 先推 main 再推 tag（tag 触发 Release <组件> workflow）
# 用法：scripts/release.sh <backend|frontend> [版本号|说明] [说明]
set -euo pipefail

component="${1:?用法: release.sh <backend|frontend> [版本号|说明] [说明]}"
arg1="${2:-}"
arg2="${3:-}"

case "$component" in
  backend|frontend) ;;
  *) echo "未知组件：${component}（只支持 backend / frontend）" >&2; exit 1 ;;
esac

ver_re='^v?[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$'

# —— 参数消歧：两个都传 → 版本号+说明；只传一个 → 按形状判定 ——
version=""
notes=""
if [[ -n "$arg2" ]]; then
  version="$arg1"
  notes="$arg2"
  [[ "$version" =~ $ver_re ]] || { echo "第一个参数不是合法版本号：${version}" >&2; exit 1; }
elif [[ -n "$arg1" ]]; then
  if [[ "$arg1" =~ $ver_re ]]; then version="$arg1"; else notes="$arg1"; fi
fi

# 这次 fetch 同时供「自动算版本号」与「main 落后检查」复用
git fetch origin main --tags

# —— 发版前校验，任一不过即中止、绝不打 tag ——
branch="$(git rev-parse --abbrev-ref HEAD)"
[[ "$branch" == "main" ]] || { echo "当前不在 main（在 ${branch}），中止" >&2; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "工作区不干净，中止" >&2; exit 1; }
[[ -z "$(git rev-list HEAD..origin/main)" ]] || { echo "本地 main 落后 origin/main，先 git pull，中止" >&2; exit 1; }

# —— 缺省版本号：最新稳定 tag 的 patch+1（git 内置版本排序，禁 sort -V）——
if [[ -z "$version" ]]; then
  latest="$(git tag -l "${component}-v*" --sort=-v:refname \
    | grep -E "^${component}-v[0-9]+\.[0-9]+\.[0-9]+$" | head -n1 || true)"
  if [[ -z "$latest" ]]; then
    version="v0.1.0"
  else
    base="${latest#"${component}"-v}"
    IFS=. read -r major minor patch <<< "$base"
    version="v${major}.${minor}.$((patch + 1))"
  fi
fi
version="v${version#v}"
tag="${component}-${version}"

git rev-parse -q --verify "refs/tags/${tag}" >/dev/null \
  && { echo "tag 已存在：${tag}，中止" >&2; exit 1; }

# —— 同步版本号文件并提交（chore 前缀，不进变更日志）——
bare="${version#v}"
case "$component" in
  frontend)
    (cd frontend && pnpm version "$bare" --no-git-tag-version --allow-same-version)
    ;;
  backend)
    (cd backend && mvn -B -q versions:set -DnewVersion="$bare" -DgenerateBackupPoms=false)
    ;;
esac
if [[ -n "$(git status --porcelain)" ]]; then
  git add -A
  git commit -m "chore(${component}): 版本号更新为 ${bare}"
fi

# —— 打 tag（有说明 annotated / 无说明轻量），先推 main 再推 tag ——
if [[ -n "$notes" ]]; then
  git tag -a "$tag" -m "$notes"
else
  git tag "$tag"
fi
git push origin main && git push origin "$tag"
echo "已发版：${tag}（Release 由 tag 触发）"
