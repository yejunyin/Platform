@echo off
chcp 65001 >nul
setlocal EnableExtensions
set "NP=C:\Program Files\nodejs"
set "PATH=%NP%;%PATH%"
cd /d "e:\Project\Platform\frontend"
echo Starting Vite dev server...
call "%NP%\npm.cmd" run dev
endlocal & exit /b %ERRORLEVEL%
