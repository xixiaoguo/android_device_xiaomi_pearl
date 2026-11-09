#!/usr/bin/env bash
#
# setup.sh —— pearl (PixelOS / MT6896) 编译前准备（精简版）
#
# 只做三件事：
#
#   1  sync   按 <设备树>/custom.dependencies 拉取全部依赖仓库
#             （并重贴 patches/files 里的本地文件覆盖，依赖每次都会被 reset 到上游）
#   2  tools  体检编包所需的包，缺哪些就列出来（只提示，不安装）
#   3  hint   打印编译命令
#
# 用法：
#
#   bash setup.sh                 # 拉仓库 → 工具体检 → 打印编译命令
#   bash setup.sh sync            # 只拉仓库
#   bash setup.sh tools           # 只做工具体检
#   bash setup.sh hint            # 只打印编译命令
#   bash setup.sh -j8             # 拉仓库的并行数（默认 4）
#   bash setup.sh -s /path/src    # 指定源码根（默认自动向上探测 build/envsetup.sh）
#   bash setup.sh -d /path/pearl  # 指定设备树（默认自动探测含 device.mk 的目录）
#
# 退出码: 0 无失败项 / 1 有失败项 / 2 用法或环境错误
#
# 注意: 本文件必须保持 LF 换行（Windows 下带 CRLF 会 bad interpreter: bash^M）
#
set -uo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROG="setup.sh"

# ── 自我保护：行尾/BOM 自检 ───────────────────────────────────────────────────
# 本文件在 Windows 上编辑过，行尾可能被整份改成 CRLF，那样 bash 连跑都跑不起来。
_self_check() {
  local self="${BASH_SOURCE[0]}" cr
  [ -f "$self" ] || return 0
  cr="$(tr -cd '\r' < "$self" 2>/dev/null | wc -c | tr -d ' ')"
  [ -z "$cr" ] && cr=0
  if [ "$cr" != "0" ]; then
    printf '错误: %s 含 %s 个 CR 字符（行尾是 CRLF），bash 无法正常执行。\n' "$PROG" "$cr" >&2
    printf '修复: sed -i "s/\\r$//" "%s"\n' "$self" >&2
    exit 2
  fi
  if [ "$(head -c 3 "$self" 2>/dev/null | od -An -tx1 | tr -d ' \n')" = "efbbbf" ]; then
    printf '错误: %s 带 UTF-8 BOM，bash 无法解析。\n' "$PROG" >&2
    printf '修复: sed -i "1s/^\\xEF\\xBB\\xBF//" "%s"\n' "$self" >&2
    exit 2
  fi
}
_self_check
unset -f _self_check

# ── 输出助手 ──────────────────────────────────────────────────────────────────
if [ -n "${NO_COLOR:-}" ]; then
  RED=''; GRN=''; YEL=''; CYN=''; BLD=''; RST=''
else
  RED=$'\033[31m'; GRN=$'\033[32m'; YEL=$'\033[33m'; CYN=$'\033[36m'; BLD=$'\033[1m'; RST=$'\033[0m'
fi
say()  { printf '%s\n' "$*"; }
info() { printf '%s%s%s\n' "$CYN" "$*" "$RST"; }
note() { printf '         %s\n' "$*"; }
ok()   { printf '%s  [OK]%s    %s\n' "$GRN" "$RST" "$*"; OKS=$((OKS+1)); }
warn() { printf '%s  [WARN]%s  %s\n' "$YEL" "$RST" "$*"; WARNS=$((WARNS+1)); }
bad()  { printf '%s  [FAIL]%s  %s\n' "$RED" "$RST" "$*"; FAILS=$((FAILS+1)); }
step() { printf '\n%s==> %s%s\n' "$BLD" "$*" "$RST"; }
err()  { printf '%s%s%s\n' "$RED" "$*" "$RST" >&2; }
OKS=0; WARNS=0; FAILS=0

have() { command -v "$1" >/dev/null 2>&1; }

# ── 配置 ──────────────────────────────────────────────────────────────────────
SRC=""                  # 留空 = 自动向上探测源码根（含 build/envsetup.sh）
DEVICE_TREE_ARG=""      # 留空 = 自动探测设备树（含 device.mk 的目录）
JOBS=4                  # sync 并行任务数
DEPTH=1                 # clone 深度，1 = 只取最新一次提交，0 = 完整历史
WHAT="all"              # all / sync / tools / hint
PROTECTED_PATHS="device/xiaomi/pearl"
GITHUB_BASE="https://github.com"

