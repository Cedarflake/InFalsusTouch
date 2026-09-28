import "package:flutter/material.dart";

import "app_strings.dart";
import "native_controller.dart";

Map<String, Object?>? calibratedJudgment(
  List<Offset> points,
  Map<String, Object?> original,
) {
  if (points.length != 6) return null;
  final fieldY = (points[0].dy + points[1].dy) / 2;
  final floorY = (points[2].dy + points[3].dy) / 2;
  final sideY = (points[4].dy + points[5].dy) / 2;
  final padding = (original["hitPadding"] as num?)?.toDouble() ?? 0.04;
  if (points.any(
        (point) =>
            !point.dx.isFinite ||
            !point.dy.isFinite ||
            point.dx < 0 ||
            point.dx > 1 ||
            point.dy < 0 ||
            point.dy > 1,
      ) ||
      points[1].dx - points[0].dx < 0.1 ||
      points[3].dx - points[2].dx < 0.1 ||
      points[4].dx >= points[2].dx ||
      points[5].dx <= points[3].dx ||
      fieldY + padding >= sideY - padding ||
      sideY >= floorY ||
      floorY >= 1) {
    return null;
  }
  return {
    ...original,
    "fieldLeft": points[0].dx,
    "fieldRight": points[1].dx,
    "fieldY": fieldY,
    "floorLeft": points[2].dx,
    "floorRight": points[3].dx,
    "floorY": floorY,
    "sideLeft": points[4].dx,
    "sideRight": points[5].dx,
    "sideY": sideY,
  };
}

class CalibrationPage extends StatefulWidget {
  const CalibrationPage({
    super.key,
    required this.controller,
    required this.state,
    required this.strings,
  });
  final NativeController controller;
  final ControllerState state;
  final AppStrings strings;

  @override
  State<CalibrationPage> createState() => _CalibrationPageState();
}

class _CalibrationPageState extends State<CalibrationPage> {
  final points = <Offset>[];
  bool isSaving = false;
  bool get isJudgment => widget.state.panel == "calibrateJudgment";
  int get total => isJudgment ? 6 : 2;

  ControllerSettings? get result {
    final original = widget.state.settings;
    if (isJudgment) {
      final judgment = calibratedJudgment(points, original.judgment);
      return judgment == null
          ? null
          : original
                .withValue("judgment", judgment)
                .withValue("layoutMode", "ALIGNED");
    }
    if (points.length != 2 || points[1].dx - points[0].dx < 0.05) return null;
    return original
        .withValue("fieldLeft", points[0].dx)
        .withValue("fieldRight", points[1].dx);
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.strings;
    final prompts = isJudgment ? s.judgmentSteps : s.fieldSteps;
    final done = points.length == total;
    final calibrated = result;
    return LayoutBuilder(
      builder: (context, constraints) {
        final safe = MediaQuery.viewPaddingOf(context);
        final picture = isJudgment
            ? widget.state.picture
            : Offset.zero & constraints.biggest;
        return Material(
          color: Colors.black.withValues(alpha: 0.20),
          child: Stack(
            children: [
              Positioned.fill(
                child: GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTapUp: (event) {
                    if (points.length >= total ||
                        picture == null ||
                        !picture.contains(event.localPosition)) {
                      return;
                    }
                    if (!isJudgment) {
                      final bottom =
                          constraints.maxHeight *
                          (1 - widget.state.settings.number("laneHeight"));
                      final top =
                          (bottom -
                                  constraints.maxHeight *
                                      widget.state.settings.number(
                                        "fieldHeight",
                                      ))
                              .clamp(0.0, bottom);
                      if (event.localPosition.dy < top ||
                          event.localPosition.dy >= bottom) {
                        return;
                      }
                    }
                    setState(
                      () => points.add(
                        Offset(
                          (event.localPosition.dx - picture.left) /
                              picture.width,
                          (event.localPosition.dy - picture.top) /
                              picture.height,
                        ),
                      ),
                    );
                  },
                  child: CustomPaint(
                    painter: _CalibrationPainter(
                      List.of(points),
                      picture,
                      isJudgment,
                      Theme.of(context).colorScheme.primary,
                    ),
                  ),
                ),
              ),
              Positioned(
                top: safe.top + 12,
                left: safe.left + 16,
                right: safe.right + 16,
                child: Card(
                  color: Theme.of(context).colorScheme.surfaceContainerHigh,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    child: Row(
                      children: [
                        IconButton(
                          tooltip: s.cancel,
                          onPressed: () => widget.controller.panel("settings"),
                          icon: const Icon(Icons.close_rounded),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                done
                                    ? (calibrated == null
                                          ? s.invalidCalibration
                                          : s.calibrated)
                                    : prompts[points.length],
                                style: Theme.of(context).textTheme.titleSmall,
                              ),
                              if (!done)
                                Text(
                                  "${points.length + 1} / $total",
                                  style: Theme.of(context).textTheme.labelSmall,
                                ),
                            ],
                          ),
                        ),
                        IconButton(
                          tooltip: s.undo,
                          onPressed: points.isEmpty
                              ? null
                              : () => setState(() => points.removeLast()),
                          icon: const Icon(Icons.undo_rounded),
                        ),
                        const SizedBox(width: 8),
                        FilledButton(
                          onPressed: calibrated == null || isSaving
                              ? null
                              : () async {
                                  setState(() => isSaving = true);
                                  if (await widget.controller.save(
                                    calibrated,
                                  )) {
                                    await widget.controller.panel("compact");
                                  }
                                  if (mounted) setState(() => isSaving = false);
                                },
                          child: Text(s.save),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _CalibrationPainter extends CustomPainter {
  _CalibrationPainter(this.points, this.picture, this.isJudgment, this.color);
  final List<Offset> points;
  final Rect? picture;
  final bool isJudgment;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = picture;
    if (rect == null) return;
    final paint = Paint()
      ..color = color
      ..strokeWidth = 2;
    Offset at(int index) => Offset(
      rect.left + points[index].dx * rect.width,
      rect.top + points[index].dy * rect.height,
    );
    for (var index = 0; index < points.length; index++) {
      canvas.drawCircle(at(index), 5, paint);
    }
    if (points.length >= 2) canvas.drawLine(at(0), at(1), paint);
    if (isJudgment) {
      if (points.length >= 4) canvas.drawLine(at(2), at(3), paint);
      if (points.length >= 5) canvas.drawLine(at(4), at(2), paint);
      if (points.length >= 6) canvas.drawLine(at(3), at(5), paint);
    }
  }

  @override
  bool shouldRepaint(_CalibrationPainter oldDelegate) =>
      oldDelegate.points != points ||
      oldDelegate.picture != picture ||
      oldDelegate.color != color;
}
