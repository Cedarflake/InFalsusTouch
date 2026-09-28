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

  String statusLabel(bool busy) {
    if (!state.connected) {
      return busy ? strings.connecting : strings.disconnected;
    }
    if (state.bindingStatus == 2) return strings.invalidKeys;
    if (!state.ready) return strings.focusGame;
    return state.settings.controlsMask == 0 ? strings.viewOnly : strings.ready;
  }

  String? connectionHint(bool busy) {
    if (state.connected) {
      return state.bindingStatus == 1 ? strings.defaultKeys : null;
    }
    if (state.connectionFailed) {
      return busy ? strings.retryHint : strings.failedHint;
    }
    return busy ? strings.connectingHint : strings.connectHint;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final busy = state.searching || controller.isConnectionCommandPending;
    final hint = connectionHint(busy);
    final hasError =
        state.connectionFailed || (state.connected && state.bindingStatus == 2);
    final statusColor = hasError && !busy
        ? colors.error
        : state.connected || busy
        ? colors.primary
        : colors.outline;
    return Card(
      key: const ValueKey("connection-panel"),
      child: AnimatedSize(
        alignment: Alignment.topCenter,
        duration: MediaQuery.disableAnimationsOf(context)
            ? Duration.zero
            : const Duration(milliseconds: 180),
        curve: Curves.easeOutCubic,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                height: 32,
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        strings.usbConnection,
                        style: theme.textTheme.titleSmall?.copyWith(
                          color: colors.onSurfaceVariant,
                        ),
                      ),
                    ),
                    if (state.connected)
                      DecoratedBox(
                        decoration: ShapeDecoration(
                          color: colors.surfaceContainerHighest,
                          shape: const StadiumBorder(),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 6,
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.devices_rounded,
                                size: 16,
                                color: colors.onSurfaceVariant,
                              ),
                              const SizedBox(width: 6),
                              Text(
                                strings.peers(state.peers),
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: colors.onSurfaceVariant,
                                  fontFeatures: [
                                    const FontFeature.tabularFigures(),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  SizedBox.square(
                    dimension: 16,
                    child: busy
                        ? CircularProgressIndicator(
                            strokeWidth: 2,
                            color: statusColor,
                          )
                        : Center(
                            child: Container(
                              width: 8,
                              height: 8,
                              decoration: BoxDecoration(
                                color: statusColor,
                                shape: BoxShape.circle,
                              ),
                            ),
                          ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      statusLabel(busy),
                      key: const ValueKey("connection-status"),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),
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
              if (hint != null) ...[
                const SizedBox(height: 8),
                Text(
                  hint,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: colors.onSurfaceVariant,
                  ),
                ),
              ],
              if (state.detail.isNotEmpty) ...[
                const SizedBox(height: 16),
                _ConnectionLog(
                  detail: state.detail,
                  isError: state.connectionFailed,
                  strings: strings,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _ConnectionLog extends StatelessWidget {
  const _ConnectionLog({
    required this.detail,
    required this.isError,
    required this.strings,
  });
  final String detail;
  final bool isError;
  final AppStrings strings;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(32),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  isError
                      ? Icons.error_outline_rounded
                      : Icons.receipt_long_rounded,
                  size: 16,
                  color: isError ? colors.error : colors.onSurfaceVariant,
                ),
                const SizedBox(width: 8),
                Text(
                  strings.connectionLog,
                  style: theme.textTheme.labelMedium?.copyWith(
                    color: colors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            SelectableText(
              detail,
              style: theme.textTheme.bodySmall?.copyWith(
                color: colors.onSurfaceVariant,
                fontFamily: "monospace",
                height: 1.4,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
