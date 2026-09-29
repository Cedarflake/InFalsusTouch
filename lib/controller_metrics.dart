import "app_strings.dart";
import "native_controller.dart";

String compactMetricsText(ControllerState state) {
  final fps = state.hasVideo
      ? ((state.video["presentFps"] as num?) ?? 0).round().toString()
      : "—";
  final refresh = state.displayHz > 0
      ? state.displayHz.round().toString()
      : "—";
  final rtt = state.connected ? state.rtt.toStringAsFixed(1) : "—";
  return "$fps FPS · $refresh Hz · RTT $rtt ms";
}

String metricsText(ControllerState state, AppStrings strings) {
  String value(String key, [int digits = 1]) =>
      ((state.video[key] as num?) ?? 0).toStringAsFixed(digits);
  return "${strings.videoSource} ${value("width", 0)} × ${value("height", 0)} · ${value("targetFps", 0)} FPS\n"
      "${strings.received} ${value("receiveFps")} · ${strings.presented} ${value("presentFps")} fps\n"
      "${strings.queue} ${value("queueDepth", 0)} · ${strings.drops} ${value("droppedFrames", 0)} · RTT ${state.rtt.toStringAsFixed(1)} ms\n"
      "${strings.decode} ${value("decoderMs")} · ${strings.latency} ${value("receiveToPresentMs")} ms";
}
