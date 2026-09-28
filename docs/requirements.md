我要创建一个专门用于游玩 PC 音游 In Falsus 的 Android 触控控制器项目，暂定名 **InFalsusTouch**。

这个项目不是通用远程桌面，也不是普通手机键盘/鼠标工具。它的目标是：

**通过 USB 数据线，把 Windows PC 上的 In Falsus 游戏画面以尽可能低的延迟实时显示在 Android 手机上，同时把手机变成适配 In Falsus 的多点触控音游控制器。**

请先完整理解下面的设计目标，然后建立项目架构并实现。不要擅自把项目简化成 Wi-Fi 串流或普通虚拟手柄。

## 一、最终交互方式

Android 手机横屏使用。

界面逻辑：

```text
┌────────────────────────────────────┐
│                                    │
│          In Falsus 实时画面         │
│                                    │
│                                    │
│       Field 触摸控制区域            │
│   手指左右移动 → 游戏鼠标水平移动    │
│                                    │
├────────────────────────────────────┤
│  1  │  2  │  3  │  4  │  5  │  6 │
│Shift│  A  │  S  │  D  │  F  │Space│
└────────────────────────────────────┘
```

同时也必须支持另一种布局：

**Overlay 模式**

游戏画面铺满手机屏幕，下方 6K 按键以可调整透明度的方式覆盖在画面底部。因为很多手机横屏比例为 20:9，如果强行给下方按键预留大量空间，会导致 16:9 游戏画面过小。

所以至少提供：

1. `Overlay`
   - 视频铺满屏幕；
   - 6K 按键覆盖底部；
   - 默认推荐模式。

2. `Reserved`
   - 上方区域显示视频；
   - 下方单独保留 6K 控制区。

允许用户调整：
- 6K 区域高度；
- Field 区域高度；
- 按键透明度；
- 视频 Fit / Fill；
- 是否裁切；
- Field 实际有效横向范围。

## 二、项目组成

最终只需要用户运行两个程序：

```text
InFalsusTouchHost.exe   # Windows
InFalsusTouch.apk       # Android
```

代码仓库建议结构：

```text
InFalsusTouch/
├─ android/
│  ├─ app/
│  ├─ video/
│  ├─ touch/
│  ├─ transport/
│  └─ settings/
│
├─ windows/
│  ├─ capture/
│  ├─ encoder/
│  ├─ transport/
│  ├─ input/
│  ├─ target/
│  └─ config/
│
├─ protocol/
├─ tests/
└─ docs/
```

不要把大量逻辑堆进单个文件。

代码必须保持正常的人类可读性，不要为了减少代码量使用极端压缩写法、大量嵌套三元表达式、超长函数、无意义缩写或把多个职责塞进一个类。

## 三、技术栈

### Android

使用：

- Kotlin
- Android Studio / Gradle
- 原生 Android API
- `SurfaceView` 或适合低延迟 MediaCodec 输出的 Surface
- `MediaCodec` 硬件解码 H.264
- 自定义 View 处理多点触控

触控部分优先使用 Android 原生 `MotionEvent`，不要依赖 WebView。

Compose 可以用于设置页面，但音游触控区域不要为了方便全部塞进复杂 Compose 手势系统；关键触控区域应尽量简单、直接、低开销。

### Windows

优先：

- C++20
- CMake
- Windows Graphics Capture
- Media Foundation
- Windows `SendInput`
- Win32 API

视频编码优先使用 Media Foundation 的硬件 H.264 Encoder MFT。

如果当前机器不存在硬件编码器，应有清楚的 fallback 或错误提示，而不是静默失败。

## 四、USB 通信

第一版不要自己开发 USB 驱动。

MVP 使用：

```text
ADB reverse + localhost TCP
```

Android 开启 USB 调试并通过数据线连接 PC。

Windows Host 启动两个 localhost TCP 服务，例如：

```text
27183  Video
27184  Control/Input
```

提供辅助脚本：

```text
adb reverse tcp:27183 tcp:27183
adb reverse tcp:27184 tcp:27184
```

Android 连接：

```text
127.0.0.1:27183
127.0.0.1:27184
```

视频和控制通道分离，避免视频拥塞阻塞按键输入。

输入通道必须优先保证低延迟。

以后可以再考虑真正的 USB 协议，但不要作为 MVP 的前置条件。

## 五、Android 多点触控逻辑

这是项目最重要的部分之一。

界面分成：

```text
Field Region
6K Region
```

每根手指 `ACTION_DOWN / ACTION_POINTER_DOWN` 时确定所属区域。

一旦某个 pointer 被分配给某一区域，在它抬起前必须锁定归属。

例如：

```text
Pointer 7
DOWN 在 Lane 3
→ Lane 3 KeyDown

即使手指随后滑进 Lane 4
→ 仍然保持 Lane 3

Pointer 7 UP
→ Lane 3 KeyUp
```

