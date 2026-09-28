class AppStrings {
  const AppStrings(this.language);
  final String language;
  bool get zh => language == "zh";

  String get settings => zh ? "设置" : "Settings";
  String get settingsAgain => zh ? "再按一下进入设置" : "Tap again to open settings";
  String get title => zh ? "控制设置" : "Controller settings";
  String get touch => zh ? "触控" : "Touch";
  String get controls => zh ? "按键显示" : "Controls";
  String get myControls => zh ? "这台设备显示什么" : "Choose this device’s controls";
  String get cooperativePlay => zh ? "多人合作" : "Play together";
  String get cooperativePlayHint => zh
      ? "最多 7 台设备可通过 USB 同时连接同一台电脑，共同游玩同一局 In Falsus。每个人选择自己负责的按键或 Field，分工完成谱面；电脑键盘和鼠标也能一起参与。各设备自由选择显示内容，无需凑齐全部操作。"
      : "Connect up to 7 devices to the same PC over USB to play one In Falsus session together. Each player chooses the keys or Field they handle and shares the chart. The PC keyboard and mouse can join in too. Each device chooses its own controls; there is no required combination.";
  String get allControls => zh ? "全部" : "All";
  String get keysOnly => zh ? "仅按键" : "Keys only";
  String get fieldOnly => zh ? "仅 Field" : "Field only";
  String get viewOnly => zh ? "只看画面" : "View only";
  String get syncedKeys => zh ? "已同步 IF 键位" : "IF bindings synced";
  String get defaultKeys =>
      zh ? "默认键位 · 连接后自动同步" : "Default keys · sync on connection";
  String get invalidKeys =>
      zh ? "IF 键位配置暂不可用，输入已暂停" : "IF bindings unavailable; input paused";
  String get fieldBusy => zh
      ? "另一台设备正在操作 Field，松手后会交接。"
      : "Another device is using Field. Control passes when it releases.";
  String peers(int count) => zh ? "$count 台设备已连接" : "$count devices connected";
  String get picture => zh ? "画面" : "Picture";
  String get connection => zh ? "连接" : "Connection";
  String get connectionLog => zh ? "连接日志" : "Connection log";
  String get other => zh ? "其他" : "Other";
  String get theme => zh ? "主题" : "Theme";
  String get systemTheme => zh ? "跟随系统" : "System";
  String get lightTheme => zh ? "浅色" : "Light";
  String get darkTheme => zh ? "深色" : "Dark";
  String get connect => zh ? "连接 USB" : "Connect USB";
  String get disconnect => zh ? "断开" : "Disconnect";
  String get connecting => zh ? "正在连接…" : "Connecting…";
  String get usbConnection => zh ? "USB 连接" : "USB connection";
  String get cancelConnection => zh ? "取消连接" : "Cancel";
  String get connectHint => zh
      ? "连接数据线，然后启动电脑端 Host。"
      : "Connect the cable and start Host on your PC.";
  String get retryHint => zh
      ? "正在重试，请检查数据线和电脑端 Host。"
      : "Retrying. Check your cable and the PC Host.";
  String get failedHint => zh
      ? "未能连接，请检查数据线和电脑端 Host。"
      : "Could not connect. Check your cable and PC Host.";
  String get connectingHint =>
      zh ? "正在确认 USB 连接…" : "Checking the USB connection…";
  String get collapse => zh ? "收起菜单" : "Collapse menu";
  String get ready => zh ? "可以开始游玩" : "Ready to play";
  String get focusGame => zh ? "请切回电脑上的游戏窗口" : "Focus the game on your PC";
  String get disconnected => zh ? "等待 USB 连接" : "Waiting for USB";
  String get save => zh ? "保存" : "Save";
  String get cancel => zh ? "取消" : "Cancel";
  String get defaults => zh ? "恢复默认" : "Defaults";
  String get confirmDefaultsTitle =>
      zh ? "恢复默认设置？" : "Restore default settings?";
  String get confirmDefaultsBody => zh
      ? "触控、判定线校准、按键显示、画面和连接设置将恢复默认。语言与主题会保留。"
      : "Reset touch, calibration, visible controls, picture and connection settings. Your language and theme will be kept.";
  String get confirmDefaults => zh ? "恢复默认" : "Restore defaults";
  String get back => zh ? "返回游戏" : "Back to game";
  String get menu => zh ? "打开菜单" : "Open menu";
  String get close => zh ? "关闭" : "Close";
  String get languageLabel => zh ? "界面语言" : "Interface language";
  String get feel => zh ? "找到顺手的位置" : "Make room for your fingers";
  String get feelHint => zh
      ? "触区可以向上加高，判定线位置保持不变。"
      : "Grow the touch areas upward while judgment lines stay in place.";
  String get buttonHeight => zh ? "按钮触控高度" : "Button touch height";
  String get opacity => zh ? "按钮透明度" : "Button opacity";
  String get brightness => zh ? "反馈亮度" : "Feedback brightness";
  String get gap => zh ? "按钮视觉间距" : "Visual button spacing";
  String get labels => zh ? "显示按键名称" : "Show key labels";
  String get buttonHaptics => zh ? "按键震动" : "Button vibration";
  String get buttonHapticsHint => zh
      ? "按下游戏按键时轻震，遵循系统触感反馈设置。"
      : "A light pulse when a game key is pressed. Follows system haptic settings.";
  String get layout => zh ? "触控布局" : "Touch layout";
  String get aligned => zh ? "对齐轨道" : "Aligned";
  String get overlay => zh ? "固定叠加" : "Fixed";
  String get reserved => zh ? "预留按键区" : "Reserved";
  String get alignedHint => zh
      ? "中央四轨与左右侧轨跟随游戏画面。Field 在按钮上方，只处理横向移动。"
      : "Four center lanes and two side lanes follow the picture. Field tracks horizontal movement above the buttons.";
  String get field => zh ? "Field 控制" : "Field control";
  String get absolute => zh ? "绝对位置（实验）" : "Absolute (experimental)";
  String get relative => zh ? "相对滑动" : "Relative";
  String get fieldHint => zh
      ? "水平滑动控制 Field，灵敏度请在 In Falsus 中调整。抬手后可从任意位置继续滑动。"
      : "Slide horizontally to move Field. Adjust sensitivity in In Falsus. Lift and touch anywhere to continue sliding.";
  String get absoluteFieldHint => zh
      ? "点按定位 Field，随后跟随手指横向移动，自动适配游戏灵敏度。目前支持 In Falsus 1.0.4b，需先对齐判定线。"
      : "Tap to position Field, then slide to follow your finger. Adapts to game sensitivity. Currently supports In Falsus 1.0.4b; align judgment lines first.";
  String get fieldHeight => zh ? "Field 触控高度" : "Field touch height";
  String get leftEdge => zh ? "Field 左边界" : "Field left edge";
  String get rightEdge => zh ? "Field 右边界" : "Field right edge";
  String get guide => zh ? "显示 Field 触区边界" : "Show Field touch boundary";
  String get align => zh ? "校准游戏判定线" : "Align judgment lines";
  String get calibrateField => zh ? "校准横向滑动范围" : "Calibrate Field area";
  String get calibrationHint => zh
      ? "设置自动保存，校准期间暂停游戏输入。"
      : "Settings save automatically. Game input pauses while calibrating.";
  String get framing => zh ? "完整画面，居中显示" : "Keep the whole chart in view";
  String get fit => zh ? "完整显示" : "Fit";
  String get stretch => zh ? "拉伸铺满" : "Stretch";
  String get crop => zh ? "裁切铺满" : "Crop";
  String get framingHint => zh
      ? "建议完整显示。宽屏两侧留白，不裁掉音符与判定线；裁切会隐藏上下画面。"
      : "Fit preserves the chart with side bars. Crop can hide notes and judgment lines.";
  String get highRefresh => zh ? "优先使用 120 Hz" : "Prefer 120 Hz";
  String get highRefreshHint => zh
      ? "让本地触控反馈更流畅；视频帧率独立，实际刷新率由系统决定。"
      : "Smoother local feedback on supported screens. Video FPS is separate; the system controls the actual refresh rate.";
  String get statistics => zh ? "显示性能统计" : "Show performance statistics";
  String get display => zh ? "屏幕" : "Display";
  String get received => zh ? "接收" : "Receive";
  String get presented => zh ? "呈现" : "Present";
  String get queue => zh ? "队列" : "Queue";
  String get drops => zh ? "丢帧" : "Drops";
  String get decode => zh ? "解码" : "Decode";
  String get encode => zh ? "捕获→编码" : "Capture→encode";
  String get latency => zh ? "接收→呈现" : "Receive→present";
  String get metricsHint => zh
      ? "这些是各设备内部的阶段耗时，不是触摸到画面的总延迟。"
      : "These local stage timings are not end-to-end touch-to-display latency.";
  String get autoConnect =>
      zh ? "自动寻找 USB Host" : "Find USB Host automatically";
  String get autoConnectHint => zh
      ? "应用在前台时重试连接；在电脑上先启动 Host 并完成 ADB reverse 设置。"
      : "Retry while the app is open. Start Host and configure ADB reverse on your PC first.";
  String get autoHide =>
      zh ? "连接后自动收起菜单" : "Collapse the menu after connecting";
  String get autoHideHint => zh
      ? "4 秒后收成侧边按钮，给读谱和手指留出空间。"
      : "After four seconds, leave a small side button for more playing space.";
  String get usbHint => zh
      ? "只使用 USB 数据连接。拔插数据线后，可能需要重新运行电脑上的 USB 设置脚本。"
      : "Uses USB data only. Reconnecting the cable may require running the PC USB setup script again.";
  String get inputHint => zh
      ? "按键名称会跟随 IF 保存的键位。请为游戏选择英语（美国）键盘，避免 Shift 切换输入法。"
      : "Labels follow IF’s saved key bindings. Use the US English keyboard layout so Shift cannot toggle an IME.";
  String get undo => zh ? "撤销一点" : "Undo point";
  String get calibrated =>
      zh ? "位置已记录，保存后应用。" : "Positions recorded. Save to apply.";
  List<String> get judgmentSteps => zh
      ? const [
          "点击天空判定线的左端",
          "点击天空判定线的右端",
          "点击中央地线的左端（第 2 轨）",
          "点击中央地线的右端（第 5 轨）",
          "点击左侧斜线的外端（第 1 轨）",
          "点击右侧斜线的外端（第 6 轨）",
        ]
      : const [
          "Tap the left end of the upper Field line",
          "Tap the right end of the upper Field line",
          "Tap the left end of the central Floor line (lane 2)",
          "Tap the right end of the central Floor line (lane 5)",
          "Tap the outer end of the left side line (lane 1)",
          "Tap the outer end of the right side line (lane 6)",
        ];
  List<String> get fieldSteps => zh
      ? const ["点击舒适滑动范围的左端", "点击舒适滑动范围的右端"]
      : const [
          "Tap the left edge of your comfortable Field range",
          "Tap its right edge",
        ];
}
