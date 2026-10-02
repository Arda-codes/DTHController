@echo off
setlocal

echo === DTHController Windows Companion Launcher ===

:: Check if ADB is in PATH or common locations
where adb >nul 2>nul
if %ERRORLEVEL% equ 0 (
    set ADB_CMD=adb
) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB_CMD=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
) else (
    echo [WARN] 'adb.exe' was not found in PATH or standard Android SDK location.
    echo Please make sure Android Platform Tools are installed or added to PATH.
    set ADB_CMD=adb
)

echo [INFO] Forwarding port 54321 via ADB reverse...
%ADB_CMD% reverse tcp:54321 tcp:54321 >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo [OK] Port 54321 forwarded over USB.
) else (
    echo [WARN] ADB reverse failed. Ensure your tablet is connected with USB Debugging enabled.
)

if not exist "DTHController.exe" (
    if exist "target\x86_64-pc-windows-msvc\release\dthcontroller-windows.exe" (
        copy /y "target\x86_64-pc-windows-msvc\release\dthcontroller-windows.exe" "DTHController.exe" >nul
    ) else if exist "..\DTHController.exe" (
        copy /y "..\DTHController.exe" "DTHController.exe" >nul
    ) else (
        echo [ERROR] DTHController.exe not found! Run build.bat to build it.
        pause
        exit /b 1
    )
)

echo [OK] Launching DTHController.exe...
DTHController.exe
pause
