@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
set "NP=C:\Program Files\nodejs"
set "PATH=%NP%;%PATH%"
cd /d "e:\Project\Platform\frontend"

echo ==== BOOT %date% %time% ====
echo Node:
"%NP%\node.exe" -v

echo Running: node node_modules\vite\bin\vite.js --host 0.0.0.0 --port 3000 --strictPort --force
"%NP%\node.exe" "node_modules\vite\bin\vite.js" --host 0.0.0.0 --port 3000 --strictPort --force 2>&1
set "RC=%ERRORLEVEL%"
echo.
echo ==== VITE EXITED %RC% at %date% %time% ====
exit /b %RC%
