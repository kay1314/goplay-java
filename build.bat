@echo off
REM Build script for GoPlay Java Client (Windows)

echo Building GoPlay Java Client...
echo ===============================

REM Check if Maven is installed
mvn --version >nul 2>&1
if errorlevel 1 (
    echo Error: Maven is not installed. Please install Maven first.
    exit /b 1
)

REM Display Maven version
echo Using Maven:
mvn --version
echo.

REM Run Maven build
echo Running Maven build...
mvn clean install

if errorlevel 1 (
    echo.
    echo Build failed!
    exit /b 1
) else (
    echo.
    echo Build successful!
    echo JAR file generated: target/goplay-java-client-0.1.0.jar
)
