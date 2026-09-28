import "dart:async";

import "package:flutter/services.dart";
import "package:flutter/widgets.dart";

Map<String, Object?> objectMap(Object? value) {
  if (value is! Map<Object?, Object?>) return {};
  return {
    for (final entry in value.entries)
      if (entry.key case final String key) key: entry.value,
  };
}

class ControllerSettings {
  ControllerSettings(Map<String, Object?> values)
    : values = Map.unmodifiable(values);

  final Map<String, Object?> values;
  String text(String key, [String fallback = ""]) => switch (values[key]) {
    final String value => value,
    _ => fallback,
  };
  double number(String key, [double fallback = 0]) => switch (values[key]) {
    final num value => value.toDouble(),
    _ => fallback,
  };
  bool flag(String key) => values[key] == true;
  int get controlsMask => (values["controlsMask"] as num?)?.toInt() ?? 127;
  Map<String, Object?> get judgment => objectMap(values["judgment"]);
  ControllerSettings withValue(String key, Object? value) =>
      ControllerSettings({...values, key: value});
}

class ControllerState {
  ControllerState(Map<String, Object?> data)
    : values = data,
      settings = ControllerSettings(objectMap(data["settings"]));

  final Map<String, Object?> values;
  final ControllerSettings settings;
  String get panel => values["panel"] as String? ?? "compact";
  String get connection => values["connection"] as String? ?? "DISCONNECTED";
  bool get connected => connection == "CONNECTED";
  bool get searching =>
      values["searching"] == true || connection == "CONNECTING";
  bool get connectionFailed => values["connectionFailed"] == true;
  bool get ready => values["targetReady"] == true;
  bool get hasVideo => values["hasVideo"] == true;
  String get language => values["language"] == "zh" ? "zh" : "en";
  String? get notice => values["notice"] as String?;
  String get detail => values["detail"] as String? ?? "";
  double get density => (values["density"] as num?)?.toDouble() ?? 1;
  double get rtt => (values["rtt"] as num?)?.toDouble() ?? 0;
  double get displayHz => (values["displayHz"] as num?)?.toDouble() ?? 0;
  int get peers => (values["peers"] as num?)?.toInt() ?? 0;
  int get bindingStatus => (values["bindingStatus"] as num?)?.toInt() ?? 1;
  bool get fieldBusy => values["fieldStatus"] == 2;
  List<String> get keyLabels => switch (values["keyLabels"]) {
    final List<Object?> labels when labels.length == 6 => labels.cast<String>(),
    _ => const ["Shift", "A", "S", "D", "F", "Space"],
  };
  Map<String, Object?> get video => objectMap(values["video"]);
  List<Rect> get cutouts {
    final items = values["cutouts"];
    if (items is! List<Object?>) return [];
    return [
      for (final item in items)
        if (item is List<Object?> &&
            item.length == 4 &&
            item.every((value) => value is num))
          Rect.fromLTRB(
            (item[0] as num).toDouble() / density,
            (item[1] as num).toDouble() / density,
            (item[2] as num).toDouble() / density,
            (item[3] as num).toDouble() / density,
          ),
    ];
  }

  Rect? get picture {
    final placement = objectMap(values["picture"]);
    if (placement.isEmpty) return null;
    double value(String key) => (placement[key]! as num).toDouble() / density;
    return Rect.fromLTWH(
      value("left"),
      value("top"),
      value("width"),
      value("height"),
    );
  }
}

class NativeController extends ChangeNotifier with WidgetsBindingObserver {
  NativeController({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel("dev.cedarflake.ift/ui");

  final MethodChannel _channel;
  ControllerState? state;
  String? error;
  bool _isDisposed = false;
  bool isConnectionCommandPending = false;
  final _queuedSettings = <String, Object?>{};
  Map<String, Object?> _writingSettings = {};
  Timer? _settingsTimer;
  Future<bool>? _settingsWrite;
  bool isResettingDefaults = false;

  ControllerSettings get settings => ControllerSettings({
    ...?state?.settings.values,
    ..._writingSettings,
    ..._queuedSettings,
  });
  String get language => switch (settings.text("language")) {
    "en" => "en",
    "zh" => "zh",
    _ => state?.language ?? "en",
  };

  Future<void> initialize() async {
    WidgetsBinding.instance.addObserver(this);
    _channel.setMethodCallHandler((call) async {
      if (call.method == "state") _receive(call.arguments);
    });
    await command("state");
  }

  void _receive(Object? data) {
    if (_isDisposed) return;
    state = ControllerState(objectMap(data));
    notifyListeners();
  }

  Future<bool> command(String method, [Object? arguments]) async {
    try {
      final result = await _channel.invokeMethod<Object?>(method, arguments);
      error = null;
      if (result != null) _receive(result);
      return true;
    } on PlatformException catch (failure) {
      error = failure.code;
    } on MissingPluginException {
      error = "bridge_unavailable";
    }
    if (!_isDisposed) notifyListeners();
    return false;
  }

  void updateSetting(String key, Object value, {bool defer = false}) {
    if (_isDisposed || isResettingDefaults) return;
    _queuedSettings[key] = value;
    notifyListeners();
    _settingsTimer?.cancel();
    if (defer) {
      _settingsTimer = Timer(const Duration(milliseconds: 180), flushSettings);
    } else {
      unawaited(flushSettings());
    }
  }

  Future<bool> flushSettings() {
    _settingsTimer?.cancel();
    if (_settingsWrite case final Future<bool> active) return active;
    if (_queuedSettings.isEmpty) return Future.value(true);
    final operation = _writeSettings().whenComplete(() {
      _settingsWrite = null;
      if (!_isDisposed) notifyListeners();
    });
    _settingsWrite = operation;
    return operation;
  }

  Future<bool> _writeSettings() async {
    while (_queuedSettings.isNotEmpty) {
      _writingSettings = Map.of(_queuedSettings);
      _queuedSettings.clear();
      final saved = await command("save", _writingSettings);
      if (!saved) {
        final newer = Map.of(_queuedSettings);
        _queuedSettings.addAll({..._writingSettings, ...newer});
      }
      _writingSettings = {};
      if (!saved) return false;
    }
    return true;
  }

  Future<bool> restoreDefaults() async {
    if (isResettingDefaults) return false;
    isResettingDefaults = true;
    notifyListeners();
    try {
      await flushSettings();
      final restored = await command("defaults");
      if (restored) _queuedSettings.clear();
      return restored;
    } finally {
      isResettingDefaults = false;
      if (!_isDisposed) notifyListeners();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) unawaited(flushSettings());
  }

  Future<bool> panel(String value) async {
    await flushSettings();
    return command("panel", value);
  }

  Future<bool> settingsHint(String message) => command("settingsHint", message);
  Future<void> toggleConnection() async {
    if (isConnectionCommandPending) return;
    isConnectionCommandPending = true;
    notifyListeners();
    try {
      await command("connect");
    } finally {
      isConnectionCommandPending = false;
      if (!_isDisposed) notifyListeners();
    }
  }

  Future<bool> uiRegions(String panel, List<Rect> rects) =>
      command("uiRegions", {
        "panel": panel,
        "rects": [
          for (final rect in rects)
            [rect.left, rect.top, rect.right, rect.bottom],
        ],
      });
  Future<bool> save(ControllerSettings value) async {
    await flushSettings();
    return command("save", value.values);
  }

  @override
  void dispose() {
    unawaited(flushSettings());
    WidgetsBinding.instance.removeObserver(this);
    _isDisposed = true;
    _channel.setMethodCallHandler(null);
    super.dispose();
  }
}
