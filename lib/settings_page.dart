import "dart:math" as math;

import "package:flutter/material.dart";

import "app_strings.dart";
import "connection_controls.dart";
import "native_controller.dart";
import "performance_details.dart";

class SettingsPage extends StatefulWidget {
  const SettingsPage({
    super.key,
    required this.controller,
    required this.state,
    required this.strings,
  });
  final NativeController controller;
  final ControllerState state;
  final AppStrings strings;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  ControllerSettings get draft => widget.controller.settings;
  late int category = widget.state.connected ? 1 : 0;
  bool isConfirmingDefaults = false;
  AppStrings get s => widget.strings;

  void change(String key, Object value, {bool defer = false}) {
    if (key == "fieldLeft" && value is double) {
      value = value.clamp(
        0.0,
        math.max(0.0, draft.number("fieldRight") - 0.01),
      );
    } else if (key == "fieldRight" && value is double) {
      value = value.clamp(math.min(1.0, draft.number("fieldLeft") + 0.01), 1.0);
    }
    widget.controller.updateSetting(key, value, defer: defer);
  }

  Future<void> confirmDefaults() async {
    if (isConfirmingDefaults || widget.controller.isResettingDefaults) return;
    setState(() => isConfirmingDefaults = true);
    try {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: Text(s.confirmDefaultsTitle),
          content: Text(s.confirmDefaultsBody),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: Text(s.cancel),
            ),
            FilledButton(
              key: const ValueKey("confirm-defaults"),
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: Text(s.confirmDefaults),
            ),
          ],
        ),
      );
      if (confirmed == true && mounted) {
        await widget.controller.restoreDefaults();
      }
    } finally {
      if (mounted) setState(() => isConfirmingDefaults = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final screen = MediaQuery.sizeOf(context);
    EdgeInsets edgeInsets(double top, double bottom) {
      double left = 0;
      double right = 0;
      for (final cutout in widget.state.cutouts) {
        if (cutout.bottom <= top || cutout.top >= bottom) continue;
        if (cutout.left <= 0) left = math.max(left, cutout.right);
        if (cutout.right >= screen.width) {
          right = math.max(right, screen.width - cutout.left);
        }
      }
      return EdgeInsets.only(left: left, right: right);
    }

    final navigationInsets = edgeInsets(64, screen.height);
    return Material(
      color: colors.surface,
      child: Column(
        key: const ValueKey("settings-fullscreen"),
        children: [
          Padding(
            padding:
                const EdgeInsets.fromLTRB(12, 8, 20, 8) + edgeInsets(0, 64),
            child: Row(
              children: [
                IconButton(
                  tooltip: s.back,
                  onPressed: () => widget.controller.panel("compact"),
                  icon: const Icon(Icons.arrow_back_rounded),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    s.title,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                TextButton(
                  key: const ValueKey("restore-defaults"),
                  onPressed:
                      isConfirmingDefaults ||
                          widget.controller.isResettingDefaults
                      ? null
                      : confirmDefaults,
                  child: Text(s.defaults),
                ),
              ],
            ),
          ),
          Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SizedBox(
                  width: 180 + navigationInsets.left,
                  child: Padding(
                    padding: EdgeInsets.only(left: navigationInsets.left),
                    child: NavigationDrawer(
                      backgroundColor: colors.surface,
                      selectedIndex: category,
                      onDestinationSelected: (value) =>
                          setState(() => category = value),
                      children: [
                        NavigationDrawerDestination(
                          icon: const Icon(Icons.usb_rounded),
                          label: Flexible(
                            child: Text(
                              s.connection,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ),
                        NavigationDrawerDestination(
                          icon: const Icon(Icons.touch_app_outlined),
                          selectedIcon: const Icon(Icons.touch_app_rounded),
                          label: Flexible(
                            child: Text(
                              s.touch,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ),
                        NavigationDrawerDestination(
                          icon: const Icon(Icons.visibility_outlined),
                          selectedIcon: const Icon(Icons.visibility_rounded),
                          label: Flexible(
                            child: Text(
                              s.controls,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ),
                        NavigationDrawerDestination(
                          icon: const Icon(Icons.crop_landscape_rounded),
                          label: Flexible(
                            child: Text(
                              s.picture,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ),
                        NavigationDrawerDestination(
                          icon: const Icon(Icons.more_horiz_rounded),
                          label: Flexible(
                            child: Text(
                              s.other,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                Expanded(
                  child: ListView(
                    key: ValueKey(category),
                    padding: EdgeInsets.fromLTRB(
                      12,
                      8,
                      24 + navigationInsets.right,
                      16,
                    ),
                    children: [
                      ...switch (category) {
                        0 => connectionSections(),
                        1 => touchSections(),
                        2 => controlsSections(),
                        3 => pictureSections(),
                        _ => otherSections(),
                      },
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> touchSections() => [
    _Section(
      title: s.feel,
      subtitle: s.feelHint,
      children: [
        slider(s.buttonHeight, "laneHeight", 10, 50),
        slider(s.opacity, "laneOpacity", 0, 100),
        slider(s.brightness, "brightness", 10, 100),
        slider(s.gap, "laneGapDp", 0, 20, divisor: 1, unit: "dp"),
        toggle(s.labels, "showLabels"),
        toggle(s.buttonHaptics, "buttonHaptics", subtitle: s.buttonHapticsHint),
      ],
    ),
    _Section(
      title: s.layout,
      subtitle: s.alignedHint,
      children: [
        choice("layoutMode", {
          "ALIGNED": s.aligned,
          "OVERLAY": s.overlay,
          "RESERVED": s.reserved,
        }),
        const SizedBox(height: 12),
        FilledButton.tonal(
          onPressed: () => widget.controller.panel("calibrateJudgment"),
          child: Text(s.align, textAlign: TextAlign.center),
        ),
        const SizedBox(height: 8),
        Text(s.calibrationHint, style: Theme.of(context).textTheme.bodySmall),
      ],
    ),
    _Section(
      title: s.field,
      subtitle: draft.text("fieldMode") == "RELATIVE"
          ? s.fieldHint
          : s.absoluteFieldHint,
      children: [
        choice("fieldMode", {"RELATIVE": s.relative, "ABSOLUTE": s.absolute}),
        slider(s.fieldHeight, "fieldHeight", 10, 100),
        if (draft.text("layoutMode") != "ALIGNED") ...[
          slider(s.leftEdge, "fieldLeft", 0, 99),
          slider(s.rightEdge, "fieldRight", 1, 100),
          FilledButton.tonal(
            onPressed: () => widget.controller.panel("calibrateField"),
            child: Text(s.calibrateField, textAlign: TextAlign.center),
          ),
        ],
        toggle(s.guide, "showFieldGuide"),
      ],
    ),
  ];

  List<Widget> controlsSections() => [
    _Section(
      title: s.cooperativePlay,
      children: [
        const SizedBox(height: 8),
        Text(s.cooperativePlayHint),
        const SizedBox(height: 12),
        Text(s.inputHint, style: Theme.of(context).textTheme.bodySmall),
      ],
    ),
    _Section(
      title: s.myControls,
      children: [
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (var index = 0; index < 7; index++)
              FilterChip(
                label: Text(
                  index == 6
                      ? "Field"
                      : "${index + 1} · ${widget.state.keyLabels[index]}",
                ),
                selected: draft.controlsMask & (1 << index) != 0,
                onSelected: (selected) => change(
                  "controlsMask",
                  selected
                      ? draft.controlsMask | (1 << index)
                      : draft.controlsMask & ~(1 << index),
                ),
              ),
          ],
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final preset in {
              127: s.allControls,
              63: s.keysOnly,
              64: s.fieldOnly,
              0: s.viewOnly,
            }.entries)
              TextButton(
                onPressed: () => change("controlsMask", preset.key),
                child: Text(preset.value),
              ),
          ],
        ),
      ],
    ),
  ];

  List<Widget> pictureSections() => [
    _Section(
      title: s.framing,
      subtitle: s.framingHint,
      children: [
        choice("videoScale", {"FIT": s.fit, "FILL": s.stretch, "CROP": s.crop}),
      ],
    ),
    _Section(
      title: "${s.display} · ${widget.state.displayHz.round()} Hz",
      children: [
        toggle(
          s.highRefresh,
          "highRefreshDisplay",
          subtitle: s.highRefreshHint,
        ),
        toggle(s.statistics, "showStatistics"),
      ],
    ),
    if (widget.state.hasVideo)
      _Section(
        title: s.performanceDetails,
        subtitle: "${s.videoSource} · ${videoSourceText(widget.state)}",
        children: [PerformanceDetails(state: widget.state, strings: s)],
      ),
  ];

  List<Widget> connectionSections() => [
    Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: ConnectionControls(
        controller: widget.controller,
        state: widget.state,
        strings: s,
      ),
    ),
    _Section(
      title: s.connection,
      subtitle: s.usbHint,
      children: [
        toggle(s.autoConnect, "autoConnect", subtitle: s.autoConnectHint),
      ],
    ),
  ];

  List<Widget> otherSections() => [
    _Section(
      title: s.languageLabel,
      children: [
        choice("language", {
          "en": "English",
          "zh": "中文",
        }, selected: widget.controller.language),
      ],
    ),
    _Section(
      title: s.theme,
      children: [
        choice("theme", {
          "system": s.systemTheme,
          "light": s.lightTheme,
          "dark": s.darkTheme,
        }),
      ],
    ),
  ];

  Widget choice(String key, Map<String, String> options, {String? selected}) =>
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: SegmentedButton<String>(
          showSelectedIcon: false,
          segments: [
            for (final entry in options.entries)
              ButtonSegment(value: entry.key, label: Text(entry.value)),
          ],
          selected: {selected ?? draft.text(key)},
          onSelectionChanged: (values) => change(key, values.single),
        ),
      );

  Widget slider(
    String title,
    String key,
    int minimum,
    int maximum, {
    double divisor = 100,
    String unit = "%",
  }) {
    final value = (draft.number(key) * divisor).clamp(
      minimum.toDouble(),
      maximum.toDouble(),
    );
    return Padding(
      padding: const EdgeInsets.only(top: 16),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ),
              Text(
                "${value.round()} $unit",
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: Theme.of(context).colorScheme.primary,
                ),
              ),
            ],
          ),
          Slider(
            value: value,
            min: minimum.toDouble(),
            max: maximum.toDouble(),
            divisions: maximum - minimum,
            label: "${value.round()} $unit",
            semanticFormatterCallback: (value) =>
                "$title ${value.round()} $unit",
            onChanged: (next) => change(key, next / divisor, defer: true),
            onChangeEnd: (_) => widget.controller.flushSettings(),
          ),
        ],
      ),
    );
  }

  Widget toggle(String title, String key, {String? subtitle}) => SwitchListTile(
    contentPadding: EdgeInsets.zero,
    title: Text(title),
    subtitle: subtitle == null ? null : Text(subtitle),
    value: draft.flag(key),
    onChanged: (value) => change(key, value),
  );
}

class _Section extends StatelessWidget {
  const _Section({required this.title, this.subtitle, required this.children});
  final String title;
  final String? subtitle;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
    child: Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            if (subtitle case final String text)
              Padding(
                padding: const EdgeInsets.only(top: 6, bottom: 12),
                child: Text(
                  text,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            ...children,
          ],
        ),
      ),
    ),
  );
}
