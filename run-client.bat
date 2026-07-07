@echo off
REM Re-launch this window at HIGH process priority so RuneLite runs smoothly, then continue.
if not "%~1"=="hi" (
    start "Item ID (dev)" /HIGH "%~f0" hi
    exit /b
)

setlocal
set "JAVA_HOME=C:\Users\markl\jdk11\jdk-11.0.31+11"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "SRC=\\wsl.localhost\Ubuntu\home\user\runelite-item-id"
set "DST=C:\Users\markl\runelite-item-id"

echo Syncing latest plugin source from WSL...
robocopy "%SRC%" "%DST%" /MIR /XD .git build .gradle /NFL /NDL /NJH /NJS /NP >nul

cd /d "%DST%"
echo Launching RuneLite at HIGH priority (8GB max heap) with Item ID...
echo (First run downloads Gradle and the RuneLite client, so give it a few minutes.)
echo.
REM --no-daemon so the forked client JVM inherits this window's HIGH priority.
call gradlew.bat runClient --no-daemon --console=plain
echo.
if errorlevel 1 (
    echo Client exited with an error - scroll up for details.
)
echo Client closed.
pause
