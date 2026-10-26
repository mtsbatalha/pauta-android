# Baixa as bibliotecas nativas do sherpa-onnx (arm64-v8a) para o projeto.
#
# Os arquivos já estão commitados em app/src/main/jniLibs/arm64-v8a — este
# script existe para documentar a origem e permitir atualizar a versão.
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts/setup-native.ps1 [-Version 1.13.8]

param(
    [string]$Version = "1.13.8"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root "app\src\main\jniLibs\arm64-v8a"
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) "sherpa-onnx-$Version"

$url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$Version/sherpa-onnx-v$Version-android.tar.bz2"

Write-Host "Baixando sherpa-onnx v$Version (Android, todas as ABIs)..."
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$tarball = Join-Path $tmp "sherpa-onnx-android.tar.bz2"
curl.exe -sL -o $tarball $url
if ($LASTEXITCODE -ne 0) { throw "Falha ao baixar $url" }

Write-Host "Extraindo arm64-v8a para $dest..."
tar -xjf $tarball -C $tmp

New-Item -ItemType Directory -Force -Path $dest | Out-Null
Copy-Item (Join-Path $tmp "jniLibs\arm64-v8a\*.so") $dest -Force

Get-ChildItem $dest | ForEach-Object { Write-Host "  $($_.Name) — $([math]::Round($_.Length/1MB,1)) MB" }
Write-Host "Concluído."
