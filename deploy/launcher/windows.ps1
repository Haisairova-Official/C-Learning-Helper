param([string]$Action, [Parameter(ValueFromRemainingArguments=$true)][string[]]$Options)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if ($Options -contains '--help' -or $Options -contains '-h') {
    Write-Host 'install.bat [--check]    run.bat [--port 9000]    run.bat status    run.bat stop'
    exit 0
}
try {
    $machine = $env:PROCESSOR_ARCHITEW6432
    if (!$machine) { $machine = $env:PROCESSOR_ARCHITECTURE }
    if ($machine -ne 'AMD64') { throw 'The Windows installer currently supports x64 Windows.' }
    $state = Join-Path $root '.local\windows'
    $python = Join-Path $state 'python\python.exe'
    if (!(Test-Path -LiteralPath $python)) {
        if ($Action -ne 'install' -or $Options) { throw 'Run install.bat first.' }
        [IO.Directory]::CreateDirectory($state) | Out-Null
        $stage = Join-Path $state ([guid]::NewGuid().ToString())
        [IO.Directory]::CreateDirectory($stage) | Out-Null
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            $zip = Join-Path $stage 'python.zip'
            $url = 'https://www.python.org/ftp/python/3.13.7/python-3.13.7-embed-amd64.zip'
            Write-Host 'Installing the native Windows launcher runtime...'
            (New-Object Net.WebClient).DownloadFile($url, $zip)
            if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne 'F6CCA216A359BE84797CABB54149CE5E062AFB16CC7567EB7FC51CACB2D86B65') {
                throw 'Python download checksum mismatch.'
            }
            $unpacked = Join-Path $stage 'unpacked'
            Expand-Archive -LiteralPath $zip -DestinationPath $unpacked
            [IO.Directory]::Move($unpacked, (Split-Path $python))
        } finally { Remove-Item -LiteralPath $stage -Recurse -Force }
    }
    $env:PYTHONUTF8 = '1'
    & $python -X utf8 (Join-Path $PSScriptRoot 'windows.py') $Action @Options
    exit $LASTEXITCODE
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}
