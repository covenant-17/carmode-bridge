# Reject Eclipse/IDE problem-method stubs before shipping JVM or Android artifacts.
function Assert-NoCompilerStubs([string]$ArchivePath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
    $checked = 0
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -notmatch '\.class$|(^|/)classes\d*\.dex$') { continue }
            $checked++
            $inputStream = $entry.Open()
            $buffer = New-Object System.IO.MemoryStream
            try {
                $inputStream.CopyTo($buffer)
                $text = [System.Text.Encoding]::UTF8.GetString($buffer.ToArray())
                if ($text.Contains('Unresolved compilation problem')) {
                    throw "Compiler error stub in $ArchivePath [$($entry.FullName)]. Artifact must not be installed."
                }
            } finally { $inputStream.Dispose(); $buffer.Dispose() }
        }
    } finally { $archive.Dispose() }
    if ($checked -eq 0) { throw "No class/DEX entries to validate in $ArchivePath" }
    Write-Host "Bytecode guard passed: $ArchivePath ($checked entries)"
}
