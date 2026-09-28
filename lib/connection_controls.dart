import "package:flutter/material.dart";

import "app_strings.dart";
import "native_controller.dart";

class ConnectionControls extends StatelessWidget {
  const ConnectionControls({
    super.key,
    required this.controller,
    required this.state,
    required this.strings,
  });
  final NativeController controller;
  final ControllerState state;
  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final busy = state.searching || controller.isConnectionCommandPending;
    final status = state.connected
        ? state.bindingStatus == 2
              ? strings.invalidKeys
              : state.ready
              ? state.settings.controlsMask == 0
                    ? strings.viewOnly
                    : strings.ready
              : strings.focusGame
        : busy
        ? strings.connecting
        : strings.disconnected;
    final hint = state.connected
        ? state.bindingStatus == 0
              ? strings.syncedKeys
              : strings.defaultKeys
        : state.connectionFailed
        ? state.searching
              ? strings.retryHint
              : strings.failedHint
        : busy
        ? strings.connectingHint
        : strings.connectHint;
    return Card(
      key: const ValueKey("connection-panel"),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                SizedBox.square(
                  dimension: 22,
                  child: busy
                      ? const Padding(
                          padding: EdgeInsets.all(2),
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : Icon(
                          Icons.usb_rounded,
                          color: colors.primary,
                          size: 22,
                        ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    strings.usbConnection,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: Text(
                    status,
                    key: const ValueKey("connection-status"),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                ),
                const SizedBox(width: 8),
                SizedBox(
                  key: const ValueKey("connection-action"),
                  width: 152,
                  height: 48,
                  child: FilledButton.tonal(
                    onPressed: controller.isConnectionCommandPending
                        ? null
                        : controller.toggleConnection,
                    child: Text(
                      state.connected
                          ? strings.disconnect
                          : busy
                          ? strings.cancelConnection
                          : strings.connect,
                      textAlign: TextAlign.center,
                      maxLines: 1,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            SizedBox(
              height: 40,
              child: Text(
                hint,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(
                  context,
                ).textTheme.bodySmall?.copyWith(color: colors.onSurfaceVariant),
              ),
            ),
            SizedBox(
              height: 36,
              child: Text(
                state.connected
                    ? "${strings.peers(state.peers)}\nRTT ${state.rtt.toStringAsFixed(1)} ms"
                    : "",
                maxLines: 2,
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  fontFeatures: [const FontFeature.tabularFigures()],
                ),
              ),
            ),
            if (state.detail.isNotEmpty) ...[
              const Divider(height: 28),
              SelectableText(
                state.detail,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: state.connectionFailed
                      ? colors.error
                      : colors.onSurfaceVariant,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
