import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../models/ai_response.dart';

enum CompanionState { idle, listening, thinking, speaking, error }

class CompanionProvider extends ChangeNotifier {
  CompanionState _state = CompanionState.idle;
  String _currentEmotion = 'neutral';
  String _currentMotion = 'idle';
  String _currentDialogue = '';
  bool _modelLoaded = false;
  String? _modelPath;
  bool _modelIsAsset = true;

  // Idle animation state
  Timer? _idleTimer;
  Timer? _blinkTimer;
  int _blinkIntervalMs = 3000;
  bool _isBlinking = false;
  double _breathPhase = 0.0;

  // Head tracking
  double _headTargetX = 0.0;
  double _headTargetY = 0.0;

  // Callbacks to the 3D viewer
  Function(String emotion)? onEmotionChange;
  Function(String motion)? onMotionChange;
  Function(Map<String, double> visemes)? onVisemeUpdate;
  Function(double breathPhase)? onBreathUpdate;
  Function(bool blink)? onBlinkUpdate;
  Function(double x, double y)? onHeadTarget;
  Function(String path, bool isAsset)? onLoadModel;
  Function()? onOpenCamera;
  Function()? onCloseCamera;
  Function(String msg)? onCameraStatus;

  bool _cameraOpen = false;
  bool get cameraOpen => _cameraOpen;

  CompanionState get state => _state;
  String get currentEmotion => _currentEmotion;
  String get currentMotion => _currentMotion;
  String get currentDialogue => _currentDialogue;
  bool get modelLoaded => _modelLoaded;
  String? get modelPath => _modelPath;
  bool get modelIsAsset => _modelIsAsset;

  void initialize() {
    _startIdleAnimations();
  }

  void applyAiResponse(AiResponse response) {
    _currentDialogue = response.dialogue;
    setEmotion(response.emotion);
    setMotion(response.motionType);
  }

  void setState(CompanionState newState) {
    _state = newState;
    notifyListeners();
  }

  void setEmotion(String emotion) {
    _currentEmotion = emotion;
    onEmotionChange?.call(emotion);
    notifyListeners();
  }

  void setMotion(String motion) {
    _currentMotion = motion;
    onMotionChange?.call(motion);
    notifyListeners();
  }

  void updateVisemes(Map<String, double> weights) {
    onVisemeUpdate?.call(weights);
  }

  void updateHeadTarget(double normalizedX, double normalizedY) {
    // Smooth interpolation
    _headTargetX = _headTargetX * 0.85 + normalizedX * 0.15;
    _headTargetY = _headTargetY * 0.85 + normalizedY * 0.15;
    onHeadTarget?.call(_headTargetX, _headTargetY);
  }

  void loadModel(String path, {bool isAsset = false}) {
    _modelPath = path;
    _modelIsAsset = isAsset;
    onLoadModel?.call(path, isAsset);
    _modelLoaded = true;
    notifyListeners();
  }

  void setModelLoaded(bool loaded) {
    _modelLoaded = loaded;
    notifyListeners();
  }

  // ── Idle Animations ───────────────────────────────────────────────────────

  void _startIdleAnimations() {
    // Breathing cycle: ~4 second period
    _idleTimer = Timer.periodic(const Duration(milliseconds: 50), (t) {
      _breathPhase =
          (_breathPhase + 0.008) % (2 * math.pi); // ~4s period at 50ms
      onBreathUpdate?.call(_breathPhase);
    });

    // Random blink: every 2–6 seconds
    _scheduleNextBlink();
  }

  void _scheduleNextBlink() {
    final delay = 2000 + math.Random().nextInt(4000);
    _blinkTimer = Timer(Duration(milliseconds: delay), () {
      if (!_isBlinking) _triggerBlink();
      _scheduleNextBlink();
    });
  }

  void _triggerBlink() {
    _isBlinking = true;
    onBlinkUpdate?.call(true);
    Timer(const Duration(milliseconds: 150), () {
      _isBlinking = false;
      onBlinkUpdate?.call(false);
    });
  }

  void triggerManualBlink() => _triggerBlink();

  void openCamera() {
    _cameraOpen = true;
    onOpenCamera?.call();
    notifyListeners();
  }

  void closeCamera() {
    _cameraOpen = false;
    onCloseCamera?.call();
    notifyListeners();
  }

  void setCameraStatus(String msg) {
    onCameraStatus?.call(msg);
  }

  @override
  void dispose() {
    _idleTimer?.cancel();
    _blinkTimer?.cancel();
    super.dispose();
  }
}
