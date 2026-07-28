# i18n-extract.ps1 - 提取缺失 key 对应的文本内容 (PowerShell 版本)
# 用于从参考文件提取指定 key 的 strings.xml 条目
# 适用于 Windows 原生环境

# 参数检查
param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$File,

    [Parameter(Mandatory=$true, Position=1)]
    [string]$Keys
)

# 检查文件是否存在
if (-not (Test-Path $File)) {
    Write-Host "错误：文件不存在 - $File" -ForegroundColor Red
    exit 1
}

# 将 key 列表转换为正则表达式模式（处理正则特殊字符）
# 输入格式: "key1|key2|key3"，需要转义每个 key 中的正则特殊字符
$keyArray = $Keys -split "\|"
$escapedKeys = $keyArray | ForEach-Object { [regex]::Escape($_) }
$keyPattern = $escapedKeys -join "|"
$regexPattern = "<string name=`\"($keyPattern)`\"[^>]*>.*?</string>"

# 提取匹配的条目
$content = Get-Content $File -Raw
$matches = [regex]::Matches($content, $regexPattern, "Singleline")

foreach ($match in $matches) {
    Write-Host $match.Value
}

Write-Host ""
Write-Host "提取完成，共 $($matches.Count) 条"