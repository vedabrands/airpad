@echo off
setlocal
echo ===============================================================
echo          AirPad Desktop Agent - Add to Windows Startup
echo ===============================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "TARGET_VBS=%SCRIPT_DIR%start-background.vbs"
set "STARTUP_FOLDER=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "SHORTCUT=%STARTUP_FOLDER%\AirPad Desktop Agent.lnk"

powershell -NoProfile -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut('%SHORTCUT%'); $s.TargetPath = 'wscript.exe'; $s.Arguments = '\"%TARGET_VBS%\"'; $s.WorkingDirectory = '%SCRIPT_DIR%'; $s.Description = 'AirPad Background Input Host'; $s.Save()"

if exist "%SHORTCUT%" (
    echo [SUCCESS] AirPad Desktop Agent successfully configured to run on Windows startup!
    echo Shortcut location: "%SHORTCUT%"
    echo.
) else (
    echo [ERROR] Failed to create startup shortcut.
)

pause