show_help() {
  sed -n '2,/^set -uo pipefail$/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'
}

# ── 解析参数 ──────────────────────────────────────────────────────────────────
while [ $# -gt 0 ]; do
  case "$1" in
    all|sync|tools|hint) WHAT="$1" ;;
    -j)   shift; JOBS="${1:-4}" ;;
    -j*)  JOBS="${1#-j}" ;;
    -s)   shift; SRC="${1:-}" ;;
    -d)   shift; DEVICE_TREE_ARG="${1:-}" ;;
    -h|--help) show_help; exit 0 ;;
    *) err "未知参数: $1"; err "用 $PROG --help 查看用法"; exit 2 ;;
  esac
  shift
done
case "$JOBS" in ''|*[!0-9]*|0) JOBS=4 ;; esac
case "$DEPTH" in ''|*[!0-9]*) DEPTH=1 ;; esac

# ── 定位源码根 ────────────────────────────────────────────────────────────────
detect_src() {
  local d="$1"
  while [ -n "$d" ] && [ "$d" != "/" ]; do
    [ -f "$d/build/envsetup.sh" ] && { printf '%s' "$d"; return 0; }
    d="$(dirname "$d")"
  done
  return 1
}

if [ -z "$SRC" ]; then
  for start in "$PWD" "$SELF_DIR"; do
    SRC="$(detect_src "$start")" && [ -n "$SRC" ] && break
    SRC=""
  done
fi
[ -n "$SRC" ] || { err "没找到源码根（含 build/envsetup.sh），用 -s /path 指定"; exit 2; }
SRC="$(cd "$SRC" && pwd)" || exit 2

CODENAME="$(basename "$SELF_DIR")"
case "$SELF_DIR" in */device/*/*) : ;; *) CODENAME="" ;; esac

# 设备树可能在三个位置：脚本自己就在里面 / 源码根 device/xiaomi/<代号> / 脚本旁边的子目录
resolve_device_tree() {
  local c hits=()
  [ -f "$SELF_DIR/device.mk" ] && { printf '%s' "$SELF_DIR"; return 0; }
  if [ -n "$CODENAME" ] && [ -f "$SRC/device/xiaomi/$CODENAME/device.mk" ]; then
    printf '%s' "$SRC/device/xiaomi/$CODENAME"; return 0
  fi
  for c in "$SELF_DIR"/*/device.mk; do
    [ -f "$c" ] && { dirname "$c"; return 0; }
  done
  while IFS= read -r c; do hits+=("$(dirname "$c")"); done \
    < <(find "$SRC/device" -maxdepth 3 -name device.mk 2>/dev/null)
  [ "${#hits[@]}" -eq 1 ] && { printf '%s' "${hits[0]}"; return 0; }
  return 1
}

if [ -n "$DEVICE_TREE_ARG" ]; then
  DEVICE_TREE="$(cd "$DEVICE_TREE_ARG" 2>/dev/null && pwd)" \
    || { err "设备树路径不存在: $DEVICE_TREE_ARG"; exit 2; }
else
  DEVICE_TREE="$(resolve_device_tree)" \
    || { err "没找到设备树（含 device.mk 的目录），用 -d /path/pearl 指定"; exit 2; }
fi
CODENAME="$(basename "$DEVICE_TREE")"
REL_DEVICE="${DEVICE_TREE#"$SRC"/}"
[ "$REL_DEVICE" = "$DEVICE_TREE" ] && REL_DEVICE="$DEVICE_TREE（源码树外）"

# 探测可用的 python（Linux 上是 python3；Windows/Git Bash 上常常只有 python，
# 且 WindowsApps 下的 python3 可能只是个返回非 0 的商店占位符，必须真跑一下才算数）
PY_BIN=""
for _p in python3 python py; do
  command -v "$_p" >/dev/null 2>&1 || continue
  if "$_p" -c 'import json' >/dev/null 2>&1; then PY_BIN="$_p"; break; fi
done
unset _p

