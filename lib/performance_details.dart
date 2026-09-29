import "package:flutter/material.dart";

import "app_strings.dart";
import "native_controller.dart";

String videoSourceText(ControllerState state) {
  int value(String key) => ((state.video[key] as num?) ?? 0).toInt();
  return "${value("width")} × ${value("height")} · ${value("targetFps")} FPS";
}

class PerformanceDetails extends StatelessWidget {
  const PerformanceDetails({
    super.key,
    required this.state,
    required this.strings,
  });

  final ControllerState state;
  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final secondaryStyle = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
      fontFeatures: const [FontFeature.tabularFigures()],
    );
    String value(String key, [int digits = 1]) =>
        ((state.video[key] as num?) ?? 0).toStringAsFixed(digits);
    final metrics = [
      (label: strings.presented, value: value("presentFps"), unit: "FPS"),
      (label: strings.received, value: value("receiveFps"), unit: "FPS"),
      (
        label: strings.inputRoundTrip,
        value: state.rtt.toStringAsFixed(1),
        unit: "ms",
      ),
      (label: strings.latency, value: value("receiveToPresentMs"), unit: "ms"),
      (label: strings.decode, value: value("decoderMs"), unit: "ms"),
      (label: strings.encode, value: value("captureToEncodeMs"), unit: "ms"),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        LayoutBuilder(
          builder: (context, constraints) {
            final scale = MediaQuery.textScalerOf(context).scale(1);
            final columns = (constraints.maxWidth / (140 * scale))
                .floor()
                .clamp(1, 3);
            final width = (constraints.maxWidth - 16 * (columns - 1)) / columns;
            return Wrap(
              spacing: 16,
              runSpacing: 20,
              children: [
                for (final (index, metric) in metrics.indexed)
                  SizedBox(
                    width: width,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(metric.label, style: secondaryStyle),
                        const SizedBox(height: 4),
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.baseline,
                          textBaseline: TextBaseline.alphabetic,
                          children: [
                            Flexible(
                              child: Text(
                                metric.value,
                                maxLines: 1,
                                style: theme.textTheme.headlineSmall?.copyWith(
                                  color: index == 0
                                      ? theme.colorScheme.primary
                                      : theme.colorScheme.onSurface,
                                  fontWeight: FontWeight.w600,
                                  fontFeatures: const [
                                    FontFeature.tabularFigures(),
                                  ],
                                ),
                              ),
                            ),
                            const SizedBox(width: 4),
                            Text(metric.unit, style: secondaryStyle),
                          ],
                        ),
                      ],
                    ),
                  ),
              ],
            );
          },
        ),
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 16),
          child: Divider(
            height: 1,
            color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5),
          ),
        ),
        Wrap(
          spacing: 24,
          runSpacing: 8,
          children: [
            Text(
              "${strings.queue}  ${value("queueDepth", 0)}",
              style: secondaryStyle,
            ),
            Text(
              "${strings.sessionDrops}  ${value("droppedFrames", 0)}",
              style: secondaryStyle,
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(strings.metricsHint, style: secondaryStyle),
      ],
    );
  }
}
