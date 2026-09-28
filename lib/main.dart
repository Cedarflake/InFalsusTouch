import "package:flutter/material.dart";
import "package:flutter/services.dart";

import "controller_app.dart";
import "native_controller.dart";

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
  runApp(ControllerApp(controller: NativeController()..initialize()));
}
