@echo off
REM Build Item ID on Windows: sync the WSL working tree, then run the full gradle build.
setlocal
set "JAVA_HOME=C:\Users\markl\jdk11\jdk-11.0.31+11"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "SRC=\\wsl.localhost\Ubuntu\home\user\runelite-item-id"
set "DST=C:\Users\markl\runelite-item-id"

echo Syncing latest plugin source from WSL...
robocopy "%SRC%" "%DST%" /MIR /XD .git build .gradle /NFL /NDL /NJH /NJS /NP >nul

cd /d "%DST%"
echo Building Item ID (clean test build)...
echo (First run downloads Gradle and the RuneLite client, so give it a few minutes.)
echo.
call gradlew.bat clean test build --console=plain
echo.
if errorlevel 1 (
    echo BUILD FAILED - scroll up for the errors.
) else (
    echo BUILD OK - plugin compiled and all tests passed.
)
pause
