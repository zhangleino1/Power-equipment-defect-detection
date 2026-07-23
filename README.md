# 配电缺陷智能识别

一套运行在 Android 手机上的端侧目标识别与巡检系统。项目使用 TensorFlow Lite 在手机本地处理摄像头画面，并通过局域网向 PC 提供实时监看、巡检记录和二次复核界面。

> 当前版本内置 SSD MobileNet V1 通用目标检测模型，适合验证完整检测流程。项目已经预留自训练 YOLO TFLite 模型导入口，后续可用于电力设备缺陷识别。

## 核心功能

| 功能 | 说明 |
| --- | --- |
| 手机实时检测 | 使用 CameraX 获取画面，在端侧执行目标检测并绘制识别框 |
| 通用目标模型 | 内置 `mobilenet_ssd.tflite`，对应 SSD MobileNet V1 |
| 自定义模型入口 | 可从设置页面导入自训练 YOLO `.tflite` 文件 |
| 推理参数设置 | 支持置信度阈值，并预留 CPU、GPU、NPU 推理配置入口 |
| PC 局域网控制台 | 手机内置 Web 服务，提供实时画面、状态、历史记录和复核操作 |
| 巡检记录 | 使用 Room 保存检测结果、位置、时间和复核状态 |
| 一键打包安装 | Windows 下提供 `打包.bat` 和 `安装.bat` |

## 当前模型

项目只保留一个内置通用模型：

| 项目 | 内容 |
| --- | --- |
| 模型 | SSD MobileNet V1 |
| 文件 | `app/src/main/assets/mobilenet_ssd.tflite` |
| 用途 | COCO 类别的通用目标检测 |
| 状态 | 内置可用 |

该模型用于验证摄像头、TensorFlow Lite 推理、结果绘制、记录保存和 Web 展示的完整链路，不是专门训练的电力缺陷模型。

## 快速开始

### 环境要求

- Android 7.0（API 24）或更高版本的手机
- Android SDK，项目编译目标为 Android SDK 36.1
- JDK 17，用于普通构建
- Java 21 或更高版本，用于运行 Android SDK 36 的 Robolectric 单元测试
- 手机与访问控制台的 PC 位于同一局域网

### Windows 一键构建

在项目根目录双击：

```text
打包.bat
```

脚本会检查 Java、Gradle Wrapper 和 APK 输出，并生成：

```text
app/build/outputs/apk/debug/app-debug.apk
```

首次构建会由 Gradle Wrapper 下载 Gradle 9.3.1。

### 安装到手机

开启手机的开发者选项和 USB 调试，然后双击：

```text
安装.bat
```

如果同时连接多台设备，可以传入设备序列号：

```bat
安装.bat emulator-5554
```

脚本会从 `PATH`、`ANDROID_SDK_ROOT`、`ANDROID_HOME` 或 `local.properties` 查找 ADB。

### 使用 Gradle 命令构建

Windows：

```powershell
.\gradlew.bat assembleDebug
```

macOS 或 Linux：

```bash
./gradlew assembleDebug
```

## 使用方法

### 手机端实时巡检

1. 安装并启动应用。
2. 授予相机权限。
3. 在“实时巡检”页面开始检测。
4. 在“模型调优”页面调整置信度和推理设备。
5. 在“巡检日志”页面查看或复核历史记录。

### PC 端控制台

应用启动后会在手机上启动局域网 Web 服务。确保手机和 PC 位于同一网络，然后在 PC 浏览器访问：

```text
http://手机IP:8080
```

手机端“局域网 Web”页面会显示实际访问地址。PC 控制台支持查看实时画面、推理状态、目标列表、历史记录和复核信息。

## 导入自训练 YOLO 模型

1. 将训练完成的模型转换为 TensorFlow Lite `.tflite` 文件。
2. 在应用的“模型调优”页面选择“自训练 YOLO 电力缺陷模型”。
3. 通过文件选择器导入模型。
4. 模型加载成功后，应用会切换到自定义模型；加载失败时会返回内置 MobileNet 模型并显示原因。

导入模型必须兼容 TensorFlow Lite Task Vision `ObjectDetector`，并包含它需要的模型 Metadata、标签和检测输出定义。原始 Ultralytics YOLO TFLite 导出文件的输出结构可能不同，不保证可以直接加载；必要时需要增加 YOLO 输出解析适配器。

## 技术栈

- Kotlin
- Jetpack Compose
- CameraX
- TensorFlow Lite 与 TensorFlow Lite Task Vision
- Room
- Kotlin Coroutines
- NanoHTTPD
- Gradle Wrapper 9.3.1

## 项目结构

```text
.
├─ app/src/main/assets/                  # 内置 TFLite 模型
├─ app/src/main/java/com/example/
│  ├─ data/                              # Room 数据库与巡检记录
│  ├─ tflite/                            # 模型配置与推理引擎
│  ├─ ui/                                # ViewModel、手机页面和主题
│  └─ web/                               # PC 控制台与局域网接口
├─ app/src/test/                         # Android 本地单元测试
├─ gradle/wrapper/                       # Gradle Wrapper
├─ tests/BatchScripts.Tests.ps1          # 打包和安装脚本回归测试
├─ 打包.bat
└─ 安装.bat
```

## 测试

批处理脚本回归测试：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\BatchScripts.Tests.ps1
```

Android Debug 单元测试需要 Java 21 或更高版本：

```powershell
.\gradlew.bat testDebugUnitTest
```

构建 Debug APK：

```powershell
.\gradlew.bat assembleDebug
```

## 后续计划

- 微调并接入面向电力设备缺陷的 YOLO 模型
- 为常见 YOLO TFLite 输出格式增加专用后处理适配器
- 补充真实手机端和 PC 控制台截图
- 增加更多真实设备上的推理性能与稳定性测试

## 注意事项

- 通用 MobileNet 模型的识别结果不能作为电力缺陷诊断结论。
- PC 控制台仅适合受信任的局域网环境，当前 Web 服务没有账号认证。
- 当前 CPU、GPU、NPU 选项是硬件适配入口；启用真实硬件 Delegate 还需要相应设备支持和推理引擎适配。
- 发布正式版本前，请配置独立的签名密钥，不要提交密钥文件或密码。
