import 'dart:convert';
import 'dart:io';
import 'package:audioplayers/audioplayers.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import '../core/constants.dart';
import '../services/database_service.dart';
import '../services/nvidia_voice_service.dart';

enum TtsEngine { device, elevenlabs, nvidia }

class TtsService {
  final DatabaseService _db = DatabaseService();
  final FlutterTts _flutterTts = FlutterTts();
  final NvidiaVoiceService _nvidiaVoice = NvidiaVoiceService();
  final AudioPlayer _audioPlayer = AudioPlayer();

  bool _isInitialized = false;
  bool _isSpeaking = false;

  Function()? onComplete;
  Function(double rms)? onAmplitudeEstimate;

  Future<void> initialize() async {
    if (_isInitialized) return;
    await _flutterTts.setLanguage('en-US');
    await _flutterTts.setSpeechRate(0.9);
    await _flutterTts.setVolume(1.0);
    await _flutterTts.setPitch(1.1);
    _flutterTts.setStartHandler(() => _isSpeaking = true);
    _flutterTts.setCompletionHandler(() {
      _isSpeaking = false;
      onComplete?.call();
    });
    _flutterTts.setErrorHandler((_) {
      _isSpeaking = false;
      onComplete?.call();
    });
    _audioPlayer.onPlayerStateChanged.listen((s) {
      if (s == PlayerState.completed) {
        _isSpeaking = false;
        onComplete?.call();
      }
    });
    _isInitialized = true;
  }

  Future<void> speak(String text) async {
    await initialize();
    switch (_engine) {
      case TtsEngine.nvidia:
        await _speakNvidia(text);
        break;
      case TtsEngine.elevenlabs:
        final key = _db.getSetting(AppConstants.keyElevenLabsKey,
            defaultValue: '') as String;
        if (key.isNotEmpty) {
          await _speakElevenLabs(text, key);
        } else {
          await _speakDevice(text);
        }
        break;
      case TtsEngine.device:
        await _speakDevice(text);
    }
  }

  // ── NVIDIA TTS ──────────────────────────────────────────────────────────

  Future<void> _speakNvidia(String text) async {
    if (!_nvidiaVoice.isConfigured) {
      await _speakDevice(text);
      return;
    }
    _isSpeaking = true;
    try {
      final path = await _nvidiaVoice.synthesise(text);
      if (path != null) {
        await _audioPlayer.play(DeviceFileSource(path));
      } else {
        await _speakDevice(text);
      }
    } catch (_) {
      await _speakDevice(text);
    }
  }

  // ── Device TTS ──────────────────────────────────────────────────────────

  Future<void> _speakDevice(String text) async {
    await _flutterTts.speak(text);
  }

  // ── ElevenLabs TTS ──────────────────────────────────────────────────────

  Future<void> _speakElevenLabs(String text, String apiKey) async {
    final voiceId = _db.getSetting(AppConstants.keyElevenLabsVoice,
        defaultValue: 'EXAVITQu4vr4xnSDxMaL') as String;
    try {
      final response = await http.post(
        Uri.parse('https://api.elevenlabs.io/v1/text-to-speech/$voiceId'),
        headers: {
          'xi-api-key': apiKey,
          'Content-Type': 'application/json',
        },
        body: jsonEncode({
          'text': text,
          'model_id': 'eleven_monolingual_v1',
          'voice_settings': {'stability': 0.5, 'similarity_boost': 0.75},
        }),
      );
      if (response.statusCode == 200) {
        final dir = await getTemporaryDirectory();
        final file = File('${dir.path}/el_tts_${DateTime.now().millisecondsSinceEpoch}.mp3');
        await file.writeAsBytes(response.bodyBytes);
        _isSpeaking = true;
        await _audioPlayer.play(DeviceFileSource(file.path));
      } else {
        await _speakDevice(text);
      }
    } catch (_) {
      await _speakDevice(text);
    }
  }

  Future<void> stop() async {
    await _flutterTts.stop();
    await _audioPlayer.stop();
    _isSpeaking = false;
  }

  TtsEngine get _engine {
    final v = _db.getSetting(AppConstants.keyTtsEngine, defaultValue: 'device') as String;
    switch (v) {
      case 'nvidia':     return TtsEngine.nvidia;
      case 'elevenlabs': return TtsEngine.elevenlabs;
      default:           return TtsEngine.device;
    }
  }

  bool get isSpeaking => _isSpeaking;

  Future<void> dispose() async {
    await _flutterTts.stop();
    await _audioPlayer.dispose();
  }
}
