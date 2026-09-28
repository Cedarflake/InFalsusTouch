import "dart:async";
import "dart:ui" show SemanticsAction;

import "package:flutter/material.dart";
import "package:flutter/services.dart";
import "package:flutter_test/flutter_test.dart";

import "package:infalsus_touch/calibration_page.dart";
import "package:infalsus_touch/controller_app.dart";
import "package:infalsus_touch/native_controller.dart";

Map<String, Object?> settingsFixture() => {
  "language": "en",
  "theme": "dark",
  "controlsMask": 127,
  "layoutMode": "ALIGNED",
  "fieldMode": "RELATIVE",
  "videoScale": "FIT",
  "laneHeight": 0.4,
  "fieldHeight": 0.65,
  "fieldLeft": 0.0,
  "fieldRight": 1.0,
  "laneOpacity": 0.45,
  "laneGapDp": 2.0,
  "brightness": 1.0,
  "showLabels": true,
  "showStatistics": false,
  "showFieldGuide": false,
  "autoConnect": false,
  "autoHideControls": true,
  "highRefreshDisplay": true,
  "judgment": {
    "fieldLeft": 0.07,
    "fieldRight": 0.93,
    "fieldY": 0.635,
    "floorLeft": 0.21,
    "floorRight": 0.79,
    "floorY": 0.89,
    "sideLeft": 0.06,
    "sideRight": 0.94,
    "sideY": 0.725,
    "hitPadding": 0.04,
  },
};

class UiHost {
  UiHost({String panel = "settings", bool connected = true})
    : state = {
        "panel": panel,
        "connection": connected ? "CONNECTED" : "DISCONNECTED",
        "targetReady": connected,
        "hasVideo": true,
        "language": "en",
        "displayHz": 120.0,
        "density": 3.0,
        "rtt": 2.4,
        "settings": settingsFixture(),
        "picture": {"left": 240, "top": 0, "width": 1920, "height": 1080},
      };
  final channel = const MethodChannel("dev.cedarflake.ift/ui");
  final Map<String, Object?> state;
  final calls = <MethodCall>[];
  Completer<void>? connectionCommand;
  Completer<void>? panelCommand;
  Completer<void>? saveCommand;
  bool saveFails = false;
  late final controller = NativeController(channel: channel);

  Future<void> mount(WidgetTester tester) async {
    tester.view.physicalSize = const Size(2400, 1080);
    tester.view.devicePixelRatio = 3.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          if (call.method == "uiRegions" ||
              call.method == "settingsHint" ||
              call.method == "feedback") {
            return null;
          }
          if (call.method == "connect") await connectionCommand?.future;
          if (call.method == "panel") {
            await panelCommand?.future;
            state["panel"] = call.arguments;
          }
          if (call.method == "save") {
            await saveCommand?.future;
            if (saveFails) {
              throw PlatformException(code: "settings_save_failed");
            }
            state["settings"] = {
              ...objectMap(state["settings"]),
              ...objectMap(call.arguments),
            };
            state["language"] = objectMap(state["settings"])["language"];
          }
          if (call.method == "defaults") {
            final previous = objectMap(state["settings"]);
            state["settings"] = {
              ...settingsFixture(),
              "language": previous["language"],
              "theme": previous["theme"],
            };
          }
          return state;
        });
    addTearDown(() {
      controller.dispose();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });
    await controller.initialize();
    await tester.pumpWidget(ControllerApp(controller: controller));
    await tester.pumpAndSettle();
  }
}

