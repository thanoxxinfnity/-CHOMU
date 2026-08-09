import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';
import '../providers/companion_provider.dart';
import '../core/theme.dart';

class CompanionViewer extends StatefulWidget {
  const CompanionViewer({super.key});

  @override
  State<CompanionViewer> createState() => _CompanionViewerState();
}

class _CompanionViewerState extends State<CompanionViewer> {
  InAppWebViewController? _webViewController;
  bool _viewerReady = false;
  bool _modelLoaded = false;

  final InAppWebViewSettings _webViewSettings = InAppWebViewSettings(
    javaScriptEnabled: true,
    allowFileAccess: true,
    allowUniversalAccessFromFileURLs: true,
    allowFileAccessFromFileURLs: true,
    mediaPlaybackRequiresUserGesture: false,
    transparentBackground: true,
    supportZoom: false,
    disableHorizontalScroll: true,
    disableVerticalScroll: true,
    hardwareAcceleration: true,
    useWideViewPort: false,
    mixedContentMode: MixedContentMode.MIXED_CONTENT_ALWAYS_ALLOW,
  );

  @override
  Widget build(BuildContext context) {
    final companion = context.watch<CompanionProvider>();

    return Stack(
      children: [
        // WebView 3D Canvas
        InAppWebView(
          initialFile: 'assets/html/viewer.html',
          initialSettings: _webViewSettings,
          onWebViewCreated: _onWebViewCreated,
          onLoadStop: _onLoadStop,
          onConsoleMessage: (c, msg) =>
              debugPrint('[WebView] ${msg.message}'),
        ),

        // State overlay indicators
        if (companion.state == CompanionState.thinking)
          _ThinkingOverlay(),
        if (companion.state == CompanionState.listening)
          _ListeningOverlay(),
        if (!_modelLoaded)
          _NoModelHint(),
      ],
    );
  }

  void _onWebViewCreated(InAppWebViewController controller) {
    _webViewController = controller;

    // Register JS handlers
    controller.addJavaScriptHandler(
      handlerName: 'onViewerReady',
      callback: (_) {
        _viewerReady = true;
        _loadInitialModel();
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'onModelLoaded',
      callback: (args) {
        setState(() => _modelLoaded = true);
        context.read<CompanionProvider>().setModelLoaded(true);
        debugPrint('[3D] Model loaded: $args');
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'onModelError',
      callback: (args) {
        debugPrint('[3D] Model error: $args');
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'onHeadTarget',
      callback: (args) {
        if (args.isNotEmpty && args[0] is Map) {
          final map = args[0] as Map;
          final x = (map['x'] as num?)?.toDouble() ?? 0.0;
          final y = (map['y'] as num?)?.toDouble() ?? 0.0;
          context.read<CompanionProvider>().updateHeadTarget(x, y);
        }
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'onStatus',
      callback: (args) => debugPrint('[3D Status] ${args.join(', ')}'),
    );

    // Wire up companion provider callbacks
    _wireCompanionCallbacks();
  }

  void _onLoadStop(InAppWebViewController controller, WebUri? url) {
    debugPrint('[WebView] Load complete: $url');
  }

  void _wireCompanionCallbacks() {
    final companion = context.read<CompanionProvider>();

    companion.onEmotionChange = (emotion) => _callBridge('setEmotion', [emotion]);
    companion.onMotionChange = (motion) => _callBridge('setMotion', [motion]);
    companion.onBlinkUpdate = (blink) {
      if (blink) _callBridge('triggerBlink', []);
    };
    companion.onHeadTarget = (x, y) => _callBridge('setHeadTarget', [x, y]);
    companion.onVisemeUpdate = (weights) {
      final json = jsonEncode(weights);
      _callBridge('setVisemes', [json]);
    };
    companion.onBreathUpdate = (phase) => _callBridge('setBreathPhase', [phase]);
    companion.onLoadModel = (path, isAsset) {
      if (isAsset) {
        _callBridge('loadAsset', [path]);
      } else {
        _callBridge('loadModel', [path]);
      }
    };
  }

  Future<void> _loadInitialModel() async {
    final companion = context.read<CompanionProvider>();

    if (companion.modelPath != null && !companion.modelIsAsset) {
      _callBridge('loadModel', [companion.modelPath]);
    } else {
      // Copy bundled asset to accessible path for WebView
      await _loadBundledAsset();
    }
  }

  Future<void> _loadBundledAsset() async {
    try {
      final data =
          await rootBundle.load('assets/models/default_companion.glb');
      final dir = await getApplicationDocumentsDirectory();
      final file = File('${dir.path}/default_companion.glb');
      await file.writeAsBytes(data.buffer.asUint8List());
      _callBridge('loadModel', ['file://${file.path}']);
    } catch (e) {
      debugPrint('[3D] Failed to load bundled asset: $e');
    }
  }

  void _callBridge(String method, List<dynamic> args) {
    if (_webViewController == null || !_viewerReady) return;
    final argsJson = args
        .map((a) => a is String ? '"${a.replaceAll('"', '\\"')}"' : a.toString())
        .join(', ');
    _webViewController!.evaluateJavascript(
      source: 'window.CompanionBridge.$method($argsJson);',
    );
  }
}

// ── State Overlays ────────────────────────────────────────────────

class _ThinkingOverlay extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Positioned(
      top: 40,
      right: 20,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: AppTheme.glassBg,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: AppTheme.glassBorder),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              width: 14,
              height: 14,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                valueColor: AlwaysStoppedAnimation(AppTheme.accentPrimary),
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'Thinking…',
              style: TextStyle(
                color: AppTheme.textSecondary,
                fontSize: 12,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ListeningOverlay extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Positioned(
      top: 40,
      right: 20,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: AppTheme.accentSecondary.withOpacity(0.2),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: AppTheme.accentSecondary.withOpacity(0.4)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(
                color: AppTheme.accentSecondary,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'Listening',
              style: TextStyle(
                color: AppTheme.accentSecondary,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _NoModelHint extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Positioned(
      top: 0,
      left: 0,
      right: 0,
      child: Container(
        height: 2,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [AppTheme.accentPrimary, AppTheme.accentSecondary],
          ),
        ),
      ),
    );
  }
}
