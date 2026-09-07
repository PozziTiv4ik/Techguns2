param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments = @('build')
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $taskJdkRoot = Join-Path $taskRoot '.tools/jdk25'
    if (Test-Path -LiteralPath $taskJdkRoot) {
        $taskJdks = @(Get-ChildItem -LiteralPath $taskJdkRoot -Directory | Where-Object {
            Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe')
        })
        if ($taskJdks.Count -eq 1) { $env:JAVA_HOME = $taskJdks[0].FullName }
    }
}
if (-not $env:JAVA_HOME) { throw 'Install JDK 25 and set JAVA_HOME before running the build.' }
Push-Location -LiteralPath $taskRoot
try {
    & (Join-Path $taskRoot 'gradlew.bat') @GradleArguments
    $taskExitCode = $LASTEXITCODE
} finally { Pop-Location }
exit $taskExitCode
