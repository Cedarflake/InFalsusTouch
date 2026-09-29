import "dart:async";

import "package:flutter/material.dart";
import "package:flutter_localizations/flutter_localizations.dart";

import "app_strings.dart";
import "calibration_page.dart";
import "controller_metrics.dart";
import "native_controller.dart";
import "settings_page.dart";

const _corners = StadiumBorder();

Rect settingsEntryBounds(Size size, List<Rect> cutouts) {
  final candidates = [
    const Rect.fromLTWH(8, 8, 48, 48),
    Rect.fromLTWH(size.width - 56, 8, 48, 48),
    Rect.fromLTWH(8, size.height - 56, 48, 48),
    Rect.fromLTWH(size.width - 56, size.height - 56, 48, 48),
  ];
  return candidates.firstWhere(
    (candidate) =>
        !cutouts.any((cutout) => candidate.overlaps(cutout.inflate(4))),
    orElse: () => candidates.first,
  );
}

class ControllerApp extends StatelessWidget {
  const ControllerApp({super.key, required this.controller});
  final NativeController controller;

  static const _buttonStyle = ButtonStyle(
    shape: WidgetStatePropertyAll(_corners),
  );
  static final _lightTheme = _createTheme(Brightness.light);
  static final _darkTheme = _createTheme(Brightness.dark);

  static ThemeData _createTheme(Brightness brightness) {
    final scheme = ColorScheme.fromSeed(
      seedColor: const Color(0xFF67D9C5),
      brightness: brightness,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: scheme.surface,
      cardTheme: CardThemeData(
        elevation: 0,
        color: scheme.surfaceContainerLow,
        margin: EdgeInsets.zero,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(32)),
        ),
      ),
      dialogTheme: const DialogThemeData(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(32)),
        ),
      ),
      filledButtonTheme: const FilledButtonThemeData(style: _buttonStyle),
      outlinedButtonTheme: const OutlinedButtonThemeData(style: _buttonStyle),
      textButtonTheme: const TextButtonThemeData(style: _buttonStyle),
      iconButtonTheme: const IconButtonThemeData(style: _buttonStyle),
      segmentedButtonTheme: const SegmentedButtonThemeData(style: _buttonStyle),
      chipTheme: const ChipThemeData(shape: _corners),
      navigationDrawerTheme: const NavigationDrawerThemeData(
        indicatorShape: _corners,
      ),
      tooltipTheme: const TooltipThemeData(
        waitDuration: Duration(milliseconds: 500),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      final state = controller.state;
      final strings = AppStrings(controller.language);
      return MaterialApp(
        title: "InFalsusTouch",
        debugShowCheckedModeBanner: false,
        locale: Locale(strings.language),
        supportedLocales: const [Locale("en"), Locale("zh")],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        theme: _lightTheme,
        darkTheme: _darkTheme,
        themeMode: switch (controller.settings.text("theme", "dark")) {
          "light" => ThemeMode.light,
          "system" => ThemeMode.system,
          _ => ThemeMode.dark,
        },
        home: state == null
            ? const SizedBox.expand()
            : _ControllerShell(
                controller: controller,
                state: state,
                strings: strings,
              ),
      );
    },
  );
}

class _ControllerShell extends StatefulWidget {
  const _ControllerShell({
    required this.controller,
    required this.state,
    required this.strings,
  });
  final NativeController controller;
  final ControllerState state;
  final AppStrings strings;

  @override
  State<_ControllerShell> createState() => _ControllerShellState();
}

class _ControllerShellState extends State<_ControllerShell> {
  final _dock = GlobalKey();
  String? _reportedPanel;
  List<Rect> _reportedRects = [];