void main() {
  testWidgets(
    "settings content fills the display without whole-page safe padding",
    (tester) async {
      final host = UiHost();
      host.state["cutouts"] = [
        [0, 440, 90, 600],
      ];
      await host.mount(tester);
      tester.view.viewPadding = const FakeViewPadding(left: 90);
      addTearDown(tester.view.resetViewPadding);
      await tester.pumpAndSettle();
      expect(
        tester.getRect(find.byKey(const ValueKey("settings-fullscreen"))),
        const Rect.fromLTWH(0, 0, 800, 360),
      );
      final back = tester.getRect(
        find.widgetWithIcon(IconButton, Icons.arrow_back_rounded),
      );
      final defaults = tester.getRect(
        find.byKey(const ValueKey("restore-defaults")),
      );
      expect(back.left, 12);
      expect(back.top, 8);
      expect(defaults.right, 780);
      expect(defaults.center.dy, back.center.dy);
      expect(find.text("Save"), findsNothing);
      expect(find.text("Cancel"), findsNothing);
      expect(find.text("English"), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  test(
    "settings entry ignores unrelated cutout padding and avoids actual cutouts",
    () {
      expect(
        settingsEntryBounds(const Size(800, 360), [
          const Rect.fromLTWH(0, 155, 30, 50),
        ]),
        const Rect.fromLTWH(8, 8, 48, 48),
      );
      expect(
        settingsEntryBounds(const Size(800, 360), [
          const Rect.fromLTWH(0, 0, 40, 70),
        ]),
        const Rect.fromLTWH(744, 8, 48, 48),
      );
    },
  );

  testWidgets("an expired first tap cannot open settings", (tester) async {
    final host = UiHost(panel: "compact");
    await host.mount(tester);
    final entry = find.byKey(const ValueKey("settings-entry"));
    await tester.tap(entry);
    await tester.pump(const Duration(milliseconds: 2200));
    await tester.tap(entry);
    await tester.pump(const Duration(milliseconds: 100));
    expect(host.state["panel"], "compact");
    await tester.tap(entry);
    await tester.pumpAndSettle();
    expect(host.state["panel"], "settings");
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    "settings entry requires two taps and does not show a connection panel over gameplay",
    (tester) async {
      final host = UiHost(panel: "compact", connected: false);
      await host.mount(tester);
      final entry = find.byKey(const ValueKey("settings-entry"));
      final menu = tester.getRect(entry);
      expect(menu.left, menu.top);
      expect(menu, const Rect.fromLTWH(8, 8, 48, 48));
      expect(find.byKey(const ValueKey("connection-panel")), findsNothing);
      await tester.tap(entry);
      await tester.pumpAndSettle();
      expect(host.state["panel"], "compact");
      expect(find.text("Tap again to open settings"), findsNothing);
      expect(find.byType(Tooltip), findsNothing);
      expect(
        host.calls
            .singleWhere((call) => call.method == "settingsHint")
            .arguments,
        "Tap again to open settings",
      );
      await tester.tap(entry);
      await tester.pump();
      expect(find.text("Controller settings"), findsOneWidget);
      expect(find.text("Connect USB"), findsOneWidget);
      final settings = tester.getRect(
        find
            .ancestor(
              of: find.text("Controller settings"),
              matching: find.byType(Material),
            )
            .first,
      );
      expect(settings, const Rect.fromLTWH(0, 0, 800, 360));
      expect(
        objectMap(
          host.calls.lastWhere((call) => call.method == "uiRegions").arguments,
        )["rects"],
        isEmpty,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    "settings entry exposes a labeled tap action and opens only once during a tap burst",
    (tester) async {
      final host = UiHost(panel: "compact");
      await host.mount(tester);
      final entry = find.byKey(const ValueKey("settings-entry"));
      final semantics = tester.ensureSemantics();
      try {
        final data = tester.getSemantics(entry).getSemanticsData();
        expect(data.label, "Settings");
        expect(data.hasAction(SemanticsAction.tap), isTrue);
      } finally {
        semantics.dispose();
      }
      host.panelCommand = Completer<void>();
      for (var tap = 0; tap < 4; tap++) {
        await tester.tap(entry);
        await tester.pump(const Duration(milliseconds: 20));
      }
      expect(
        host.calls.where((call) => call.method == "settingsHint").length,
        1,
      );
      expect(host.calls.where((call) => call.method == "panel").length, 1);
      host.panelCommand!.complete();
      await tester.pumpAndSettle();
      expect(host.state["panel"], "settings");
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    "USB action responds immediately and its geometry stays fixed across connection states",
    (tester) async {
      final host = UiHost(connected: false);
      await host.mount(tester);
      final panel = find.byKey(const ValueKey("connection-panel"));
      final action = find.byKey(const ValueKey("connection-action"));
      final panelBounds = tester.getRect(panel);
      final actionBounds = tester.getRect(action);
      final status = find.byKey(const ValueKey("connection-status"));
      expect(tester.getCenter(status).dy, actionBounds.center.dy);
      expect(find.byType(Card), findsNWidgets(2));
      expect(
        find.descendant(of: panel, matching: find.byType(SwitchListTile)),
        findsNothing,
      );
      expect(
        tester.getCenter(find.text("Connect USB")).dx,
        actionBounds.center.dx,
      );
      host.connectionCommand = Completer<void>();
      await tester.tap(find.text("Connect USB"));
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(
        find.descendant(of: action, matching: find.text("Cancel")),
        findsOneWidget,
      );
      expect(tester.getRect(action), actionBounds);
      expect(tester.getCenter(status).dy, actionBounds.center.dy);
      await tester.tap(
        find.descendant(of: action, matching: find.text("Cancel")),
      );
      await tester.pump();
      expect(host.calls.where((call) => call.method == "connect").length, 1);
      host.state["searching"] = true;
      host.state["connectionFailed"] = true;
      host.connectionCommand!.complete();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(
        find.text("Retrying. Check your cable and the PC Host."),
        findsOneWidget,
      );
      for (final values in [
        {
          "connection": "CONNECTED",
          "searching": false,
          "targetReady": false,
          "peers": 1,
          "rtt": 0.9,
        },
        {
          "connection": "CONNECTED",
          "targetReady": true,
          "peers": 7,
          "rtt": 125.2,
        },
        {"connection": "DISCONNECTED", "targetReady": false, "peers": 0},
      ]) {
        host.state.addAll(values);
        await host.controller.command("state");
        await tester.pump(const Duration(milliseconds: 250));
        expect(tester.getRect(panel).topLeft, panelBounds.topLeft);
        expect(tester.getRect(panel).width, panelBounds.width);
        expect(tester.getRect(action), actionBounds);
        expect(tester.getCenter(status).dy, actionBounds.center.dy);
      }
      host.controller.updateSetting("language", "zh");
      await tester.pumpAndSettle();
      expect(tester.getCenter(find.text("连接 USB")).dx, actionBounds.center.dx);
      host.state["searching"] = true;
      await host.controller.command("state");
      await tester.pump(const Duration(milliseconds: 250));
      expect(find.text("正在连接…"), findsOneWidget);
      expect(tester.getRect(action), actionBounds);
      expect(tester.getCenter(status).dy, actionBounds.center.dy);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    "automatic retries retain the last error and resize only for a new outcome",
    (tester) async {
      final host = UiHost(connected: false);
      const detail =
          "Connection refused: the USB Host could not be reached. "
          "Check that the PC Host is running and USB port forwarding is configured. "
          "Reconnect the cable if the device no longer appears in ADB.";
      host.state.addAll({"detail": detail, "connectionFailed": true});
      await host.mount(tester);
      final panel = find.byKey(const ValueKey("connection-panel"));
      final action = find.byKey(const ValueKey("connection-action"));
      host.state["searching"] = true;
      await host.controller.command("state");
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      final bounds = tester.getRect(panel);
      final actionBounds = tester.getRect(action);
      for (var attempt = 0; attempt < 3; attempt++) {
        for (final connection in ["CONNECTING", "DISCONNECTED"]) {
          host.state.addAll({
            "connection": connection,
            "searching": true,
            "detail": connection == "CONNECTING"
                ? "Connecting over USB…"
                : detail,
          });
          await host.controller.command("state");
          await tester.pump(const Duration(milliseconds: 300));
          expect(find.text(detail), findsOneWidget);
          expect(find.text("Connecting over USB…"), findsNothing);
          expect(tester.getRect(panel), bounds);
          expect(tester.getRect(action), actionBounds);
        }
      }
      host.state.addAll({
        "connection": "CONNECTED",
        "searching": false,
        "connectionFailed": false,
        "detail": "USB connected",
        "bindingStatus": 0,
        "peers": 1,
      });
      await host.controller.command("state");
      await tester.pump();
      final startHeight = tester.getSize(panel).height;
      await tester.pump(const Duration(milliseconds: 90));
      final intermediateHeight = tester.getSize(panel).height;
      await tester.pumpAndSettle();
      final endHeight = tester.getSize(panel).height;
      expect(startHeight, isNot(endHeight));
      expect(intermediateHeight, greaterThan(endHeight));
      expect(intermediateHeight, lessThan(startHeight));
      expect(tester.getRect(action), actionBounds);
      expect(find.text("USB connected"), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    "each phone can hide any controls without overwriting calibration",
    (tester) async {
      final host = UiHost();
      host.state["keyLabels"] = ["Shift", "J", "K", "L", ";", "Space"];
      await host.mount(tester);
      await tester.tap(find.text("Controls"));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text("Field only"),
        150,
        scrollable: find.byType(Scrollable).last,
      );
      await tester.pumpAndSettle();
      expect(find.text("2 · J"), findsOneWidget);
      await tester.tap(find.text("Field only"));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<FilterChip>(find.widgetWithText(FilterChip, "Field"))
            .selected,
        isTrue,
      );
      expect(
        tester
            .widget<FilterChip>(find.widgetWithText(FilterChip, "2 · J"))
            .selected,
        isFalse,
      );
      await tester.ensureVisible(find.text("View only"));
      await tester.pumpAndSettle();
      await tester.tap(find.text("View only"));
      await tester.pumpAndSettle();
      final saved = objectMap(host.state["settings"]);
      expect(saved["controlsMask"], 0);
      expect(saved["judgment"], settingsFixture()["judgment"]);
      expect(host.state["panel"], "settings");
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    "Other changes language immediately and touch settings save automatically",
    (tester) async {
      final host = UiHost();
      await host.mount(tester);
      expect(
        Theme.of(tester.element(find.text("Controller settings"))).useMaterial3,
        isTrue,
      );
      await tester.drag(find.byType(Slider).first, const Offset(50, 0));
      await tester.pumpAndSettle();
      final height = tester.widget<Slider>(find.byType(Slider).first).value;
      await tester.tap(find.text("Other"));
      await tester.pumpAndSettle();
      await tester.tap(find.text("中文"));
      await tester.pumpAndSettle();
      expect(find.text("控制设置"), findsOneWidget);
      expect(find.text("主题"), findsOneWidget);
      await tester.tap(find.text("触控"));
      await tester.pumpAndSettle();
      expect(find.text("按钮触控高度"), findsOneWidget);
      expect(tester.widget<Slider>(find.byType(Slider).first).value, height);
      expect(objectMap(host.state["settings"])["language"], "zh");
      expect(objectMap(host.state["settings"])["laneHeight"], height / 100);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets("touch changes save on release without leaving settings", (
    tester,
  ) async {
    final host = UiHost();
    await host.mount(tester);
    await tester.drag(find.byType(Slider).first, const Offset(50, 0));
    await tester.pumpAndSettle();
    final saved = objectMap(
      host.calls.singleWhere((call) => call.method == "save").arguments,
    );
    expect(saved["laneHeight"], isNot(0.4));
    expect(
      objectMap(host.state["settings"])["judgment"],
      settingsFixture()["judgment"],
    );
    expect(host.state["panel"], "settings");
    expect(tester.takeException(), isNull);
  });

  testWidgets("returning to the game flushes the latest pending setting", (
    tester,
  ) async {
    final host = UiHost();
    await host.mount(tester);
    host.controller.updateSetting("laneHeight", 0.31, defer: true);
    host.controller.updateSetting("laneHeight", 0.32, defer: true);
    expect(host.calls.where((call) => call.method == "save"), isEmpty);
    await tester.tap(find.byTooltip("Back to game"));
    await tester.pumpAndSettle();
    expect(host.calls.where((call) => call.method == "save").length, 1);
    expect(objectMap(host.state["settings"])["laneHeight"], 0.32);
    expect(host.state["panel"], "compact");
  });

  testWidgets(
    "picture and connection settings fit the landscape viewport in both languages",
    (tester) async {
      final host = UiHost();
      await host.mount(tester);
      for (final language in ["en", "zh"]) {
        host.controller.updateSetting("language", language);
        await host.controller.flushSettings();
        await tester.pumpAndSettle();
        await tester.tap(find.text(language == "en" ? "Picture" : "画面"));
        await tester.pumpAndSettle();
        expect(find.text(language == "en" ? "Fit" : "完整显示"), findsOneWidget);
        await tester.tap(
          find.text(language == "en" ? "Connection" : "连接").first,
        );
        await tester.pumpAndSettle();
        expect(
          find.text(
            language == "en" ? "Find USB Host automatically" : "自动寻找 USB Host",
          ),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
      }
    },
  );

  testWidgets(
    "canceling the defaults dialog or pressing Back leaves settings intact",
    (tester) async {
      final host = UiHost();
      await host.mount(tester);
      host.controller.updateSetting("controlsMask", 64);
      await tester.pumpAndSettle();
      for (final language in ["en", "zh"]) {
        host.controller.updateSetting("language", language);
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(const ValueKey("restore-defaults")));
        await tester.pumpAndSettle();
        expect(find.byType(AlertDialog), findsOneWidget);
        expect(
          find.text(language == "en" ? "Restore default settings?" : "恢复默认设置？"),
          findsOneWidget,
        );
        expect(host.calls.where((call) => call.method == "defaults"), isEmpty);
        if (language == "en") {
          await tester.tap(find.text("Cancel"));
        } else {
          await tester.binding.handlePopRoute();
        }
        await tester.pumpAndSettle();
        expect(find.byType(AlertDialog), findsNothing);
        expect(objectMap(host.state["settings"])["controlsMask"], 64);
        expect(host.state["panel"], "settings");
        expect(host.calls.where((call) => call.method == "defaults"), isEmpty);
      }
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    "theme follows the chosen mode and defaults preserve language and appearance",
    (tester) async {
      final host = UiHost();
      tester.binding.platformDispatcher.platformBrightnessTestValue =
          Brightness.light;
      addTearDown(
        tester.binding.platformDispatcher.clearPlatformBrightnessTestValue,
      );
      await host.mount(tester);
      Brightness brightness() => Theme.of(
        tester.element(find.byKey(const ValueKey("settings-fullscreen"))),
      ).brightness;
      expect(brightness(), Brightness.dark);
      await tester.tap(find.text("Other"));
      await tester.pumpAndSettle();
      await tester.tap(find.text("Light"));
      await tester.pumpAndSettle();
      expect(brightness(), Brightness.light);
      expect(objectMap(host.state["settings"])["theme"], "light");
      await tester.tap(find.text("System"));
      await tester.pumpAndSettle();
      expect(brightness(), Brightness.light);
      tester.binding.platformDispatcher.platformBrightnessTestValue =
          Brightness.dark;
      await tester.pumpAndSettle();
      expect(brightness(), Brightness.dark);
      await tester.tap(find.text("Light"));
      await tester.tap(find.text("中文"));
      await tester.pumpAndSettle();
      host.controller.updateSetting("controlsMask", 64);
      await tester.pumpAndSettle();
      host.controller.updateSetting("laneHeight", 0.31, defer: true);
      await tester.tap(find.byKey(const ValueKey("restore-defaults")));
      await tester.pumpAndSettle();
      expect(host.calls.where((call) => call.method == "defaults"), isEmpty);
      await tester.tap(find.byKey(const ValueKey("confirm-defaults")));
      await tester.pumpAndSettle();
      final saved = objectMap(host.state["settings"]);
      expect(saved["controlsMask"], 127);
      expect(saved["laneHeight"], 0.4);
      expect(saved["language"], "zh");
      expect(saved["theme"], "light");
      expect(brightness(), Brightness.light);
      expect(find.text("控制设置"), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    "delayed saves and native status updates cannot overwrite newer edits",
    (tester) async {
      final host = UiHost();
      await host.mount(tester);
      host.saveCommand = Completer<void>();
      host.controller.updateSetting("laneHeight", 0.31);
      await tester.pump();
      host.controller.updateSetting("laneHeight", 0.35, defer: true);
      host.controller.updateSetting("controlsMask", 73);
      await host.controller.command("state");
      await tester.pump();
      expect(host.calls.where((call) => call.method == "save").length, 1);
      expect(tester.widget<Slider>(find.byType(Slider).first).value, 35);
      host.saveCommand!.complete();
      await tester.pumpAndSettle();
      final saved = objectMap(host.state["settings"]);
      expect(saved["laneHeight"], 0.35);
      expect(saved["controlsMask"], 73);
      expect(saved["judgment"], settingsFixture()["judgment"]);
      expect(host.calls.where((call) => call.method == "save").length, 2);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    "a failed automatic save retains edits and retries when backgrounded",
    (tester) async {
      final host = UiHost();
      await host.mount(tester);
      host.saveFails = true;
      host.controller.updateSetting("laneHeight", 0.31);
      await tester.pumpAndSettle();
      expect(host.controller.error, "settings_save_failed");
      expect(
        host.calls.where((call) => call.method == "feedback").single.arguments,
        "settings_save_failed",
      );
      expect(find.textContaining("Settings could not be saved"), findsNothing);
      expect(objectMap(host.state["settings"])["laneHeight"], 0.4);
      await host.controller.command("state");
      await tester.pumpAndSettle();
      expect(host.controller.settings.number("laneHeight"), 0.31);
      host.saveFails = false;
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      await tester.pumpAndSettle();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      expect(objectMap(host.state["settings"])["laneHeight"], 0.31);
      expect(host.calls.where((call) => call.method == "save").length, 2);
      expect(host.controller.error, isNull);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    "judgment calibration ignores side bars and saves source-picture coordinates",
    (tester) async {
      final host = UiHost(panel: "calibrateJudgment");
      await host.mount(tester);
      await tester.tapAt(const Offset(20, 200));
      await tester.pumpAndSettle();
      expect(find.text("1 / 6"), findsOneWidget);
      const positions = [
        Offset(0.07, 0.635),
        Offset(0.93, 0.635),
        Offset(0.21, 0.89),
        Offset(0.79, 0.89),
        Offset(0.06, 0.725),
        Offset(0.94, 0.725),
      ];
      for (final point in positions) {
        await tester.tapAt(Offset(80 + point.dx * 640, point.dy * 360));
        await tester.pumpAndSettle();
      }
      await tester.tap(find.text("Save"));
      await tester.pumpAndSettle();
      final saved = objectMap(
        host.calls.singleWhere((call) => call.method == "save").arguments,
      );
      expect(
        objectMap(saved["judgment"])["fieldLeft"],
        closeTo(0.07, 0.000001),
      );
      expect(saved["layoutMode"], "ALIGNED");
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    "invalid calibration requests native feedback without an error banner",
    (tester) async {
      final host = UiHost(panel: "calibrateField");
      await host.mount(tester);
      await tester.tapAt(const Offset(600, 170));
      await tester.tapAt(const Offset(200, 170));
      await tester.pumpAndSettle();
      expect(
        host.calls.where((call) => call.method == "feedback").single.arguments,
        "invalid_calibration",
      );
      expect(find.textContaining("Edges are reversed"), findsNothing);
      expect(
        tester
            .widget<FilledButton>(find.widgetWithText(FilledButton, "Save"))
            .onPressed,
        isNull,
      );
      await tester.tap(find.byTooltip("Undo point"));
      await tester.pumpAndSettle();
      expect(find.text("2 / 2"), findsOneWidget);
      expect(host.calls.where((call) => call.method == "feedback").length, 1);
    },
  );

  test("calibration rejects reversed endpoints and an overlapping Field", () {
    const points = [
      Offset(0.08, 0.635),
      Offset(0.92, 0.635),
      Offset(0.21, 0.89),
      Offset(0.79, 0.89),
      Offset(0.06, 0.725),
      Offset(0.94, 0.725),
    ];
    final original = objectMap(settingsFixture()["judgment"]);
    expect(calibratedJudgment(points, original)?["fieldLeft"], 0.08);
    expect(
      calibratedJudgment([points[1], points[0], ...points.skip(2)], original),
      isNull,
    );
    expect(
      calibratedJudgment([
        const Offset(0.08, 0.8),
        const Offset(0.92, 0.8),
        ...points.skip(2),
      ], original),
      isNull,
    );
  });
}
