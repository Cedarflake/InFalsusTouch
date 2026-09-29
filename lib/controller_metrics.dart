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