禁止滑动过程中自动串轨。

### 6K

横向六等分：

```text
Lane 1 → Shift
Lane 2 → A
Lane 3 → S
Lane 4 → D
Lane 5 → F
Lane 6 → Space
```

必须支持：

- 单押；
- 双押；
- 三押；
- 六押；
- Hold；
- 不同手指独立 KeyDown / KeyUp；
- 快速连续点击。

同一 Lane 如果意外存在两个 pointer，使用引用计数：

```text
lanePressedCount++
lanePressedCount--
```

只有从 `0 → 1` 时发送 KeyDown。

只有从 `1 → 0` 时发送 KeyUp。

避免一根手指先抬起造成另一根仍按住时提前 KeyUp。

按钮视觉仅作为反馈，不应该参与实际触摸判定逻辑。

## 六、Field 控制

Field 区域主要需要水平位置。

第一版提供两种模式。

### Absolute

默认模式。

Android 发送归一化坐标：

```text
x = touchX / fieldWidth
```

范围：

```text
0.0 ～ 1.0
```

Windows Host 根据 In Falsus 游戏窗口客户区计算目标鼠标位置。

例如：

```text
0.0 → 游戏 Field 最左
0.5 → 游戏 Field 中央
1.0 → 游戏 Field 最右
```

允许用户设置：

```text
FieldLeft
FieldRight
```

例如只使用游戏窗口横向的：

```text
5% ～ 95%
```

避免直接绑定整个 Windows 桌面。

Y 坐标默认固定到游戏客户区的某个可配置位置，因为主要需求是水平控制。

不要让 Android Touch 直接移动 Windows 系统光标；由 Windows Host 完成坐标映射。

### Relative

作为备用模式。

Android 发送：

```text
deltaX
```

Windows 转换成相对 Mouse Move。

允许调节：
- 灵敏度；
- 加速度；
- 平滑；
- 最大速度。

Absolute 是项目默认目标。

## 七、Field 与 6K 必须能够同时使用

例如：

```text
Pointer A
→ Field 区域持续左右滑

Pointer B
→ Lane 2 Hold

Pointer C
→ Lane 4 点击

Pointer D
→ Lane 6 点击
```

以上四个触点必须互不干扰。

Field Pointer 不能因为下面出现新的 Pointer 而失去控制。

6K Pointer 也不能改变 Field 的 pointer ID。

## 八、Windows 输入注入

收到：

```text
LANE_DOWN 3
```

Windows 执行：

```text
S KeyDown
```

收到：

```text
LANE_UP 3
```

执行：

```text
S KeyUp
```

使用 `SendInput`。

不要使用轮询键盘状态模拟。

Field 使用 Mouse `SendInput`。

程序关闭、Android 断连、网络连接异常时必须立刻释放所有可能仍处于 KeyDown 状态的按键：

```text
Shift
A
S
D
F
Space
```

避免出现“键卡住”。

## 九、视频采集

Windows Host 应允许用户选择一个目标窗口。

优先自动识别：

```text
In Falsus
```

但不要硬编码死进程名称，应允许手动选择窗口。

优先使用：

```text
Windows Graphics Capture
```

而不是不断截图。

只采集目标游戏窗口，不需要传输整个桌面。

## 十、视频编码

目标不是极致画质，而是：

**低延迟。**

第一阶段：

```text
H.264
60 FPS
硬件编码
```

参数设计偏向：

- low latency；
- 禁止或减少帧重排序；
- 不使用 B Frame；
- 短 GOP；
- 不积压帧；
- 新帧到来时，如果旧帧来不及处理，宁可丢旧帧，也不要累积延迟。

提供可调整：

```text
720p60
1080p60

Bitrate
FPS
```

如果设备和 PC 支持，后续再考虑：

```text
90 FPS
120 FPS
```

但不要在 MVP 阶段为了 120 FPS 把架构搞复杂。

## 十一、Android 视频解码

使用 Android `MediaCodec`。

输出到 Surface。

重点：

**不要让解码队列不断积累。**

宁愿掉一帧，也不能因为缓冲越来越多导致延迟从 20 ms 慢慢涨到 200 ms。

需要记录：

```text
captureTimestamp
encodeTimestamp
sendTimestamp
receiveTimestamp
decodeTimestamp
presentTimestamp
```

至少调试模式能够显示基础统计信息，例如：

```text
FPS
Bitrate
Decode FPS
Dropped Frames
Video queue depth
Ping
Input RTT
```

如果无法可靠测量完整 glass-to-glass latency，就不要伪造一个“总延迟”数字。

## 十二、视频布局

Android 收到的是正常游戏画面。

不要要求 Windows 把游戏强行改成手机奇怪的宽高比。

Android 支持：

```text
FIT
FILL
CROP
```

Overlay 模式：

