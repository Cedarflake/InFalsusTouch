# 开发指南

项目使用 Flutter Material 3、原生 Kotlin 和 C++20，职责见 [架构说明](../ARCHITECTURE.md)。普通玩家直接使用 [发行包](https://github.com/Cedarflake/InFalsusTouch/releases)，无需以下环境。

## 环境与构建

- Windows 11；Visual Studio 2022 C++ Build Tools、Windows SDK、CMake。
- JDK 17 或 21；Gradle 8.11.1、Android Gradle Plugin 8.9.2、Kotlin 2.1.20。
- Android SDK Platform 35、Build Tools 35.0.0、platform-tools。
- Flutter 3.44.x，Dart 版本以 `pubspec.yaml` 为准。
- 集成测试使用 `uv` 管理的 Python 3.13。

在 `android/local.properties` 配置 `sdk.dir`，将 Flutter 加入 PATH。可选的 `scripts/bootstrap-android.ps1 -AcceptAndroidSdkLicense` 会安装项目内工具并接受 SDK 许可。

在项目根目录运行：

```powershell
flutter pub get
.\scripts\build-windows.ps1
.\scripts\build-android.ps1
```

Windows 脚本构建 Release 并运行 CTest，输出 `dist/InFalsusTouchHost.exe` 及配套 ADB。已有 Host 正在使用该文件时，可用 `-SkipDistCopy` 仅更新构建目录。

Android 脚本使用已缓存的 Flutter 依赖，Gradle 按需下载依赖，串行运行 Flutter 分析与测试、Kotlin 测试及 lint。缓存完整后可加 `-Offline` 离线构建。默认输出开发签名的 `dist/InFalsusTouch.apk`。版本统一读取 `pubspec.yaml`。

```powershell
.\scripts\build-android.ps1 -Mode profile -DeviceTests
```

profile 模式使用 Flutter AOT，输出 `dist/InFalsusTouch-profile.apk`，用于性能和设备调试。它与 debug 包使用同一开发签名；二者均不作为 GitHub 发行包。

## 自动化与实机验证

```powershell
uv run --python 3.13 tests/tcp-integration.py --host build/windows/windows/Release/InFalsusTouchHost.exe
uv run --python 3.13 tests/multiplayer-integration.py
uv run --python 3.13 tests/profile-integration.py
.\scripts\test-device.ps1
```

先构建 Windows 后，Android 测试还会通过真实 Host 的 dry-run 模式验证 Kotlin/C++ 互通。常规集成测试不会向游戏注入输入。

视频测试使用项目自带的 Direct3D 窗口：

```powershell
uv run --python 3.13 tests/video-integration.py
uv run --python 3.13 tests/video-recovery.py
uv run --python 3.13 tests/video-device.py --build-mode profile
uv run --python 3.13 tests/video-device.py --build-mode profile --lifecycle-cycles 3 --skip-install
uv run --python 3.13 tests/multiplayer-video.py
uv run --python 3.13 tests/video-frame-rate.py --device
```

恢复测试需要 ffmpeg。`--skip-install` 仅在设备已安装对应应用和测试 APK 时使用。设备测试可能重建 Activity、切换后台及重建 Surface，不应在正在游玩时运行。结果保存在忽略的 `build/` 目录。

帧率测试将 Host 后备值设为 30，验证手机覆盖它、在线切换、多设备取最低请求及断线解除限制。`--device` 还会通过手机设置界面切换 60／120 FPS，检查实际呈现和重启保存；测试后恢复应用设置及 USB 转发。

检查间歇性卡顿时，使用帧间隔采样；默认采集测试窗口，`--window` 可指定 `InFalsusTouchHost.exe --list` 列出的游戏窗口句柄：

```powershell
uv run --python 3.13 tests/video-cadence.py --fps 120 --seconds 60 --skip-install
uv run --python 3.13 tests/video-cadence.py --fps 120 --seconds 60 --skip-install --window <窗口句柄>
```

该测试使用已安装的 profile 应用和测试 APK，不发送游戏输入。它分别记录接收、解码和呈现的最大间隔、每秒帧率及解码链恢复次数，结束后恢复连接与应用设置。

进一步分析帧时间可用：

```powershell
uv run --python 3.13 tests/video-system-trace.py --fps 60 --build-mode profile --skip-install
uv run --python 3.13 tests/analyze-video-trace.py build/video-system-trace/<本次目录> --processor <trace_processor_shell.exe路径>
```

跟踪有额外开销，不能将其吞吐量作为关闭跟踪后的性能结论。电脑和手机时钟不能直接相减；帧呈现回调不代表物理屏幕发光时间。

带有 `native-input`、`game-input`、`game-field` 名称的工具及 `tests/direct-field-game.py` 会发送实际输入，运行前必须核对目标窗口。合成 MotionEvent 无法证明真实多指或完整谱面表现，实机检查见 [验收清单](../tests/manual-acceptance.md)。

## 发布

发布 Android 包必须使用独立签名，通过当前进程的环境变量提供：

| 变量 | 内容 |
| --- | --- |
| `IFT_SIGNING_STORE_FILE` | 发布密钥库绝对路径 |
| `IFT_SIGNING_STORE_PASSWORD` | 密钥库密码 |
| `IFT_SIGNING_KEY_ALIAS` | 密钥别名 |
| `IFT_SIGNING_KEY_PASSWORD` | 密钥密码 |

不要将密钥、密码或签名配置提交到仓库。版本及 Android versionCode 在 `pubspec.yaml` 更新；正式升级必须保留签名并递增 versionCode。

```powershell
.\scripts\build-windows.ps1 -SkipDistCopy
.\scripts\build-android.ps1 -Mode release
.\scripts\package-release.ps1
```

release 模式运行同样的源码检查，启用 Flutter AOT 与 Android release 构建，并输出 `dist/InFalsusTouch-release.apk`。打包脚本验证签名、版本、ABI 和非调试属性，使用本次 Windows 构建产物，生成 APK、Windows x64 ZIP 和 SHA-256 校验文件。

真机检查发行模式时，可用 `tests/release-smoke.init.gradle` 作为 Gradle init script 构建临时应用，应用 ID 为 `dev.cedarflake.iftreleasecheck`。它沿用 release 优化与签名配置，独立验证启动、设置和 USB 视频，避免替换正在使用的应用。该临时包不作为发行资产；打包脚本只接受正式应用 ID。

维护者本机的首次发布密钥保存在 `%LOCALAPPDATA%\InFalsusTouch\release-signing`，密码凭据由当前 Windows 用户保护；它不是仓库内容。发布前应妥善备份密钥及可恢复的密码，不能只复制受当前用户保护的凭据文件到另一台机器。后续更新必须复用同一密钥。

确认测试及资产后，以对应提交建立 `v版本号` 标签，在 GitHub 创建发布。预览版本标记为 prerelease，发行说明使用中文。开发签名包不能直接覆盖安装发布签名包，不要在验证过程中擅自卸载用户设备上的旧应用。

## 维护约定

- 使用 Conventional Commits，英文摘要不超过 20 个单词。
- 保持输入与视频分离，输入状态必须在失焦、取消、断线时释放。
- 不提交 SDK、构建缓存、安装包、诊断日志、密钥或游戏资源。
- 修改协议需同步 Android、Host、协议文档和测试向量。
- 不把模拟测试、平均帧率或局部延迟当作实机游玩验收。

[输入协议](../protocol/INPUT_PROTOCOL.md) · [视频协议](../protocol/VIDEO_PROTOCOL.md)
