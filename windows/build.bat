@echo off
setlocal

echo === Building DTHController for Windows ===

where cargo >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo [INFO] Building with Cargo...
    cargo build --release
    if %ERRORLEVEL% equ 0 (
        copy /y "target\release\dthcontroller-windows.exe" "DTHController.exe" >nul
        echo [OK] Build successful: DTHController.exe
        exit /b 0
    )
)

echo [ERROR] Cargo is required to build from source. Install Rust from https://rustup.rs/
pause
exit /b 1
