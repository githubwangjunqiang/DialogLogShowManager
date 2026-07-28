# i18n-check.ps1 - Android 国际化检查脚本 (PowerShell 版本)
# 用于扫描、统计、比对各语言 strings.xml 文件
#
# 参数：res 的父目录路径（通常是 src/main）
# AI 应先探索项目找到正确的 res 位置，再调用本脚本

param(
    [Parameter(Mandatory=$true)]
    [string]$ModulePath
)

# 切换到模块目录
if (-not (Test-Path $ModulePath)) {
    Write-Host "错误：路径不存在 - $ModulePath" -ForegroundColor Red
    exit 1
}

Set-Location $ModulePath

# 检查 res 目录是否存在
if (-not (Test-Path "res")) {
    Write-Host "错误：res 目录不存在于 $ModulePath" -ForegroundColor Red
    exit 1
}

function PrintSeparator {
    Write-Host "================================"
}

$tempAllKeys = Join-Path $env:TEMP "i18n_all_keys.txt"
$tempCurKeys = Join-Path $env:TEMP "i18n_cur_keys.txt"

# 1. 语言文件统计
PrintSeparator
Write-Host "语言文件统计"
PrintSeparator

$stringsFiles = Get-ChildItem -Path "res" -Filter "strings.xml" -Recurse | Where-Object { $_.DirectoryName -match "values" }
foreach ($file in ($stringsFiles | Sort-Object FullName)) {
    $count = (Select-String -Path $file.FullName -Pattern '<string name=' -SimpleMatch).Count
    $relativePath = $file.FullName.Substring((Get-Location).Path.Length + 1)
    Write-Host "$relativePath: $count"
}

# 2. 全量 key 并集
Write-Host ""
PrintSeparator
Write-Host "全量 key 并集"
PrintSeparator

$allKeys = @()
foreach ($file in $stringsFiles) {
    $content = Get-Content $file.FullName -Raw
    $matches = [regex]::Matches($content, '<string name="([^"]+)"')
    foreach ($match in $matches) {
        $allKeys += $match.Groups[1].Value
    }
}
$uniqueKeys = $allKeys | Sort-Object -Unique
$uniqueKeys | Out-File -FilePath $tempAllKeys -Encoding utf8
Write-Host "全量基准: $($uniqueKeys.Count) strings"

# 3. 各语言缺失详情
Write-Host ""
PrintSeparator
Write-Host "各语言缺失详情"
PrintSeparator

foreach ($file in ($stringsFiles | Sort-Object FullName)) {
    $dirName = Split-Path $file.FullName -Parent | Split-Path -Leaf

    $content = Get-Content $file.FullName -Raw
    $matches = [regex]::Matches($content, '<string name="([^"]+)"')
    $curKeys = @()
    foreach ($match in $matches) {
        $curKeys += $match.Groups[1].Value
    }
    $curKeys | Sort-Object -Unique | Out-File -FilePath $tempCurKeys -Encoding utf8

    $allKeysList = Get-Content $tempAllKeys
    $curKeysList = Get-Content $tempCurKeys
    $missing = Compare-Object -ReferenceObject $allKeysList -DifferenceObject $curKeysList -PassThru | Where-Object { $_.SideIndicator -eq "<=" }

    $count = if ($missing) { $missing.Count } else { 0 }

    Write-Host ""
    Write-Host "$dirName: 缺失 $count strings"

    if ($count -gt 0 -and $count -le 20) {
        foreach ($key in $missing) {
            Write-Host "  - $key"
        }
    } elseif ($count -gt 20) {
        Write-Host "  （缺失过多，仅显示前 10 条）"
        $missing | Select-Object -First 10 | ForEach-Object { Write-Host "  - $_" }
    }
}

Remove-Item -Path $tempAllKeys -Force -ErrorAction SilentlyContinue
Remove-Item -Path $tempCurKeys -Force -ErrorAction SilentlyContinue

Write-Host ""
PrintSeparator
Write-Host "检查完成"
PrintSeparator