import 'dart:convert';
import 'dart:io';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import '../core/constants.dart';
import '../services/database_service.dart';

enum TtsEngine { device, elevenlabs }

class TtsService {
  final DatabaseService _db = DatabaseService();
  final FlutterTts _flutterTts = FlutterTts();

  bool _isInitialized = false;
  bool _isSpeaking = false;
  Function(double amplitude)? onAmplitude;
  Function()? onComplete;

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

    _isInitialized = true;
  }

  Future<void> speak(String text) async {
    await initialize();
    final engine = _currentEngine;

    if (engine == TtsEngine.elevenlabs) {
      final key = _db.getSetting(AppConstants.keyElevenLabsKey, defaultValue: '') as String;
      final voiceId = _db.getSetting(AppConstants.keyElevenLabsVoice,
          defaultValue: 'EXAVITQu4vr4xnSDxMaL') as String;
      if (key.isNotEmpty) {
        await _speakElevenLabs(text, key, voiceId);
        return;
      }
    }

    await _speakDevice(text);
  }

  Future<void> _speakDevice(String text) async {
    await _flutterTts.speak(text);
  }

  Future<void> _speakElevenLabs(
      String text, String apiKey, String voiceId) async {
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
          'voice_settings': {
            'stability': 0.5,
            'similarity_boost': 0.75,
          },
        }),
      );

      if (response.statusCode == 200) {
        final dir = await getTemporaryDirectory();
        final file = File('${dir.path}/tts_output.mp3');
        await file.writeAsBytes(response.bodyBytes);
        // Playback handled by AudioService
        onComplete?.call();
      } else {
        // Fallback to device TTS
        await _speakDevice(text);
      }
    } catch (_) {
      await _speakDevice(text);
    }
  }

  Future<void> stop() async {
    await _flutterTts.stop();
    _isSpeaking = false;
  }

  bool get isSpeaking => _isSpeaking;

  TtsEngine get _currentEngine {
    final engineStr = _db.getSetting(AppConstants.keyTtsEngine,
        defaultValue: 'device') as String;
    return engineStr == 'elevenlabs' ? TtsEngine.elevenlabs : TtsEngine.device;
  }

  Future<List<dynamic>> getAvailableVoices() async {
    return await _flutterTts.getVoices ?? [];
  }

  Future<void> setRate(double rate) async {
    await _flutterTts.setSpeechRate(rate.clamp(0.1, 2.0));
  }

  Future<void> setPitch(double pitch) async {
    await _flutterTts.setPitch(pitch.clamp(0.5, 2.0));
  }
}
