@echo off
setlocal EnableExtensions
chcp 65001 >nul 2>&1

set "PROJECT_ROOT=%~dp0"
set "APK_PATH=%PROJECT_ROOT%app\build\outputs\apk\debug\app-debug.apk"
set "EXIT_CODE=1"
set "NO_PAUSE="

if /i "%~1"=="--no-pause" set "NO_PAUSE=1"

cd /d "%PROJECT_ROOT%" 2>nul
if errorlevel 1 (
  echo [ERROR] PROJECT_ROOT_UNAVAILABLE: 无法进入项目目录。
  goto finish
)

if not exist "%PROJECT_ROOT%gradlew.bat" goto wrapper_missing
if not exist "%PROJECT_ROOT%gradle\wrapper\gradle-wrapper.jar" goto wrapper_missing
if not exist "%PROJECT_ROOT%gradle\wrapper\gradle-wrapper.properties" goto wrapper_missing

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" goto java_ready
where java.exe >nul 2>&1
if not errorlevel 1 goto java_ready

if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
  set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
  goto java_ready
)

for /d %%D in ("%ProgramFiles%\Java\jdk-17*" "%ProgramFiles%\Eclipse Adoptium\jdk-17*" "%PROJECT_ROOT%..\software\jdk-17*") do (
  if exist "%%~fD\bin\java.exe" (
    set "JAVA_HOME=%%~fD"
    goto java_ready
  )
)

echo [ERROR] JAVA_NOT_FOUND: 未找到可用的 Java 17。
echo 请安装 JDK 17，或正确设置 JAVA_HOME 后重试。
goto finish

:wrapper_missing
echo [ERROR] GRADLE_WRAPPER_MISSING: Gradle Wrapper 文件不完整。
echo 请确认 gradlew.bat、gradle\wrapper\gradle-wrapper.jar 和 gradle-wrapper.properties 都存在。
goto finish

:java_ready
echo.
echo 正在构建 Debug APK，请稍候...
call "%PROJECT_ROOT%gradlew.bat" --console=plain --no-configuration-cache assembleDebug
if errorlevel 1 (
  echo.
  echo [ERROR] BUILD_FAILED: APK 构建失败，请查看上方 Gradle 错误。
  goto finish
)

if not exist "%APK_PATH%" (
  echo.
  echo [ERROR] APK_NOT_CREATED: Gradle 已结束，但没有找到 APK。
  goto finish
)

echo.
echo [OK] BUILD_SUCCESS
echo APK: "%APK_PATH%"
set "EXIT_CODE=0"

:finish
echo.
if not defined NO_PAUSE pause
endlocal & exit /b %EXIT_CODE%
