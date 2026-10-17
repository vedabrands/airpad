@echo off
title AirPad Desktop Agent
cd /d "%~dp0"
echo Starting AirPad Desktop Agent...
node src/index.js
pause