  void reportRegions() {
    if (!mounted) return;
    final rects = <Rect>[];
    for (final key in [_dock]) {
      final box = key.currentContext?.findRenderObject();
      if (box is RenderBox && box.hasSize) {
        rects.add(box.localToGlobal(Offset.zero) & box.size);
      }
    }
    if (_reportedPanel == widget.state.panel &&
        rects.length == _reportedRects.length &&
        List.generate(
          rects.length,
          (i) => rects[i] == _reportedRects[i],
        ).every((same) => same)) {
      return;
    }
    _reportedPanel = widget.state.panel;
    _reportedRects = rects;
    widget.controller.uiRegions(widget.state.panel, rects);
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final strings = widget.strings;
    WidgetsBinding.instance.addPostFrameCallback((_) => reportRegions());
    return PopScope(
      canPop: state.panel == "compact",
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) {
          widget.controller.panel("compact");
        }
      },
      child: switch (state.panel) {
        "calibrateField" || "calibrateJudgment" => CalibrationPage(
          key: ValueKey(state.panel),
          controller: widget.controller,
          state: state,
          strings: strings,
        ),
        "settings" => SettingsPage(
          controller: widget.controller,
          state: state,
          strings: strings,
        ),
        _ => LayoutBuilder(
          builder: (context, constraints) {
            final safe = MediaQuery.viewPaddingOf(context);
            final entry = settingsEntryBounds(
              constraints.biggest,
              state.cutouts,
            );
            return Material(
              color: Colors.transparent,
              child: Stack(
                children: [
                  Positioned(
                    left: entry.left,
                    top: entry.top,
                    width: 48,
                    child: RepaintBoundary(
                      key: _dock,
                      child: _SettingsButton(
                        controller: widget.controller,
                        state: state,
                        strings: strings,
                      ),
                    ),
                  ),
                  if (state.settings.flag("showStatistics"))
                    Positioned(
                      left:
                          entry.top == 8 &&
                              entry.center.dx < constraints.maxWidth / 2
                          ? entry.right + 8
                          : safe.left + 8,
                      right:
                          entry.top == 8 &&
                              entry.center.dx > constraints.maxWidth / 2
                          ? constraints.maxWidth - entry.left + 8
                          : safe.right + 8,
                      top: safe.top + 8,
                      child: IgnorePointer(
                        child: Align(
                          alignment: Alignment.topRight,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: Colors.black.withValues(alpha: 0.3),
                              borderRadius: BorderRadius.circular(999),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 3,
                              ),
                              child: Text(
                                compactMetricsText(state),
                                maxLines: 1,
                                softWrap: false,
                                overflow: TextOverflow.fade,
                                textAlign: TextAlign.right,
                                style: Theme.of(context).textTheme.labelSmall
                                    ?.copyWith(
                                      color: Colors.white.withValues(
                                        alpha: 0.85,
                                      ),
                                      fontFeatures: [
                                        const FontFeature.tabularFigures(),
                                      ],
                                    ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            );
          },
        ),
      },
    );
  }
}

class _SettingsButton extends StatefulWidget {
  const _SettingsButton({
    required this.controller,
    required this.state,
    required this.strings,
  });
  final NativeController controller;
  final ControllerState state;
  final AppStrings strings;

  @override
  State<_SettingsButton> createState() => _SettingsButtonState();
}

class _SettingsButtonState extends State<_SettingsButton>
    with WidgetsBindingObserver {
  Timer? deadline;
  bool isArmed = false;
  bool isOpening = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  void reset() {
    deadline?.cancel();
    if (mounted) setState(() => isArmed = false);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) reset();
  }

  Future<void> handleTap() async {
    if (isOpening) return;
    if (isArmed) {
      reset();
      isOpening = true;
      final opened = await widget.controller.panel("settings");
      if (mounted && !opened) isOpening = false;
      return;
    }
    setState(() => isArmed = true);
    unawaited(widget.controller.settingsHint(widget.strings.settingsAgain));
    deadline = Timer(const Duration(seconds: 2), reset);
  }

  @override
  void dispose() {
    deadline?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: 48,
    child: IconButton.filledTonal(
      key: const ValueKey("settings-entry"),
      onPressed: handleTap,
      isSelected: isArmed,
      icon: Badge(
        backgroundColor: widget.state.ready
            ? Theme.of(context).colorScheme.primary
            : Theme.of(context).colorScheme.outline,
        smallSize: 6,
        child: Icon(Icons.tune_rounded, semanticLabel: widget.strings.settings),
      ),
    ),
  );
}
