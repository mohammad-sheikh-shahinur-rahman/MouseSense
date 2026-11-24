@echo off
setlocal

if not exist "dist" mkdir "dist"
if not exist "dist\adb" mkdir "dist\adb"

if exist "build\bin\Release\Mousedroid.exe" (
    copy /y "build\bin\Release\Mousedroid.exe" "dist\"
    copy /y "build\bin\Release\*.dll" "dist\"
)
if exist "build\vcpkg_installed\x64-windows\bin\*.dll" (
    copy /y "build\vcpkg_installed\x64-windows\bin\*.dll" "dist\"
)

if exist "out\build\x64-Release\bin" (
    xcopy "out\build\x64-Release\bin\*.*" "dist\" /E /I /Y /H
)

xcopy "adb" "dist\adb\" /E /I /Y

copy /y app.ico dist\
copy /y icon.png dist\

if not exist "dist\config.ini" (
    (
        echo MINIMIZE_TASKBAR=0
        echo MOVE_SENSITIVITY=10
        echo RUN_STARTUP=0
        echo SCROLL_SENSITIVITY=3
    )>dist\config.ini
)

echo Deployment to "dist" complete!
pause
