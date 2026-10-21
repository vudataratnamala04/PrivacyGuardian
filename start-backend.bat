@echo off
cd /d "%~dp0backend"
if "%DB_PASSWORD%"=="" set DB_PASSWORD=Ratna@2007
mvnd clean install
if errorlevel 1 (
  echo Backend build failed.
  pause
  exit /b 1
)
mvnd spring-boot:run
pause
