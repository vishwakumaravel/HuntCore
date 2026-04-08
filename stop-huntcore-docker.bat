@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%scripts\stop-huntcore-docker-stack.ps1"
if errorlevel 1 (
  echo.
  echo HuntCore Docker shutdown failed. Read the error above, then press any key to close this window.
  pause >nul
)
endlocal
