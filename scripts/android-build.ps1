param(
    [Parameter(Mandatory=$true)][ValidateSet('resources','package')][string]$Phase,
    [Parameter(Mandatory=$true)][string]$Sdk,
    [string]$Platform = '34', [string]$BuildTools = '34.0.0',
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$Version
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/assert-bytecode.ps1"
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $projectRoot 'target'
$toolDir = Join-Path $Sdk "build-tools/$BuildTools"
$androidJar = Join-Path $Sdk "platforms/android-$Platform/android.jar"
$javaExe = Join-Path $JavaHome 'bin/java.exe'
$manifest = [xml](Get-Content -Raw -LiteralPath "$projectRoot/src/main/AndroidManifest.xml")
if ($manifest.manifest.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android') -ne $Version) {
    throw 'pom.xml and AndroidManifest.xml versionName must match. Increment versionCode for each APK update.'
}
function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed with exit code $LASTEXITCODE" }
}
foreach ($required in @($androidJar, $javaExe, "$JavaHome/bin/javac.exe", "$JavaHome/bin/jar.exe", "$toolDir/aapt2.exe", "$toolDir/lib/d8.jar", "$toolDir/lib/apksigner.jar")) {
    if (!(Test-Path -LiteralPath $required)) { throw "Required tool missing: $required. See README.md." }
}
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
function Build-Resources([string]$Destination, [string]$Generated) {
    New-Item -ItemType Directory -Force -Path $Generated | Out-Null
    Invoke-Checked "$toolDir/aapt2.exe" @('compile', '--dir', "$projectRoot/src/main/res", '-o', "$Destination/resources.zip")
    Invoke-Checked "$toolDir/aapt2.exe" @('link', '-o', "$Destination/resources.apk", '-I', $androidJar,
        '--manifest', "$projectRoot/src/main/AndroidManifest.xml", '--java', $Generated,
        '--min-sdk-version', '29', '--target-sdk-version', '29', '--version-name', $Version,
        '-R', "$Destination/resources.zip", '--auto-add-overlay')
}
if ($Phase -eq 'resources') {
    Build-Resources $outputDir (Join-Path $outputDir 'generated-sources/android')
    exit 0
}
# target/classes is shared with IDE builders. ECJ can overwrite javac output with
# problem-method stubs even after Maven compilation succeeds. Never package it.
# A fresh private directory also prevents stale anonymous classes/resource IDs.
$staging = Join-Path $outputDir ('apk-staging/' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $staging 'classes'
$generated = Join-Path $staging 'generated'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
Build-Resources $staging $generated
$sources = @(Get-ChildItem -LiteralPath "$projectRoot/src/main/java", $generated -Filter '*.java' -Recurse | Sort-Object FullName | ForEach-Object { $_.FullName })
Invoke-Checked "$JavaHome/bin/javac.exe" (@('--release', '8', '-encoding', 'UTF-8', '-proc:none', '-classpath', $androidJar, '-d', $classes) + $sources)
$privateJar = Join-Path $staging 'app.jar'
Invoke-Checked "$JavaHome/bin/jar.exe" @('--create', '--file', $privateJar, '-C', $classes, '.')
Assert-NoCompilerStubs $privateJar
# Publish the same clean JVM input as the Maven artifact; never read the IDE JAR for D8.
Copy-Item -LiteralPath $privateJar -Destination "$outputDir/carmode-bridge-$Version.jar" -Force
$dexZip = Join-Path $staging 'dex.zip'
Invoke-Checked $javaExe @('-cp', "$toolDir/lib/d8.jar", 'com.android.tools.r8.D8', '--min-api', '29',
    '--lib', $androidJar, '--output', $dexZip, $privateJar)
Assert-NoCompilerStubs $dexZip
$unsigned = Join-Path $staging 'unsigned.apk'
Copy-Item -LiteralPath "$staging/resources.apk" -Destination $unsigned -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($unsigned, 'Update')
$dexArchive = [System.IO.Compression.ZipFile]::OpenRead($dexZip)
try {
    foreach ($dexEntry in $dexArchive.Entries) {
        $inputStream = $dexEntry.Open()
        $outputStream = $archive.CreateEntry($dexEntry.Name).Open()
        try { $inputStream.CopyTo($outputStream) }
        finally { $inputStream.Dispose(); $outputStream.Dispose() }
    }
} finally { $dexArchive.Dispose(); $archive.Dispose() }
Invoke-Checked "$toolDir/zipalign.exe" @('-f', '4', $unsigned, "$staging/aligned.apk")
# Project-local debug key survives mvn clean. It is never checked in.
$keyDir = Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
$keyFile = Join-Path $keyDir 'debug.keystore'
if (!(Test-Path -LiteralPath $keyFile)) {
    Invoke-Checked "$JavaHome/bin/keytool.exe" @('-genkeypair', '-keystore', $keyFile, '-storepass', 'android',
        '-alias', 'androiddebugkey', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '10000', '-dname', 'CN=CarMode Debug,O=CarMode,C=UA')
}
$apk = Join-Path $outputDir "carmode-bridge-$Version-debug.apk"
Invoke-Checked $javaExe @('-jar', "$toolDir/lib/apksigner.jar", 'sign', '--ks', $keyFile,
    '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', $apk, "$staging/aligned.apk")
Assert-NoCompilerStubs $apk
Invoke-Checked $javaExe @('-jar', "$toolDir/lib/apksigner.jar", 'verify', '--verbose', $apk)
Invoke-Checked "$toolDir/zipalign.exe" @('-c', '4', $apk)
Write-Host "APK: $apk"
