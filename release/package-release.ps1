param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version,
    [switch]$SkipAndroid
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$artifactDir = Join-Path $PSScriptRoot 'artifacts'
$stage = Join-Path $env:TEMP "syncon-extension-$Version"

if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage | Out-Null
New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null

$extensionRoot = Join-Path $repo 'extension'
@('assets', 'blocked', 'dashboard', 'lib', 'onboarding', 'popup') | ForEach-Object {
    Copy-Item -LiteralPath (Join-Path $extensionRoot $_) -Destination $stage -Recurse
}
@('manifest.json', 'shared.css') | ForEach-Object {
    Copy-Item -LiteralPath (Join-Path $extensionRoot $_) -Destination $stage
}

$extensionZip = Join-Path $artifactDir "syncon-chrome-$Version.zip"
if (Test-Path -LiteralPath $extensionZip) { Remove-Item -LiteralPath $extensionZip -Force }
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $extensionZip -CompressionLevel Optimal
Remove-Item -LiteralPath $stage -Recurse -Force

if (-not $SkipAndroid) {
    $signing = Join-Path $repo 'keystore.properties'
    if (-not (Test-Path -LiteralPath $signing)) {
        throw 'Android signing is not configured. Copy release/keystore.properties.example to keystore.properties and add the private values, or rerun with -SkipAndroid.'
    }
    & (Join-Path $repo 'gradlew.bat') -p $repo verifyReleaseReadiness bundleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Android release bundle failed.' }
    $bundle = Join-Path $repo 'app/build/outputs/bundle/release/app-release.aab'
    Copy-Item -LiteralPath $bundle -Destination (Join-Path $artifactDir "syncon-android-$Version.aab") -Force
}

Get-ChildItem -LiteralPath $artifactDir -File |
    Get-FileHash -Algorithm SHA256 |
    Select-Object Path, Hash |
    Format-Table -AutoSize
