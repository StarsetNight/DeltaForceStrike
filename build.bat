@echo off
setlocal

cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java was not found. Please install Java 21 and add it to PATH.
    exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven was not found. Please install Maven 3.9+ and add it to PATH.
    exit /b 1
)

for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /R /C:"version"') do set "JAVA_VERSION=%%~V"
echo Building DeltaForceStrike with Maven and Java %JAVA_VERSION%...

call mvn clean package %*
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed.
    exit /b 1
)

echo.
echo [OK] Build completed: target\DeltaForceStrike-1.3.0.jar
exit /b 0
