#!/usr/bin/env bash
#
# 静态防回归检查：本应用必须保持无广告。
#
# 为什么需要它：广告在官方客户端里是**客户端主动拉取**的（`ad_content_all` 与
# `advertise_all` 两个接口 + 60 多个 `adKey` 插槽），所以「无广告」这个性质
# 依赖于「我们永远不去碰它们」。这是一条容易被无意破坏的约束 ——
# 比如将来照着官方代码补功能时顺手把 adKey 抄进来。
#
# 检查项（出现在**代码**里即失败，注释与文档不受影响）：
#   - 广告接口路径：ad_content_all / advertise_all
#   - 广告插槽：adKey
#   - 广告素材字段：adv_id / adv_img / adv_link / adv_recommend / adv_title / adv_text
#
# 注释剥离是必需的：AdBlocker.kt 的文档里**必须**写明这些接口与字段名，
# 否则「防的是什么」就无从理解。因此检查前先剥掉 // 行注释与 /* */ 块注释。
#
set -euo pipefail
cd "$(dirname "$0")/.."

# 必须带词边界：裸子串匹配会把 reloadKey 这类正常标识符里的 "adKey" 也算命中，
# 误报会让检查逐渐被无视，比没有检查更糟。POSIX awk 没有 \y，故用显式字符类。
PATTERN='(^|[^A-Za-z0-9_])(ad_content_all|advertise_all|adKey|adv_id|adv_img|adv_link|adv_recommend|adv_title|adv_text)([^A-Za-z0-9_]|$)'

# 剥离注释后按模式匹配。用 awk 走一个小状态机，正确跨行处理块注释。
run_check() {
  find app/src/main/kotlin -name '*.kt' -print0 \
    | xargs -0 awk -v pat="$PATTERN" '
      BEGIN { inblk = 0 }
      {
        line = $0; out = ""; i = 1; n = length(line)
        while (i <= n) {
          if (inblk) {
            p = index(substr(line, i), "*/")
            if (p == 0) { i = n + 1 } else { i = i + p + 1; inblk = 0 }
          } else {
            rest = substr(line, i)
            lc = index(rest, "//")
            bc = index(rest, "/*")
            if (lc > 0 && (bc == 0 || lc < bc)) { out = out substr(rest, 1, lc - 1); i = n + 1 }
            else if (bc > 0) { out = out substr(rest, 1, bc - 1); i = i + bc + 1; inblk = 1 }
            else { out = out rest; i = n + 1 }
          }
        }
        if (out ~ pat) print FILENAME ":" FNR ": " out
      }'
}

hits="$(run_check || true)"

if [ -n "$hits" ]; then
  echo "✗ 发现广告相关代码，本应用必须保持无广告：" >&2
  echo "$hits" >&2
  echo >&2
  echo "若确需引用这些名称（例如在 AdBlocker 的说明里），请写进注释。" >&2
  exit 1
fi

echo "✓ 未发现广告接口 / 插槽 / 素材字段的引用"