# ══════════════════════════════════════════════════════════════════════════════
# 步骤 1：拉取依赖仓库
# ══════════════════════════════════════════════════════════════════════════════
# 仓库清单唯一来源：<设备树>/custom.dependencies
#   格式: [{"repository": "<org>/<repo>", "target_path": "<src 下路径>", "branch": "<分支>"}, ...]
REPOS=()

is_protected() {
  case " $PROTECTED_PATHS " in *" $1 "*) return 0 ;; esac
  return 1
}

load_deps_from_file() {
  local f="$1"
  [ -n "$PY_BIN" ] || { err "解析 $f 需要能用的 python3（未找到可执行的 python3/python）"; return 1; }
  # tr -d '\r'：Windows 上的 python 会把 print 的 \n 写成 \r\n，不删掉会让
  # branch 变成 "master\r"，git 报 "Remote branch master? not found"。
  # set -o pipefail 保证 python 的失败码仍能传出去。
  "$PY_BIN" - "$f" <<'PY' | tr -d '\r'
import json, sys

# 注意用 utf-8-sig：清单在 Windows 上被编辑过时可能带 UTF-8 BOM
try:
    data = json.load(open(sys.argv[1], encoding="utf-8-sig"))
except Exception as exc:
    sys.stderr.write("解析失败: %s\n" % exc); sys.exit(1)

if not isinstance(data, list) or not data:
    sys.stderr.write("清单为空，或顶层不是 JSON 数组\n"); sys.exit(1)

errs = []
for i, d in enumerate(data):
    if not isinstance(d, dict):
        errs.append("#%d 不是 JSON 对象" % i); continue
    miss = [k for k in ("repository", "target_path", "branch") if not str(d.get(k) or "").strip()]
    if miss:
        errs.append("#%d（%s）缺少字段: %s" % (i, d.get("repository") or "?", ", ".join(miss)))
if errs:
    sys.stderr.write("清单不完整（branch 必须显式写明）:\n  " + "\n  ".join(errs) + "\n")
    sys.exit(2)

for d in data:
    print("%s|%s|%s" % (str(d["target_path"]).strip(), str(d["repository"]).strip(), str(d["branch"]).strip()))
PY
}

# 确保 origin 远程存在且 URL 正确（有些仓库远程名是 lineageos）
ensure_origin_remote() {
  local abs="$1" url="$2" cur="" r=""
  if cur="$(git -C "$abs" remote get-url origin 2>/dev/null)"; then
    if [ "$cur" != "$url" ] && [ "${cur%.git}" != "${url%.git}" ]; then
      warn "  修正 origin URL: $cur -> $url"
      git -C "$abs" remote set-url origin "$url" || return 1
    fi
    return 0
  fi
  r="$(git -C "$abs" remote 2>/dev/null | head -1)"
  if [ -n "$r" ]; then
    warn "  远程名不是 origin（当前为 $r），改名为 origin"
    git -C "$abs" remote rename "$r" origin || return 1
    git -C "$abs" remote set-url origin "$url" || return 1
    return 0
  fi
  warn "  没有任何远程，添加 origin -> $url"
  git -C "$abs" remote add origin "$url" || return 1
}

sync_one() {
  local path="$1" repo="$2" branch="$3"
  local url="$GITHUB_BASE/$repo" abs="$SRC/$path" depth_arg=()
  [ "$DEPTH" -gt 0 ] 2>/dev/null && depth_arg=(--depth="$DEPTH")

  if is_protected "$path"; then
    warn "[跳过] $path 受保护（你自己的设备树，不覆盖）"
    return 0
  fi
  info "==> $path  ($repo @ $branch)"

  if [ -d "$abs/.git" ]; then
    ensure_origin_remote "$abs" "$url" || return 1
    if [ -n "$(git -C "$abs" status --porcelain 2>/dev/null)" ]; then
      # 这些是拉下来的依赖仓库，为保证与上游一致，强制重置
      warn "  $path 有本地改动，fetch 后强制重置到上游"
      git -C "$abs" fetch "${depth_arg[@]}" origin "$branch" || return 1
      git -C "$abs" reset --hard FETCH_HEAD || return 1
    else
      git -C "$abs" fetch "${depth_arg[@]}" origin "$branch" || return 1
      git -C "$abs" checkout -q -B "$branch" FETCH_HEAD || return 1
    fi
  else
    if [ -d "$abs" ] && [ -n "$(ls -A "$abs" 2>/dev/null)" ]; then
      err "  $path 已存在且非空，但不是 git 仓库，未动它"
      return 1
    fi
    mkdir -p "$(dirname "$abs")"
    git clone "${depth_arg[@]}" --single-branch --branch "$branch" "$url" "$abs" || return 1
  fi

  # 关掉这个仓库的 autocrlf：全局 true 会把源码 checkout 成 CRLF
  [ -d "$abs/.git" ] && git -C "$abs" config core.autocrlf false >/dev/null 2>&1

  ok "  done: $(git -C "$abs" rev-parse --short HEAD 2>/dev/null) $(git -C "$abs" log -1 --pretty=%s 2>/dev/null | cut -c1-55)"
}

