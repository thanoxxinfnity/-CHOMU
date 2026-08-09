import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'package:audioplayers/audioplayers.dart';
import 'package:record/record.dart';
import 'package:path_provider/path_provider.dart';
import 'viseme_service.dart';

class AudioService {
  final AudioPlayer _player = AudioPlayer();
  final AudioRecorder _recorder = AudioRecorder();
  VisemeService? visemeService;

  Timer? _amplitudeTimer;
  bool _isRecording = false;
  bool _isPlaying = false;
  String? _currentRecordingPath;

  // Callbacks
  Function(String path)? onRecordingComplete;
  Function(double rms)? onAmplitudeUpdate;
  Function()? onPlaybackComplete;

  Future<void> initialize() async {
    _player.onPlayerStateChanged.listen((state) {
      _isPlaying = state == PlayerState.playing;
      if (state == PlayerState.completed) {
        _stopAmplitudeTracking();
        onPlaybackComplete?.call();
      }
    });
  }

  // ── Recording ─────────────────────────────────────────────────────────────

  Future<bool> startRecording() async {
    if (_isRecording) return false;
    final hasPermission = await _recorder.hasPermission();
    if (!hasPermission) return false;

    final dir = await getTemporaryDirectory();
    _currentRecordingPath = '${dir.path}/recording_${DateTime.now().millisecondsSinceEpoch}.m4a';

    await _recorder.start(
      const RecordConfig(
        encoder: AudioEncoder.aacLc,
        bitRate: 128000,
        sampleRate: 16000,
      ),
      path: _currentRecordingPath!,
    );

    _isRecording = true;
    _startAmplitudeTracking();
    return true;
  }

  Future<String?> stopRecording() async {
    if (!_isRecording) return null;
    _stopAmplitudeTracking();
    final path = await _recorder.stop();
    _isRecording = false;
    onRecordingComplete?.call(path ?? _currentRecordingPath ?? '');
    return path;
  }

  Future<void> cancelRecording() async {
    if (!_isRecording) return;
    _stopAmplitudeTracking();
    await _recorder.cancel();
    _isRecording = false;
    if (_currentRecordingPath != null) {
      try {
        await File(_currentRecordingPath!).delete();
      } catch (_) {}
    }
  }

  // ── Playback ──────────────────────────────────────────────────────────────

  Future<void> playFile(String path) async {
    await _player.stop();
    await _player.play(DeviceFileSource(path));
    _isPlaying = true;
    _startAmplitudeTracking();
  }

  Future<void> playAsset(String assetPath) async {
    await _player.stop();
    await _player.play(AssetSource(assetPath));
    _isPlaying = true;
    _startAmplitudeTracking();
  }

  Future<void> stopPlayback() async {
    await _player.stop();
    _stopAmplitudeTracking();
    _isPlaying = false;
  }

  // ── Amplitude Tracking ────────────────────────────────────────────────────

  void _startAmplitudeTracking() {
    _amplitudeTimer?.cancel();
    _amplitudeTimer = Timer.periodic(
      const Duration(milliseconds: 33), // ~30 fps
      (_) async {
        double rms = 0.0;
        if (_isRecording) {
          final amp = await _recorder.getAmplitude();
          // Convert dBFS to linear 0-1
          rms = _dbToLinear(amp.current);
        } else if (_isPlaying) {
          // Simulate amplitude from player position for lip-sync
          // (real amplitude requires native audio analysis)
          rms = _simulatePlaybackAmplitude();
        }
        onAmplitudeUpdate?.call(rms);
        visemeService?.processAmplitude(rms);
      },
    );
  }

  void _stopAmplitudeTracking() {
    _amplitudeTimer?.cancel();
    _amplitudeTimer = null;
    onAmplitudeUpdate?.call(0.0);
    visemeService?.processAmplitude(0.0);
  }

  double _dbToLinear(double dbfs) {
    if (dbfs <= -60) return 0.0;
    return math.pow(10, dbfs / 20).toDouble().clamp(0.0, 1.0);
  }

  double _simulatePlaybackAmplitude() {
    // Generates pseudo-random speech-like amplitude envelope
    final t = DateTime.now().millisecondsSinceEpoch / 1000.0;
    final base = math.sin(t * 8) * 0.3 + math.sin(t * 13) * 0.2 + 0.4;
    return base.clamp(0.0, 1.0);
  }

  bool get isRecording => _isRecording;
  bool get isPlaying => _isPlaying;

  Future<void> dispose() async {
    _stopAmplitudeTracking();
    await _recorder.dispose();
    await _player.dispose();
  }
}
