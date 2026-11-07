@echo off
rem Starts RuneLite with Bank Sorter loaded. Rebuild the jar after plugin changes: gradlew.bat shadowJar
rem Logs in with the saved Jagex session (see README "Running day to day").
cd /d "%~dp0"
start "" javaw -ea -jar "build\libs\bank-sorter-unspecified-all.jar" --developer-mode
