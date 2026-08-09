import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';
import '../providers/companion_provider.dart';
import '../providers/chat_provider.dart';
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
    allowsInlineMediaPlayback: true,
    allowsAirPlayForMediaPlayback: false,
  );

  @override
  Widget build(BuildContext context) {
    final companion = context.watch<CompanionProvider>();

    return Stack(
      children: [
        InAppWebView(
          initialFile: 'assets/html/viewer.html',
          initialSettings: _webViewSettings,
          onWebViewCreated: _onWebViewCreated,
          onLoadStop: _onLoadStop,
          onConsoleMessage: (c, msg) => debugPrint('[WebView] ${msg.message}'),
          onPermissionRequest: (controller, request) async {
            // Auto-grant camera and mic permissions from WebView
            return PermissionResponse(
              resources: request.resources,
              action: PermissionResponseAction.GRANT,
            );
          },
        ),

        // Thinking indicator - simple, no BackdropFilter
        if (companion.state == CompanionState.thinking)
          const Positioned(
            top: 50,
            right: 16,
            child: _ThinkingBadge(),
          ),

        // Listening indicator
        if (companion.state == CompanionState.listening)
          const Positioned(
            top: 50,
            right: 16,
            child: _ListeningBadge(),
          ),
      ],
    );
  }

  void _onWebViewCreated(InAppWebViewController controller) {
    _webViewController = controller;

    controller.addJavaScriptHandler(
      handlerName: 'onViewerReady',
      callback: (_) {
        setState(() => _viewerReady = true);
        _loadInitialModel();
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'onModelLoaded',
      callback: (args) {
        if (!mounted) return null;
        setState(() => _modelLoaded = true);
        context.read<CompanionProvider>().setModelLoaded(true);
        debugPrint('[3D] Model loaded: $args');
        return null;
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'onModelError',
      callback: (args) {
        debugPrint('[3D] Model error: $args');
        return null;
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'onHeadTarget',
      callback: (args) {
        if (!mounted || args.isEmpty || args[0] is! Map) return null;
        final map = args[0] as Map;
        final x = (map['x'] as num?)?.toDouble() ?? 0.0;
        final y = (map['y'] as num?)?.toDouble() ?? 0.0;
        context.read<CompanionProvider>().updateHeadTarget(x, y);
        return null;
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'onCameraFrame',
      callback: (args) async {
        if (!mounted || args.isEmpty) return null;
        final base64 = args[0] as String?;
        if (base64 == null || base64.isEmpty) return null;
        if (!mounted) return null;
        try {
          await context.read<ChatProvider>().sendCameraFrame(base64);
        } catch (e) {
          debugPrint('[Camera] send error: $e');
        }
        return null;
      },
    );

    controller.addJavaScriptHandler(
      handlerName: 'onStatus',
      callback: (args) { debugPrint('[3D] ${args.join(', ')}'); return null; },
    );

    _wireCompanionCallbacks();
  }

  void _onLoadStop(InAppWebViewController controller, WebUri? url) {
    debugPrint('[WebView] Load complete: $url');
  }

  void _wireCompanionCallbacks() {
    if (!mounted) return;
    final companion = context.read<CompanionProvider>();
    companion.onEmotionChange  = (e)    => _callBridge('setEmotion', [e]);
    companion.onMotionChange   = (m)    => _callBridge('setMotion', [m]);
    companion.onBlinkUpdate    = (b)    { if (b) _callBridge('triggerBlink', []); };
    companion.onHeadTarget     = (x, y) => _callBridge('setHeadTarget', [x, y]);
    companion.onVisemeUpdate   = (w)    => _callBridge('setVisemes', [jsonEncode(w)]);
    companion.onBreathUpdate   = (p)    => _callBridge('setBreathPhase', [p]);
    companion.onLoadModel      = (path, isAsset) {
      if (isAsset) _callBridge('loadModel', [path]);
      else         _callBridge('loadModel', [path]);
    };
    companion.onOpenCamera = () => _callBridge('openCamera', []);
    companion.onCloseCamera = () => _callBridge('closeCamera', []);
    companion.onCameraStatus = (msg) => _callBridge('setCameraStatus', [msg]);
  }

  Future<void> _loadInitialModel() async {
    if (!mounted) return;
    final companion = context.read<CompanionProvider>();
    if (companion.modelPath != null) {
      _callBridge('loadModel', [companion.modelPath!]);
    } else {
      await _loadBundledAsset();
    }
  }

  Future<void> _loadBundledAsset() async {
    try {
      final data = await rootBundle.load('assets/models/default_companion.glb');
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
    final argsStr = args.map((a) {
      if (a is String) return '"${a.replaceAll('\\', '\\\\').replaceAll('"', '\\"')}"';
      return a.toString();
    }).join(', ');
    _webViewController!.evaluateJavascript(
      source: 'if(window.CompanionBridge&&window.CompanionBridge.$method)window.CompanionBridge.$method($argsStr);',
    ).catchError((_) {});
  }

  // Public method for camera toggle from outside
  void toggleCamera(bool open) {
    if (open) _callBridge('openCamera', []);
    else _callBridge('closeCamera', []);
  }
}

class _ThinkingBadge extends StatelessWidget {
  const _ThinkingBadge();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: Colors.black.withOpacity(0.65),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.accentPrimary.withOpacity(0.4)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 12, height: 12,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation(AppTheme.accentPrimary),
            ),
          ),
          const SizedBox(width: 6),
          const Text('Thinking…',
            style: TextStyle(color: Colors.white70, fontSize: 11)),
        ],
      ),
    );
  }
}

class _ListeningBadge extends StatelessWidget {
  const _ListeningBadge();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: Colors.black.withOpacity(0.65),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.redAccent.withOpacity(0.5)),
      ),
      child: const Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.mic, color: Colors.redAccent, size: 12),
          SizedBox(width: 6),
          Text('Listening', style: TextStyle(color: Colors.white70, fontSize: 11)),
        ],
      ),
    );
  }
}