write_local_manifest() {
  [ -d "$SRC/.repo" ] || return 0
  local dir out entry path repo branch name fetch
  dir="$SRC/.repo/local_manifests"
  out="$dir/pearl-deps.xml"
  mkdir -p "$dir"
  {
    printf '%s\n' '<?xml version="1.0" encoding="UTF-8"?>'
    printf '%s\n' '<!-- generated by setup.sh; pearl device tree itself NOT included -->'
    printf '%s\n' '<manifest>'
    for entry in "${REPOS[@]-}"; do
      IFS='|' read -r path repo branch <<<"$entry"
      is_protected "$path" && continue
      name="${repo#*/}"
      fetch="$GITHUB_BASE/${repo%%/*}"
      printf '  <remote name="pearl-%s" fetch="%s" revision="%s" />\n' "$name" "$fetch" "$branch"
      if [ "$DEPTH" -gt 0 ]; then
        printf '  <project path="%s" name="%s" remote="pearl-%s" revision="%s" clone-depth="%s" />\n' \
          "$path" "$name" "$name" "$branch" "$DEPTH"
      else
        printf '  <project path="%s" name="%s" remote="pearl-%s" revision="%s" />\n' \
          "$path" "$name" "$name" "$branch"
      fi
    done
    printf '%s\n' '</manifest>'
  } > "$out"
  ok "已写入 ${out#$SRC/}"
}

# ── 本地文件替换：patches/files/<仓库相对路径> -> <源码根>/<同一路径> ──────────
# 依赖仓库每次都会 reset 到上游，所以每次拉完都要重贴。
PATCH_FILES_DIR="$DEVICE_TREE/patches/files"

overlay_patch_files() {
  local root="$PATCH_FILES_DIR" f rel dst n=0 same=0 fail=0
  if [ ! -d "$root" ]; then
    info "patches/files 不存在，跳过本地文件替换"
    return 0
  fi
  info "==> 重贴 patches/files 本地覆盖"
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    rel="${f#$root/}"
    case "$(basename "$rel")" in README.md|.gitkeep) continue ;; esac
    dst="$SRC/$rel"
    if [ -f "$dst" ] && cmp -s "$f" "$dst" 2>/dev/null; then
      same=$((same+1)); continue
    fi
    if [ ! -d "$(dirname "$dst")" ]; then
      warn "目标目录不存在（仓库没拉取或路径不同）: $(dirname "$rel")"
      fail=$((fail+1)); continue
    fi
    if cp -f "$f" "$dst"; then
      n=$((n+1))
    else
      bad "文件替换失败: $rel"
      fail=$((fail+1))
    fi
  done < <(find "$root" -type f 2>/dev/null | sort)

  [ "$n" -gt 0 ] && ok "已重贴 $n 个文件"
  [ "$same" -gt 0 ] && ok "$same 个文件已是最新（无需重贴）"
  [ "$fail" -gt 0 ] && return 1
  return 0
}