```text
┌──────────────────────────┐
│                          │
│      Game Video          │
│                          │
│                          │
│                          │
│1 │2 │3 │4 │5 │6         │ ← 半透明Overlay
└──────────────────────────┘
```

Reserved 模式：

```text
┌──────────────────────────┐
│                          │
│      Game Video          │
│                          │
├──────────────────────────┤
│1 │2 │3 │4 │5 │6         │
└──────────────────────────┘
```

## 十三、触控 UI

6K 不需要花哨 UI。

默认：

```text
│ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │
```

每轨：
- 边界清晰；
- 按下时亮起；
- Hold 时保持亮起；
- 松开立即恢复。

可选隐藏文字。

允许调节：
- 高度；
- 透明度；
- lane gap；
- 视觉亮度；
- 是否显示编号。

不要加入动画弹跳、渐变动画等可能影响性能的装饰。

## 十四、性能原则

这是音游控制器。

优先级：

```text
Input latency
>
Input stability
>
Video latency
>
Frame rate stability
>
Image quality
>
UI 美观
```

输入事件绝不能因为视频编码卡顿而等待。

视频线程、输入线程和 UI 线程必须合理分离。

禁止因为日志输出导致高频输入路径明显阻塞。

高频事件不要做大量对象分配。

## 十五、开发顺序

不要一口气写完然后最后才测试。

### Phase 1 — Input Prototype

先不做视频。

Android：
- Field；
- 6K；
- MultiTouch；
- TCP 输入发送。

Windows：
- TCP Receiver；
- SendInput；
- 鼠标控制。

做到可以真正启动 In Falsus 玩。

验收：

- 六键分别正确；
- 多押正常；
- Hold 正常；
- 无卡键；
- Field 与 6K 可以同时操作；
- 断线自动释放按键。

### Phase 2 — Video Prototype

加入：

```text
Windows Graphics Capture
→ H.264
→ TCP
→ Android MediaCodec
```

先实现稳定 720p60。

### Phase 3 — Low Latency Optimization

再：
- 减少 frame queue；
- 优化编码；
- 优化 decoder；
- 优化 transport；
- 加统计信息。

### Phase 4 — UX

最后再：
- 设置页；
- 自动发现 Host；
- 保存配置；
- Field Calibration；
- Overlay / Reserved；
- 视频质量选项。

## 十六、测试

至少加入自动化测试覆盖：

- lane X 坐标 → 正确轨道；
- Pointer lock；
- 多 pointer；
- 同一 Lane 多 pointer 引用计数；
- disconnect 后释放全部按键；
- protocol encode/decode；
- normalized Field X 映射；
- malformed packet；
- reconnect。

不要只写“能跑”的代码而完全没有测试。

## 十七、文档

README 必须包含：

### Requirements

Windows：

```text
Windows 11
ADB
支持 Windows Graphics Capture
```

Android：

```text
Android 手机
USB 数据线
USB Debugging
```

### Quick Start

类似：

```text
1. 手机开启 USB Debugging
2. USB 连接 PC
3. adb devices
4. 运行 scripts/setup-adb.ps1
5. 启动 InFalsusTouchHost.exe
6. 选择 In Falsus 窗口
7. 打开 Android App
8. Connect
```

### Architecture

画出：

```text
In Falsus
    │
Windows Graphics Capture
    │
H.264 Encoder
    │
USB/TCP ─────────→ Android MediaCodec
                         │
                         ▼
                     Game Video

Android MultiTouch
    │
USB/TCP
    │
Windows Host
    │
SendInput
    │
In Falsus
```

## 十八、编码要求

请特别遵守：

- 不要生成高度压缩、难以阅读的 AI 风格代码；
- 不要把整个项目塞进几个超大文件；
- 一个函数尽量只做一件事；
- 类和变量名称表达真实职责；
- 不要出现几十层嵌套逻辑；
- 避免无必要的 abstraction；
- 不要为了“优雅”做过度工程；
- 错误必须显式处理；
- 关键协议和线程模型写清楚注释；
- 普通代码不要堆大量解释性废话注释；
- 优先可维护性和可调试性。

所有高频路径都要考虑线程安全和资源生命周期。

## 十九、第一步

先不要直接输出几千行代码。

首先：

1. 创建完整仓库骨架；
2. 写 `ARCHITECTURE.md`；
3. 明确 Android/Windows/Protocol 三部分职责；
4. 定义输入协议；
5. 实现 Phase 1；
6. 编译并运行测试；
7. 修复实际编译错误；
8. 确认 Phase 1 可运行后，再进入视频部分。

如果当前环境无法连接 Android 真机，可以完成编译、单元测试和模拟输入测试，但必须清楚标记哪些部分尚未经过真机验证。

不要假装已经验证未实际测试的硬件行为。

最终目标不是制作通用遥控软件，而是制作一个：

**低延迟、USB 有线、专门适配 In Falsus 的 Android 触控音游控制器。**