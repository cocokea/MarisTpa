@echo off
set GRADLE_VERSION=9.1.0
set APP_HOME=%~dp0
set DIST_DIR=%APP_HOME%.gradle-dist
set GRADLE_HOME=%DIST_DIR%\gradle-%GRADLE_VERSION%
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  mkdir "%DIST_DIR%" 2>nul
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip -OutFile '%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip'; Expand-Archive -Force '%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip' '%DIST_DIR%'"
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