do_sync() {
  step "步骤 1/2 拉取依赖仓库"

  local deps="$DEVICE_TREE/custom.dependencies" parsed=""
  if [ ! -f "$deps" ]; then
    bad "找不到 $deps —— 依赖清单缺失，无法拉取"
    note '格式: [{"repository": "<org>/<repo>", "target_path": "<src 下路径>", "branch": "<分支>"}, ...]'
    return 1
  fi
  if ! parsed="$(load_deps_from_file "$deps")" || [ -z "$parsed" ]; then
    bad "$deps 解析失败、为空或字段不全"
    [ -n "$PY_BIN" ] && note "校验:  $PY_BIN -m json.tool $deps"
    return 1
  fi
  mapfile -t REPOS <<< "$parsed"
  ok "从 custom.dependencies 读到 ${#REPOS[@]} 个仓库"

  say ""
  say "  清单     : ${deps#$DEVICE_TREE/}（${#REPOS[@]} 个）"
  say "  并行/深度: $JOBS / $DEPTH（0=完整历史）"
  say ""

  local total="${#REPOS[@]}" idx=0 end pids=() i rc nfail=0 orc=0 tmpd
  while [ "$idx" -lt "$total" ]; do
    end=$(( idx + JOBS )); [ "$end" -gt "$total" ] && end=$total
    pids=()
    tmpd="$(mktemp -d)"
    for (( i=idx; i<end; i++ )); do
      local p_path p_repo p_branch
      IFS='|' read -r p_path p_repo p_branch <<<"${REPOS[$i]}"
      ( sync_one "$p_path" "$p_repo" "$p_branch"; echo $? > "$tmpd/rc.$i" ) > "$tmpd/log.$i" 2>&1 &
      pids+=($!)
    done
    for pid in "${pids[@]}"; do wait "$pid" 2>/dev/null || true; done
    for (( i=idx; i<end; i++ )); do
      cat "$tmpd/log.$i" 2>/dev/null
      rc="$(cat "$tmpd/rc.$i" 2>/dev/null || echo 1)"
      [ "$rc" != "0" ] && nfail=$(( nfail + 1 ))
    done
    rm -rf "$tmpd"
    idx=$end
  done

  write_local_manifest
  overlay_patch_files || orc=1

  # 汇总表
  say ""
  printf '  %-32s %-14s %-9s %s\n' "路径" "分支" "HEAD" "状态"
  printf '  %-32s %-14s %-9s %s\n' "--------------------------------" "--------------" "---------" "----"
  local path repo branch abs head cur
  for entry in "${REPOS[@]-}"; do
    IFS='|' read -r path repo branch <<<"$entry"
    abs="$SRC/$path"
    if is_protected "$path"; then
      printf '  %-32s %-14s %-9s %s\n' "$path" "$branch" "-" "受保护/跳过"
    elif [ -d "$abs/.git" ]; then
      head="$(git -C "$abs" rev-parse --short HEAD 2>/dev/null)"
      cur="$(git -C "$abs" rev-parse --abbrev-ref HEAD 2>/dev/null)"
      [ "$cur" = "$branch" ] && printf '  %-32s %-14s %-9s %s\n' "$path" "$branch" "$head" "OK" \
                            || printf '  %-32s %-14s %-9s %s\n' "$path" "$branch" "$head" "分支是 $cur"
    else
      printf '  %-32s %-14s %-9s %s\n' "$path" "$branch" "-" "缺失"
    fi
  done

  [ "$nfail" -gt 0 ] && bad "有 $nfail 个仓库处理失败"
  [ "$orc" -ne 0 ] && bad "patches/files 有替换失败项（明细见上方 [WARN]）"
  [ "$nfail" -eq 0 ] && [ "$orc" -eq 0 ] && ok "依赖源码全部就位"
  return 0
}

# ══════════════════════════════════════════════════════════════════════════════
# 步骤 2：编译工具体检（只提示，不安装）
# ══════════════════════════════════════════════════════════════════════════════
BUILD_PKGS=(
  bc bison build-essential curl flex g++-multilib gcc-multilib git git-lfs
  gnupg gperf lib32ncurses-dev lib32readline-dev lib32z1-dev libelf-dev
  liblz4-tool libncurses-dev libssl-dev libxml2 libxml2-utils lzop
  openjdk-21-jdk pngcrush protobuf-compiler python3 repo unzip xsltproc
  zip zlib1g-dev
)

# 包名 -> 代表性命令（有这个命令就认为包在位）。没有映射的是纯开发库，只能靠 dpkg 判定。
declare -A PKG_CMD=(
  [bc]=bc [bison]=bison [build-essential]=gcc [curl]=curl [flex]=flex
  [git]=git [git-lfs]=git-lfs [gnupg]=gpg [gperf]=gperf
  [liblz4-tool]=lz4 [libxml2-utils]=xmllint [lzop]=lzop
  [openjdk-21-jdk]=javac [pngcrush]=pngcrush [protobuf-compiler]=protoc
  [python3]=python3 [repo]=repo [unzip]=unzip [xsltproc]=xsltproc [zip]=zip
)

