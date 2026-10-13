param([ValidateSet("setup", "start", "stop", "doctor", "config", "logs")][string]$Command = "doctor")
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "../..")
& python (Join-Path $Root "workbench") $Command
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
