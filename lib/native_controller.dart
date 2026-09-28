import "package:flutter/foundation.dart";
import "package:flutter/services.dart";

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

class NativeController extends ChangeNotifier {
  NativeController({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel("dev.cedarflake.ift/ui");

  final MethodChannel _channel;
  ControllerState? state;
  String? error;
  bool _isDisposed = false;
  bool isConnectionCommandPending = false;

  Future<void> initialize() async {
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

  Future<bool> panel(String value) => command("panel", value);
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
  Future<bool> save(ControllerSettings value) => command("save", value.values);
  Future<bool> language(String value) => command("save", {"language": value});

  @override
  void dispose() {
    _isDisposed = true;
    _channel.setMethodCallHandler(null);
    super.dispose();
  }
}
