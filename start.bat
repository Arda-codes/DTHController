@echo off
setlocal

echo === DTHController Windows Companion Launcher ===

where adb >nul 2>nul
if %ERRORLEVEL% equ 0 (
    set ADB_CMD=adb
) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB_CMD=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
) else (
    echo [WARN] 'adb.exe' was not found in PATH or standard Android SDK location.
    set ADB_CMD=adb
)

echo [INFO] Forwarding port 54321 via ADB reverse...
%ADB_CMD% reverse tcp:54321 tcp:54321 >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo [OK] Port 54321 forwarded over USB.
) else (
    echo [WARN] ADB reverse failed. Ensure your tablet is connected with USB Debugging enabled.
)

if exist "windows\DTHController.exe" (
    cd windows
    DTHController.exe
) else if exist "DTHController.exe" (
    DTHController.exe
) else (
    echo [ERROR] DTHController.exe not found!
    pause
    exit /b 1
)
pause
