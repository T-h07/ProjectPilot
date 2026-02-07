$ErrorActionPreference = "Stop"

$taskName = "ProjectPilot Cloud Server"
Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue | Out-Null
Write-Host "Removed task: $taskName"
