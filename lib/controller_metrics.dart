import "app_strings.dart";
import "native_controller.dart";

String metricsText(ControllerState state, AppStrings strings) {
  String value(String key, [int digits = 1]) =>
      ((state.video[key] as num?) ?? 0).toStringAsFixed(digits);
  return "${strings.received} ${value("receiveFps")} · ${strings.presented} ${value("presentFps")} fps\n"
      "${strings.queue} ${value("queueDepth", 0)} · ${strings.drops} ${value("droppedFrames", 0)} · RTT ${state.rtt.toStringAsFixed(1)} ms\n"
      "${strings.decode} ${value("decoderMs")} · ${strings.latency} ${value("receiveToPresentMs")} ms";
}
