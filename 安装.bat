@echo off
setlocal EnableExtensions
chcp 65001 >nul 2>&1

set "PROJECT_ROOT=%~dp0"
set "APK_PATH=%PROJECT_ROOT%app\build\outputs\apk\debug\app-debug.apk"
set "ADB_EXE="
set "DEVICE_SERIAL="
set "EXIT_CODE=1"
set "NO_PAUSE="

:parse_arguments
if "%~1"=="" goto arguments_ready
if /i "%~1"=="--no-pause" (
  set "NO_PAUSE=1"
  shift
  goto parse_arguments
)
if not defined DEVICE_SERIAL (
  set "DEVICE_SERIAL=%~1"
  shift
  goto parse_arguments
)
echo [ERROR] INVALID_ARGUMENT: 无法识别参数 "%~1"。
goto finish

:arguments_ready
cd /d "%PROJECT_ROOT%" 2>nul
if errorlevel 1 (
  echo [ERROR] PROJECT_ROOT_UNAVAILABLE: 无法进入项目目录。
  goto finish
)

if not exist "%APK_PATH%" (
  echo [ERROR] APK_NOT_FOUND: 未找到 Debug APK。
  echo 请先双击“打包.bat”，成功后再运行安装脚本。
  goto finish
)

for /f "delims=" %%A in ('where adb 2^>nul') do if not defined ADB_EXE set "ADB_EXE=%%A"

if not defined ADB_EXE if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB_EXE=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB_EXE if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB_EXE=%ANDROID_HOME%\platform-tools\adb.exe"

if not defined ADB_EXE if exist "%PROJECT_ROOT%local.properties" (
  for /f "tokens=1,* delims==" %%A in ('findstr /b /c:"sdk.dir=" "%PROJECT_ROOT%local.properties"') do set "SDK_DIR=%%B"
)
if defined SDK_DIR set "SDK_DIR=%SDK_DIR:\\=\%"
if not defined ADB_EXE if defined SDK_DIR if exist "%SDK_DIR%\platform-tools\adb.exe" set "ADB_EXE=%SDK_DIR%\platform-tools\adb.exe"

if not defined ADB_EXE (
  echo [ERROR] ADB_NOT_FOUND: 未找到 adb。
  echo 请安装 Android SDK Platform-Tools，或设置 ANDROID_SDK_ROOT。
  goto finish
)

if defined DEVICE_SERIAL (
  call "%ADB_EXE%" -s "%DEVICE_SERIAL%" get-state >nul 2>&1
) else (
  call "%ADB_EXE%" get-state >nul 2>&1
)
if errorlevel 1 (
  echo [ERROR] DEVICE_NOT_READY: 没有可用设备、设备未授权，或同时连接了多台设备。
  call "%ADB_EXE%" devices
  echo 多设备时可运行：安装.bat 设备序列号
  goto finish
)

echo 正在安装 APK...
if defined DEVICE_SERIAL (
  call "%ADB_EXE%" -s "%DEVICE_SERIAL%" install -r "%APK_PATH%"
) else (
  call "%ADB_EXE%" install -r "%APK_PATH%"
)
if errorlevel 1 (
  echo.
  echo [ERROR] INSTALL_FAILED: ADB 安装失败，请查看上方错误。
  goto finish
)

echo.
echo [OK] INSTALL_SUCCESS
set "EXIT_CODE=0"

:finish
echo.
if not defined NO_PAUSE pause
endlocal & exit /b %EXIT_CODE%