pkg_present() {
  local p="$1" c
  if [ "$p" = "python3" ]; then
    [ -n "$PY_BIN" ] && return 0
  fi
  if [ -n "${PKG_CMD[$p]+x}" ]; then
    c="${PKG_CMD[$p]}"
    have "$c" && return 0
    # 命令不在，但如果 dpkg 说包装了（比如只是 PATH 不全），也算在位
    if have dpkg; then dpkg -s "$p" >/dev/null 2>&1 && return 0; fi
    return 1
  fi
  # 纯开发库：有 dpkg 才判得准
  if have dpkg; then
    dpkg -s "$p" >/dev/null 2>&1 && return 0
    return 1
  fi
  return 2   # 无法判定
}

do_tools() {
  step "步骤 2/2 编译工具体检"

  local p missing=() unknown=()
  for p in "${BUILD_PKGS[@]}"; do
    pkg_present "$p"
    case "$?" in
      0) : ;;
      1) missing+=("$p") ;;
      2) unknown+=("$p") ;;
    esac
  done

  if [ "${#missing[@]}" -eq 0 ]; then
    ok "编包所需的包都在"
  else
    warn "缺 ${#missing[@]} 个包:"
    note "$(printf '%s\n' "${missing[@]}" | sort | tr '\n' ' ')"
  fi
  if [ "${#unknown[@]}" -gt 0 ]; then
    note "非 Debian/Ubuntu（无 dpkg），无法判定开发库: $(printf '%s\n' "${unknown[@]}" | sort | tr '\n' ' ')"
  fi
}

# ══════════════════════════════════════════════════════════════════════════════
# 编译命令提示
# ══════════════════════════════════════════════════════════════════════════════
build_lunch_target() {
  local prod rel f
  prod="$(grep -ho 'PRODUCT_NAME[[:space:]]*:=[[:space:]]*[A-Za-z0-9_]*' "$DEVICE_TREE"/*.mk 2>/dev/null \
          | head -1 | sed 's/.*:=[[:space:]]*//' | tr -d '\r')"
  [ -n "$prod" ] || prod="custom_${CODENAME:-pearl}"

  # PixelOS 的 release 段（如 cp2a）来自 vendor/custom/config
  rel=""
  for f in "$SRC"/vendor/custom/config/*.mk "$SRC"/vendor/custom/*.mk; do
    [ -f "$f" ] || continue
    rel="$(sed -n 's/^[[:space:]]*PRODUCT_RELEASE_NAME[[:space:]]*:=[[:space:]]*\([A-Za-z0-9_]*\).*/\1/p' "$f" | head -1 | tr -d '\r')"
    [ -n "$rel" ] && break
  done
  [ -n "$rel" ] || rel="cp2a"
  printf '%s-%s-user' "$prod" "$rel"
}

print_build_hint() {
  local lunch dev_seg
  lunch="$(build_lunch_target)"
  dev_seg="${CODENAME:-pearl}"

  say ""
  say "  ══════════════ 编译 ══════════════"
  say ""
  say "    cd $SRC"
  say "    . build/envsetup.sh"
  say "    lunch $lunch"
  say "    m pixelos -j\$(nproc --all)"
  say ""
  say "    产物: $SRC/out/target/product/$dev_seg/"
  say ""
}

# ══════════════════════════════════════════════════════════════════════════════
# 主流程
# ══════════════════════════════════════════════════════════════════════════════
say ""
info "$PROG —— pearl 编译前准备（精简版）"
say "  源码根  : $SRC"
say "  设备树  : $REL_DEVICE"
say "  将执行  : $WHAT"

t_start=$(date +%s)

case "$WHAT" in
  sync)  do_sync ;;
  tools) do_tools ;;
  hint)  : ;;
  *)
    do_sync
    do_tools
    ;;
esac

t_end=$(date +%s)

say ""
say "  OK: $OKS    WARN: $WARNS    FAIL: $FAILS    用时 $((t_end - t_start))s"

print_build_hint

[ "$FAILS" -gt 0 ] && exit 1
exit 0
