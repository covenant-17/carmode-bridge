$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/assert-bytecode.ps1"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$fixtureDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) ('target/bytecode-guard-tests/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixtureDirectory -Force | Out-Null
function New-Fixture([string]$Name, [string]$EntryName, [string]$Content) {
    $path = Join-Path $fixtureDirectory $Name
    $archive = [System.IO.Compression.ZipFile]::Open($path, 'Create')
    try {
        $writer = New-Object System.IO.StreamWriter($archive.CreateEntry($EntryName).Open())
        try { $writer.Write($Content) } finally { $writer.Dispose() }
    } finally { $archive.Dispose() }
    return $path
}
function Expect-Rejection([string]$Path, [string]$Reason) {
    try { Assert-NoCompilerStubs $Path } catch {
        if ($_.Exception.Message -notlike "*$Reason*") { throw }
        return
    }
    throw "Guard accepted invalid fixture: $Path"
}
# These are scanner fixtures, not executable class files.
Assert-NoCompilerStubs (New-Fixture 'valid.jar' 'example/Good.class' 'normal bytecode constants')
Assert-NoCompilerStubs (New-Fixture 'valid.apk' 'classes.dex' 'normal dex constants')
Expect-Rejection (New-Fixture 'bad.jar' 'example/Bad.class' 'Unresolved compilation problems: I18n cannot be resolved') 'Compiler error stub'
Expect-Rejection (New-Fixture 'bad.apk' 'classes2.dex' 'Unresolved compilation problem: missing symbol') 'Compiler error stub'
Expect-Rejection (New-Fixture 'empty.jar' 'META-INF/MANIFEST.MF' 'Manifest-Version: 1.0') 'No class/DEX entries'
# A text resource may discuss the error legitimately; only executable entries are scanned.
$mixed = New-Fixture 'resource-text.jar' 'example/Good.class' 'normal bytecode constants'
$archive = [System.IO.Compression.ZipFile]::Open($mixed, 'Update')
try {
    $writer = New-Object System.IO.StreamWriter($archive.CreateEntry('notes.txt').Open())
    try { $writer.Write('Unresolved compilation problems: documentation only') } finally { $writer.Dispose() }
} finally { $archive.Dispose() }
Assert-NoCompilerStubs $mixed
Write-Host 'Bytecode guard: 6 regression checks passed.'
