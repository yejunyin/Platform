@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
set "NP=C:\Program Files\nodejs"
set "PATH=%NP%;%PATH%"
cd /d "e:\Project\Platform\frontend"
echo [BOOT] Node version:
"%NP%\node.exe" -v
echo [BOOT] Running vite --host 0.0.0.0 --port 3000
"%NP%\node.exe" "node_modules\vite\bin\vite.js" --host 0.0.0.0 --port 3000 --strictPort
set "RC=%ERRORLEVEL%"
echo.
echo [BOOT] Vite process exited with code %RC%
exit /b %RC%
