import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'services/database_service.dart';
import 'app.dart';

Future<void> main() async {
  // Catch all Flutter framework errors before crash
  FlutterError.onError = (details) {
    debugPrint('[FlutterError] ${details.exceptionAsString()}');
  };

  await runZonedGuarded(() async {
    WidgetsFlutterBinding.ensureInitialized();

    await SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);

    // Init database with auto-recovery on corrupt data
    try {
      await DatabaseService().initialize();
    } catch (e) {
      debugPrint('[Main] DB init failed, attempting reset: $e');
      try {
        await DatabaseService().resetAndInitialize();
      } catch (e2) {
        debugPrint('[Main] DB reset also failed, starting fresh: $e2');
      }
    }

    try {
      await InAppWebViewController.setWebContentsDebuggingEnabled(false);
    } catch (_) {}

    runApp(const ChomApp());
  }, (error, stack) {
    debugPrint('[ZoneError] $error\n$stack');
  });
}
