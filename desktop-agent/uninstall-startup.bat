@echo off
setlocal
echo ===============================================================
echo        AirPad Desktop Agent - Remove from Windows Startup
echo ===============================================================
echo.

set "STARTUP_FOLDER=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "SHORTCUT=%STARTUP_FOLDER%\AirPad Desktop Agent.lnk"

if exist "%SHORTCUT%" (
    del /f /q "%SHORTCUT%"
    echo [SUCCESS] AirPad Desktop Agent removed from Windows startup.
) else (
    echo [INFO] Startup shortcut not found.
)

pause
