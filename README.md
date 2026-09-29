# InFalsusTouch

通过 USB，把 Android 手机变成 **In Falsus 的触控控制器**。
在手机上看游戏画面、左右滑动操作 Field，用六个触控按键点按和长按。

这是一个非官方项目，为 In Falsus 提供一种可选的触屏玩法。

> **目前是测试原型。** 游戏画面传输和多点输入已实现，游戏内 Field 的绝对位置对齐、
> 完整谱面实玩尚未完成验收。具体进度见 [验证记录](docs/STATUS.md)。

## 可以怎么玩

- 多指点按和长按，按下时显示反馈，按键名称自动跟随游戏保存的键位。
- 自由选择显示哪些按键，调整触区高度、透明度和布局；游戏画面默认完整、居中显示。
- 最多 7 台设备连接同一台电脑，各自负责不同按键或 Field，合作玩同一局；电脑键鼠也能参与。多人功能已通过模拟设备测试，真机协作仍待验证。
- 界面支持中文、英语及深浅主题，设置自动保存。

## 安装与连接

需要 Windows 11 电脑、支持多点触控的 Android 8.0 及以上手机，以及支持数据传输的 USB 线。
手机需要开启 USB 调试；视频还需要电脑和手机支持硬件编解码。

**目前需要自行构建，尚未提供正式发行包。**
按 [开发指南](docs/DEVELOPMENT.md) 构建后，会得到
`dist/InFalsusTouchHost.exe` 和 `dist/InFalsusTouch.apk`。

1. 手机开启 USB 调试，连接电脑，确认手机上的授权提示。
2. 在项目根目录运行 `.\scripts\setup-adb.ps1 -Install`，安装应用并配置 USB 连接。
3. 电脑打开 In Falsus，再运行 `dist/InFalsusTouchHost.exe`；如提示选择窗口，选择游戏。
4. 打开手机应用，连续点两次（防误触）左上角设置按钮，在“连接”页点击“连接 USB”，连接后返回游戏界面。
5. 将电脑焦点切回游戏，并选择**英语（美国）键盘布局**，避免 Shift 触发输入法切换。

重新插拔数据线后，可再次运行连接脚本。
多台设备使用 `.\scripts\setup-adb.ps1 -AllDevices -Install`，然后分别在“按键显示”中选择各自的操作。

## 文档与开发

- [设置与校准](docs/SETTINGS.md)
- [构建、测试和故障排查](docs/DEVELOPMENT.md)
- [架构与模块说明](ARCHITECTURE.md)
- [当前进度与验证记录](docs/STATUS.md)

<details>
<summary>技术架构概览</summary>

```mermaid
flowchart LR
  Game[In Falsus] --> Capture[Windows Graphics Capture]
  Capture --> Encoder[H.264 编码]
  Encoder -- USB --> Screen[Android 解码与显示]
  Touch[Android 多点触控] -- USB --> Host[Windows Host]
  Host --> Input[SendInput]
  Input --> Game
```

输入与视频使用独立通道。详细约定见 [输入协议](protocol/INPUT_PROTOCOL.md) 和 [视频协议](protocol/VIDEO_PROTOCOL.md)。

</details>
