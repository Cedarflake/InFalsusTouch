# 开发指南

界面使用 Flutter Material 3，Android 触控和视频由原生 Kotlin 处理；
Windows Host 使用 C++20 / Win32。模块和线程职责见 [架构说明](../ARCHITECTURE.md)。

## 准备环境

- Windows 11。
- Visual Studio 2022 Build Tools：Desktop development with C++、Windows SDK、CMake。
- JDK 17 或 21；项目使用 Gradle 8.11.1、Android Gradle Plugin 8.9.2、Kotlin 2.1.20。
- Android SDK Platform 35、Build Tools 35.0.0、platform-tools。
- Flutter 3.44.x / Dart 3.12.2 及以上（以 `pubspec.yaml` 为准）。
- 集成测试使用 `uv` 管理的 Python 3.13。

在项目根目录的 PowerShell 中运行：

```powershell
# 已有 Android SDK 时，直接在 android/local.properties 中配置 sdk.dir。
# 此可选脚本接受 Android SDK 许可，将工具安装到项目内 .tools。
.\scripts\bootstrap-android.ps1 -AcceptAndroidSdkLicense

flutter pub get
.\scripts\build-windows.ps1
.\scripts\build-android.ps1
```

Android 构建脚本使用已缓存的 Flutter 依赖。Windows 脚本构建 Release 并运行 CTest；
Android 脚本运行 Flutter 分析、界面测试、Kotlin 测试和 lint，然后生成 debug APK。
先构建 Windows 时，Android 测试会额外启动真实 Host 的 dry-run 模式，
验证 Kotlin/C++ 握手、六押、Field、断线释放与重连。

产物是 `dist/InFalsusTouchHost.exe` 和 `dist/InFalsusTouch.apk`。
APK 使用开发签名。SDK、依赖缓存与构建输出不提交到 Git。
Java 临时套接字目录由脚本设在项目内，只对当前进程生效。

## 测试

```powershell
uv run --python 3.13 tests/tcp-integration.py --host dist/InFalsusTouchHost.exe
uv run --python 3.13 tests/multiplayer-integration.py
uv run --python 3.13 tests/profile-integration.py

# 安装应用及测试 APK，使用 dry-run Host 验证真机 USB 输入。
.\scripts\test-device.ps1

# 使用项目自己的 Direct3D 测试窗口验证视频，不注入游戏输入。
uv run --python 3.13 tests/video-integration.py
.\scripts\build-android.ps1 -DeviceTests
uv run --python 3.13 tests/video-device.py
uv run --python 3.13 tests/multiplayer-video.py
```

常规自动化使用 dry-run 输入接收端。`native-input`、`game-input` 和 `game-field`
验收工具会向指定前台窗口发送实际输入，运行前应核对目标窗口。
真机测试注入的 MotionEvent 是合成事件，不能证明物理七指能力或完整谱面的操作效果。

RTT 是控制协议往返耗时，视频统计是各个处理阶段的测量；
两端时钟未经同步，不能直接相减或当作屏幕端到端延迟。
当前结果见 [STATUS](STATUS.md)，待测项目见 [实机验收清单](../tests/manual-acceptance.md)。

## 连接与运行排查

- `adb devices -l` 应显示 `device`；`unauthorized` 需要在手机上确认授权。
- `setup-adb.ps1` 支持 `-Serial`、`-AdbPath`、`-AllDevices` 和 `-Install`。
  它将视频端口 27183、输入端口 27184 映射到电脑；拔插设备或重启 ADB 后需重新运行。
- 电脑需支持 Windows Graphics Capture 和同一 GPU 上的 D3D11 硬件 H.264 编码器，
  手机需支持目标分辨率与帧率的硬件 H.264 解码器。不支持时会报错，不静默改用软件编解码。
- 同时六押和操作 Field 需要至少七个触点，实际触点上限取决于手机硬件。
- Host 与游戏应使用相同权限等级。Host 只向选定的前台游戏窗口发送输入。
- 使用英语（美国）键盘布局。中文输入法切到英文输入模式，仍可能被 Shift 切回中文。
- Host 和 APK 必须一起更新；当前输入协议为 v2，不接受 v1 客户端。

```powershell
.\dist\InFalsusTouchHost.exe --list
.\dist\InFalsusTouchHost.exe --title "In Falsus"
.\dist\InFalsusTouchHost.exe --video-diagnostics
.\dist\InFalsusTouchHost.exe --no-video
.\dist\InFalsusTouchHost.exe --resolution 1080p --fps 60 --bitrate 12000000
```

`--window` 可精确选择窗口，句柄应取自本次 `--list` 输出。
视频默认 720p60 / 8 Mbps / H.264 Baseline / 半秒 GOP，仅捕获所选窗口客户区。
手机的 120 Hz 显示偏好与视频帧率相互独立，实际刷新率由设备和系统决定。
配置文件、视频选项和 Field 校准见 [设置文档](SETTINGS.md)。

## 多设备与键位同步

一个 Host 最多接收 7 台设备，所有设备共享一次游戏窗口采集和编码。
每台设备可以选择任意操作，包括只看画面，不要求合计覆盖所有按键。
共同按住同一键时，最后一台设备松手才抬键；断开只释放本设备的输入。
Field 由先触摸的设备持有，松手后交给等待中的设备。

Host 只读监测 `%USERPROFILE%/AppData/LocalLow/lowiro/infalsus/userV2.prefs`，
同步游戏保存的六轨键名和扫描码，不修改存档。
当前支持已观察到的 `keybind_state=0` 键盘绑定；未知键或其他绑定状态会暂停输入。
`--bindings PATH` 指定配置位置，`--no-key-sync` 使用默认键位。
绑定变更时先释放旧键，并要求新的触摸按下。

正常断连会释放对应设备的按键；静默断线使用约 500 ms 的心跳超时，加上系统调度开销。
强制杀进程或系统崩溃不保证执行清理。协议只面向本机和已授权 USB 设备，没有额外身份认证。

## 进一步阅读

- [输入协议](../protocol/INPUT_PROTOCOL.md)
- [视频协议](../protocol/VIDEO_PROTOCOL.md)
- [原始需求](requirements.md)
- [架构说明](../ARCHITECTURE.md)
