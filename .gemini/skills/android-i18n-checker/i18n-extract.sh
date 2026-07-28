#!/bin/bash
# i18n-extract.sh - 提取缺失 key 对应的文本内容
# 用于从参考文件提取指定 key 的 strings.xml 条目

set -e

# 参数检查
if [ $# -lt 2 ]; then
    echo "用法: $0 <strings文件路径> <key列表>"
    echo "示例: $0 res/values-zh-rCN/strings.xml 'key1|key2|key3'"
    exit 1
fi

FILE="$1"
KEYS="$2"

# 检查文件是否存在
if [ ! -f "$FILE" ]; then
    echo "错误：文件不存在 - $FILE"
    exit 1
fi

# 转义正则特殊字符（处理 key 中可能包含的 . * + ? [ ] ^ $ 等字符）
# 将 | 分隔的 key 列表拆分，转义每个 key，再重新组合
ESCAPED_KEYS=$(echo "$KEYS" | sed 's/[][\.^$*+?{}\\]/\\&/g')

# 提取匹配的条目（使用扩展正则，兼容 macOS BSD grep）
grep -E "<string name=\"($ESCAPED_KEYS)\"" "$FILE"

echo ""
echo "提取完成，共 $(grep -E "<string name=\"($ESCAPED_KEYS)\"" "$FILE" | wc -l | tr -d ' ') 条"