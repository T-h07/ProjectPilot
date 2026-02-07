@echo off
setlocal

if "%~1"=="" (
  echo Usage: run-client.bat ^<host-ip^>
  echo Example: run-client.bat 192.168.1.50
  pause
  exit /b 1
)

set PP_LAN_MODE=client
set PP_LAN_HOST=%~1

set "APP_EXE=%~dp0dist\ProjectPilot\ProjectPilot.exe"
if exist "%APP_EXE%" (
  start "" "%APP_EXE%"
  exit /b 0
)

echo App image not found.
echo Build it first with: package-windows.ps1
pause
exit /b 1
