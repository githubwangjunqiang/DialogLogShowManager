#!/bin/bash
# i18n-check.sh - Android 国际化检查脚本
# 用于扫描、统计、比对各语言 strings.xml 文件
#
# 参数：res 的父目录路径（通常是 src/main）
# AI 应先探索项目找到正确的 res 位置，再调用本脚本

set -e

# 参数：res 父目录路径
MODULE_PATH="$1"

if [ -z "$MODULE_PATH" ]; then
    echo "错误：缺少参数"
    echo "用法: $0 <res父目录路径>"
    echo "示例: $0 app/src/main"
    exit 1
fi

cd "$MODULE_PATH" || { echo "错误：路径不存在 - $MODULE_PATH"; exit 1; }

# 检查 res 目录是否存在
if [ ! -d "res" ]; then
    echo "错误：res 目录不存在于 $MODULE_PATH"
    exit 1
fi

# 输出分隔线
print_separator() {
    echo "================================"
}

# 1. 语言文件统计
print_separator
echo "语言文件统计"
print_separator
find res -name "strings.xml" -path "*/values*/*" -exec sh -c 'echo "$1: $(grep -c "<string name=" "$1")"' _ {} \; | sort

# 2. 全量 key 并集
echo ""
print_separator
echo "全量 key 并集"
print_separator
find res -name "strings.xml" -path "*/values*/*" -exec grep -oE '<string name="[^"]+"' {} \; | sed 's/<string name="//;s/"$//' | sort -u > /tmp/i18n_all_keys.txt
echo "全量基准: $(wc -l < /tmp/i18n_all_keys.txt) strings"

# 3. 各语言缺失详情
echo ""
print_separator
echo "各语言缺失详情"
print_separator
for f in $(find res -name "strings.xml" -path "*/values*/*" | sort); do
    dir=$(dirname "$f" | xargs basename)
    grep -oE '<string name="[^"]+"' "$f" | sed 's/<string name="//;s/"$//' | sort > /tmp/i18n_cur_keys.txt

    missing=$(comm -23 /tmp/i18n_all_keys.txt /tmp/i18n_cur_keys.txt)

    if [ -z "$missing" ]; then
        count=0
    else
        count=$(echo "$missing" | wc -l | tr -d ' ')
    fi

    echo ""
    echo "$dir: 缺失 $count strings"

    if [ "$count" -gt 0 ] && [ "$count" -le 20 ]; then
        echo "$missing" | sed 's/^/  - /'
    elif [ "$count" -gt 20 ]; then
        echo "  （缺失过多，仅显示前 10 条）"
        echo "$missing" | head -10 | sed 's/^/  - /'
    fi
done

rm -f /tmp/i18n_all_keys.txt /tmp/i18n_cur_keys.txt

echo ""
print_separator
echo "检查完成"
print_separator