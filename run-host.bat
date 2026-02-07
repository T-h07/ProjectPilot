@echo off
setlocal

set PP_LAN_MODE=host

set "APP_EXE=%~dp0dist\ProjectPilot\ProjectPilot.exe"
if exist "%APP_EXE%" (
  start "" "%APP_EXE%"
  exit /b 0
)

echo App image not found.
echo Build it first with: package-windows.ps1
pause
exit /b 1
