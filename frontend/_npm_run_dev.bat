@echo off
chcp 65001 >nul
set "NP=C:\Program Files\nodejs"
set "PATH=%NP%;%PATH%"
cd /d "e:\Project\Platform\frontend"
call "%NP%\npm.cmd" run dev 2>&1
exit /b %ERRORLEVEL%
