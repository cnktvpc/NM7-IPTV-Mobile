@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-mobile-windows.ps1"
if errorlevel 1 (
  echo BUILD FAILED.
  pause
  exit /b 1
)
echo BUILD SUCCESSFUL.
pause
