$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$buildScriptName = ([string][char]0x6253) + [char]0x5305 + ".bat"
$installScriptName = ([string][char]0x5B89) + [char]0x88C5 + ".bat"
$buildScript = Join-Path $repoRoot $buildScriptName
$installScript = Join-Path $repoRoot $installScriptName
$systemPath = Join-Path $env:SystemRoot "System32"
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("power-inspector-batch-tests-" + [guid]::NewGuid().ToString("N"))

function Invoke-BatchScript {
    param(
        [Parameter(Mandatory)]
        [string] $ScriptPath,

        [string] $Arguments = "",

        [Parameter(Mandatory)]
        [string] $PathValue
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = Join-Path $systemPath "cmd.exe"
    $startInfo.Arguments = "/d /c call `"$ScriptPath`" $Arguments < nul"
    $startInfo.WorkingDirectory = $systemPath
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $previousPath = $env:Path
    try {
        $env:Path = $PathValue
        [void] $process.Start()
    }
    finally {
        $env:Path = $previousPath
    }
    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()

    if (-not $process.WaitForExit(15000)) {
        $process.Kill()
        throw "Batch script timed out: $ScriptPath"
    }

    [pscustomobject]@{
        ExitCode = $process.ExitCode
        Output = $standardOutput + $standardError
    }
}

function Assert-True {
    param(
        [Parameter(Mandatory)]
        [bool] $Condition,

        [Parameter(Mandatory)]
        [string] $Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

try {
    New-Item -ItemType Directory -Path $testRoot | Out-Null

    $missingWrapperRoot = Join-Path $testRoot "missing-wrapper"
    New-Item -ItemType Directory -Path $missingWrapperRoot | Out-Null
    Copy-Item -LiteralPath $buildScript -Destination (Join-Path $missingWrapperRoot $buildScriptName)
    $missingWrapperResult = Invoke-BatchScript `
        -ScriptPath (Join-Path $missingWrapperRoot $buildScriptName) `
        -Arguments "--no-pause" `
        -PathValue $systemPath
    Assert-True ($missingWrapperResult.ExitCode -ne 0) "Build script should fail when the Gradle Wrapper is missing."
    Assert-True ($missingWrapperResult.Output -match "GRADLE_WRAPPER_MISSING") "Build script should explain that the Gradle Wrapper is missing."

    $missingApkRoot = Join-Path $testRoot "missing-apk"
    New-Item -ItemType Directory -Path $missingApkRoot | Out-Null
    Copy-Item -LiteralPath $installScript -Destination (Join-Path $missingApkRoot $installScriptName)
    $missingApkResult = Invoke-BatchScript `
        -ScriptPath (Join-Path $missingApkRoot $installScriptName) `
        -Arguments "--no-pause" `
        -PathValue $systemPath
    Assert-True ($missingApkResult.ExitCode -ne 0) "Install script should fail when the APK is missing."
    Assert-True ($missingApkResult.Output -match "APK_NOT_FOUND") "Install script should explain that the APK is missing."

    $missingAdbRoot = Join-Path $testRoot "missing-adb"
    $missingAdbApk = Join-Path $missingAdbRoot "app\build\outputs\apk\debug\app-debug.apk"
    New-Item -ItemType Directory -Path (Split-Path -Parent $missingAdbApk) -Force | Out-Null
    Set-Content -LiteralPath $missingAdbApk -Value "fake apk"
    Copy-Item -LiteralPath $installScript -Destination (Join-Path $missingAdbRoot $installScriptName)
    $missingAdbResult = Invoke-BatchScript `
        -ScriptPath (Join-Path $missingAdbRoot $installScriptName) `
        -Arguments "--no-pause" `
        -PathValue $systemPath
    Assert-True ($missingAdbResult.ExitCode -ne 0) "Install script should fail when adb is unavailable."
    Assert-True ($missingAdbResult.Output -match "ADB_NOT_FOUND") "Install script should explain that adb is unavailable."

    $successRoot = Join-Path $testRoot "success"
    $successApk = Join-Path $successRoot "app\build\outputs\apk\debug\app-debug.apk"
    $fakeTools = Join-Path $successRoot "fake-tools"
    New-Item -ItemType Directory -Path (Split-Path -Parent $successApk) -Force | Out-Null
    New-Item -ItemType Directory -Path $fakeTools | Out-Null
    Set-Content -LiteralPath $successApk -Value "fake apk"
    Copy-Item -LiteralPath $installScript -Destination (Join-Path $successRoot $installScriptName)
    Set-Content -LiteralPath (Join-Path $fakeTools "adb.cmd") -Encoding Ascii -Value @"
@echo off
if "%~1"=="get-state" (
  echo device
  exit /b 0
)
if "%~1"=="install" (
  echo Success
  exit /b 0
)
exit /b 1
"@
    $successResult = Invoke-BatchScript `
        -ScriptPath (Join-Path $successRoot $installScriptName) `
        -Arguments "--no-pause" `
        -PathValue ($fakeTools + [System.IO.Path]::PathSeparator + $systemPath)
    Assert-True ($successResult.ExitCode -eq 0) "Install script should return success when adb installs the APK."
    Assert-True ($successResult.Output -match "INSTALL_SUCCESS") ("Install script should report a successful installation. Output: " + $successResult.Output)

    Write-Output "PASS: Batch script regression tests completed."
}
finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
