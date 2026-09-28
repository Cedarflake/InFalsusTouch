import "dart:math" as math;

import "package:flutter/material.dart";

import "app_strings.dart";
import "connection_controls.dart";
import "controller_metrics.dart";
import "native_controller.dart";

class SettingsPage extends StatefulWidget {
  const SettingsPage({
    super.key,
    required this.controller,
    required this.state,
    required this.strings,
    required this.entryIsOnRight,
  });
  final NativeController controller;
  final ControllerState state;
  final AppStrings strings;
  final bool entryIsOnRight;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late ControllerSettings draft = widget.state.settings;
  late int category = widget.state.connected ? 1 : 3;
  bool isSaving = false;
  AppStrings get s => widget.strings;

  @override
  void didUpdateWidget(covariant SettingsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.state.settings.text("language") !=
        widget.state.settings.text("language")) {
      draft = draft.withValue(
        "language",
        widget.state.settings.text("language"),
      );
    }
  }

  void change(String key, Object value) =>
      setState(() => draft = draft.withValue(key, value));

  Future<void> save([String panel = "compact"]) async {
    setState(() => isSaving = true);
    final succeeded = await widget.controller.save(draft);
    if (succeeded) await widget.controller.panel(panel);
    if (mounted) setState(() => isSaving = false);
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final message = widget.controller.error ?? widget.state.notice;
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

    final navigationInsets = edgeInsets(64, screen.height - 65);
    final back = IconButton(
      tooltip: s.back,
      onPressed: () => widget.controller.panel("compact"),
      icon: const Icon(Icons.close_rounded),
    );
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
                if (widget.entryIsOnRight) back,
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    s.title,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                const Icon(Icons.translate_rounded, size: 20),
                const SizedBox(width: 12),
                Semantics(
                  label: s.languageLabel,
                  child: SegmentedButton<String>(
                    showSelectedIcon: false,
                    segments: const [
                      ButtonSegment(value: "en", label: Text("English")),
                      ButtonSegment(value: "zh", label: Text("中文")),
                    ],
                    selected: {widget.state.language},
                    onSelectionChanged: (values) =>
                        widget.controller.language(values.single),
                  ),
                ),
                if (!widget.entryIsOnRight) ...[
                  const SizedBox(width: 12),
                  back,
                ],
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
                          icon: const Icon(Icons.crop_landscape_rounded),
                          label: Flexible(
                            child: Text(
                              s.picture,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ),
                        NavigationDrawerDestination(
                          icon: const Icon(Icons.usb_rounded),
                          label: Flexible(
                            child: Text(
                              s.connection,
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
                    padding: EdgeInsets.fromLTRB(
                      12,
                      8,
                      24 + navigationInsets.right,
                      16,
                    ),
                    children: [
                      if (message != null)
                        Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: Material(
                            color: colors.errorContainer,
                            borderRadius: BorderRadius.circular(32),
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      s.message(message),
                                      style: TextStyle(
                                        color: colors.onErrorContainer,
                                      ),
                                    ),
                                  ),
                                  if (widget.state.notice != null)
                                    IconButton(
                                      tooltip: s.close,
                                      onPressed: () => widget.controller
                                          .command("dismissNotice"),
                                      icon: const Icon(Icons.close_rounded),
                                    ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ...switch (category) {
                        0 => controlsSections(),
                        1 => touchSections(),
                        2 => pictureSections(),
                        _ => connectionSections(),
                      },
                    ],
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Padding(
            padding:
                const EdgeInsets.symmetric(horizontal: 20, vertical: 8) +
                edgeInsets(screen.height - 65, screen.height),
            child: Row(
              children: [
                TextButton(
                  onPressed: isSaving
                      ? null
                      : () async {
                          if (await widget.controller.command("defaults") &&
                              mounted) {
                            setState(
                              () => draft = widget.controller.state!.settings,
                            );
                          }
                        },
                  child: Text(s.defaults),
                ),
                const Spacer(),
                TextButton(
                  onPressed: isSaving
                      ? null
                      : () => widget.controller.panel("compact"),
                  child: Text(s.cancel),
                ),
                const SizedBox(width: 12),
                FilledButton(
                  onPressed: isSaving ? null : () => save(),
                  child: Text(s.save, textAlign: TextAlign.center),
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
          onPressed: isSaving ? null : () => save("calibrateJudgment"),
          child: Text(s.align, textAlign: TextAlign.center),
        ),
        const SizedBox(height: 8),
        Text(s.calibrationHint, style: Theme.of(context).textTheme.bodySmall),
      ],
    ),
    _Section(
      title: s.field,
      subtitle: s.fieldHint,
      children: [
        choice("fieldMode", {"ABSOLUTE": s.absolute, "RELATIVE": s.relative}),
        slider(s.fieldHeight, "fieldHeight", 10, 100),
        if (draft.text("layoutMode") != "ALIGNED") ...[
          slider(s.leftEdge, "fieldLeft", 0, 99),
          slider(s.rightEdge, "fieldRight", 1, 100),
          FilledButton.tonal(
            onPressed: isSaving ? null : () => save("calibrateField"),
            child: Text(s.calibrateField, textAlign: TextAlign.center),
          ),
        ],
        toggle(s.guide, "showFieldGuide"),
      ],
    ),
  ];

  List<Widget> controlsSections() => [
    _Section(
      title: s.myControls,
      subtitle: s.controlsHint,
      children: [
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
    _Section(
      title: widget.state.connected
          ? s.peers(widget.state.peers)
          : s.disconnected,
      children: [
        Text(switch (widget.state.bindingStatus) {
          0 when widget.state.connected => s.syncedKeys,
          2 when widget.state.connected => s.invalidKeys,
          _ => s.defaultKeys,
        }),
        Padding(
          padding: const EdgeInsets.only(top: 12),
          child: Text(
            s.inputHint,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ),
        if (widget.state.fieldBusy)
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Text(s.fieldBusy),
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
        if (widget.state.hasVideo) ...[
          const Divider(),
          Text(
            metricsText(widget.state, s),
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 8),
          Text(s.metricsHint, style: Theme.of(context).textTheme.bodySmall),
        ],
      ],
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

  Widget choice(String key, Map<String, String> options) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: SegmentedButton<String>(
      showSelectedIcon: false,
      segments: [
        for (final entry in options.entries)
          ButtonSegment(value: entry.key, label: Text(entry.value)),
      ],
      selected: {draft.text(key)},
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
            onChanged: (next) => change(key, next / divisor),
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
