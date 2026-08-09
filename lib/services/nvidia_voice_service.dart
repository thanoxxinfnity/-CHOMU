import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import '../core/constants.dart';
import '../services/database_service.dart';

/// NVIDIA NIM Voice Service — TTS with built-in presets + voice cloning.
///
/// Uses NVIDIA's PlayAI TTS NIM endpoint (OpenAI-compatible audio API).
/// Voice cloning sends a reference audio sample to create a custom voice ID.
class NvidiaVoiceService {
  static const String _baseUrl = 'https://integrate.api.nvidia.com/v1';

  // ── Built-in Voice Presets ─────────────────────────────────────────────────
  //
  // These map to NVIDIA PlayAI TTS speaker IDs.
  // Display name → voice_id value sent to the API.
  static const Map<String, NvidiaVoicePreset> builtinPresets = {
    // International voices
    'Aria (Female, Warm)':       NvidiaVoicePreset(id: 'aria',    gender: 'female', style: 'warm'),
    'Atlas (Male, Deep)':        NvidiaVoicePreset(id: 'atlas',   gender: 'male',   style: 'deep'),
    'Elara (Female, Soft)':      NvidiaVoicePreset(id: 'elara',   gender: 'female', style: 'soft'),
    'Orion (Male, Clear)':       NvidiaVoicePreset(id: 'orion',   gender: 'male',   style: 'clear'),
    'Nova (Female, Bright)':     NvidiaVoicePreset(id: 'nova',    gender: 'female', style: 'bright'),
    'Rex (Male, Casual)':        NvidiaVoicePreset(id: 'rex',     gender: 'male',   style: 'casual'),
    'Luna (Female, Calm)':       NvidiaVoicePreset(id: 'luna',    gender: 'female', style: 'calm'),
    'Zephyr (NB, Airy)':         NvidiaVoicePreset(id: 'zephyr',  gender: 'neutral',style: 'airy'),
    // Indian accent voices
    'Priya (Female, Indian)':    NvidiaVoicePreset(id: 'priya',   gender: 'female', style: 'indian'),
    'Arjun (Male, Indian)':      NvidiaVoicePreset(id: 'arjun',   gender: 'male',   style: 'indian'),
    'Ananya (Female, Hindi)':    NvidiaVoicePreset(id: 'ananya',  gender: 'female', style: 'hindi'),
    'Rohan (Male, Hindi)':       NvidiaVoicePreset(id: 'rohan',   gender: 'male',   style: 'hindi'),
  };

  final DatabaseService _db = DatabaseService();

  String get _apiKey =>
      _db.getSetting(AppConstants.keyNvidiaApiKey, defaultValue: '') as String;

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $_apiKey',
      };

  // ── TTS with Preset ────────────────────────────────────────────────────────

  /// Synthesise [text] using [voiceId] (preset ID or cloned voice ID).
  /// Returns path to a temporary .wav file, or null on failure.
  Future<String?> synthesise(String text, {String? voiceId}) async {
    final voice = voiceId ??
        (_db.getSetting(AppConstants.keyNvidiaVoiceId,
                defaultValue: 'aria') as String);

    final clonedId =
        _db.getSetting(AppConstants.keyNvidiaClonedVoiceId) as String?;
    final effectiveVoice = (clonedId != null && clonedId.isNotEmpty)
        ? clonedId
        : voice;

    try {
      final response = await http.post(
        Uri.parse('$_baseUrl/audio/speech'),
        headers: _headers,
        body: jsonEncode({
          'model': 'nvidia/tts-fastpitch', // NVIDIA FastPitch TTS NIM
          'input': text,
          'voice': effectiveVoice,
          'response_format': 'wav',
          'speed': 1.0,
        }),
      ).timeout(const Duration(seconds: 20));

      if (response.statusCode == 200) {
        final dir = await getTemporaryDirectory();
        final path =
            '${dir.path}/nvidia_tts_${DateTime.now().millisecondsSinceEpoch}.wav';
        await File(path).writeAsBytes(response.bodyBytes);
        return path;
      } else {
        // Try fallback model
        return await _synthesiseFallback(text, effectiveVoice);
      }
    } catch (_) {
      return await _synthesiseFallback(text, effectiveVoice);
    }
  }

  Future<String?> _synthesiseFallback(String text, String voice) async {
    try {
      final response = await http.post(
        Uri.parse('$_baseUrl/audio/speech'),
        headers: _headers,
        body: jsonEncode({
          'model': 'playai/playai-tts',
          'input': text,
          'voice': voice,
          'response_format': 'mp3',
        }),
      ).timeout(const Duration(seconds: 20));

      if (response.statusCode == 200) {
        final dir = await getTemporaryDirectory();
        final path =
            '${dir.path}/nvidia_tts_${DateTime.now().millisecondsSinceEpoch}.mp3';
        await File(path).writeAsBytes(response.bodyBytes);
        return path;
      }
    } catch (_) {}
    return null;
  }

  // ── Voice Cloning ──────────────────────────────────────────────────────────

  /// Upload a reference audio file to clone the voice.
  /// [audioFilePath] — path to a clear 10-30 second speech sample (.wav/.mp3)
  /// Returns a cloned voice ID that can be used in [synthesise].
  Future<VoiceCloneResult> cloneVoice({
    required String audioFilePath,
    required String voiceName,
    String? description,
  }) async {
    try {
      // Step 1: upload the reference audio
      final file = File(audioFilePath);
      if (!file.existsSync()) {
        return VoiceCloneResult.error('Audio file not found');
      }

      final audioBytes = await file.readAsBytes();
      final ext = audioFilePath.split('.').last.toLowerCase();

      // NVIDIA voice cloning endpoint
      final uploadRequest = http.MultipartRequest(
        'POST',
        Uri.parse('$_baseUrl/audio/voices'),
      )
        ..headers['Authorization'] = 'Bearer $_apiKey'
        ..fields['name'] = voiceName
        ..fields['description'] = description ?? 'Cloned via CHOMU'
        ..files.add(http.MultipartFile.fromBytes(
          'file',
          audioBytes,
          filename: 'reference.$ext',
        ));

      final uploadResponse =
          await uploadRequest.send().timeout(const Duration(seconds: 30));
      final uploadBody = await uploadResponse.stream.bytesToString();

      if (uploadResponse.statusCode == 200 ||
          uploadResponse.statusCode == 201) {
        final json = jsonDecode(uploadBody) as Map<String, dynamic>;
        final voiceId = json['id'] as String? ?? json['voice_id'] as String?;

        if (voiceId != null) {
          // Save to settings
          await _db.setSetting(AppConstants.keyNvidiaClonedVoiceId, voiceId);
          await _db.setSetting(AppConstants.keyNvidiaClonedVoiceName, voiceName);
          return VoiceCloneResult.success(voiceId: voiceId, name: voiceName);
        } else {
          return VoiceCloneResult.error(
              'No voice ID returned: $uploadBody');
        }
      } else {
        return VoiceCloneResult.error(
            'Upload failed (${uploadResponse.statusCode}): $uploadBody');
      }
    } catch (e) {
      return VoiceCloneResult.error('Clone error: $e');
    }
  }

  /// Remove the currently saved cloned voice.
  Future<void> clearClonedVoice() async {
    await _db.setSetting(AppConstants.keyNvidiaClonedVoiceId, null);
    await _db.setSetting(AppConstants.keyNvidiaClonedVoiceName, null);
  }

  // ── Custom Preset Management ───────────────────────────────────────────────

  List<CustomVoicePreset> getCustomPresets() {
    final raw =
        _db.getSetting(AppConstants.keyNvidiaCustomPresets, defaultValue: '[]')
            as String;
    try {
      final list = jsonDecode(raw) as List<dynamic>;
      return list
          .map((e) => CustomVoicePreset.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return [];
    }
  }

  Future<void> saveCustomPreset(CustomVoicePreset preset) async {
    final presets = getCustomPresets();
    presets.removeWhere((p) => p.id == preset.id);
    presets.add(preset);
    await _db.setSetting(
      AppConstants.keyNvidiaCustomPresets,
      jsonEncode(presets.map((p) => p.toJson()).toList()),
    );
  }

  Future<void> deleteCustomPreset(String presetId) async {
    final presets = getCustomPresets()
      ..removeWhere((p) => p.id == presetId);
    await _db.setSetting(
      AppConstants.keyNvidiaCustomPresets,
      jsonEncode(presets.map((p) => p.toJson()).toList()),
    );
  }

  String? get clonedVoiceId =>
      _db.getSetting(AppConstants.keyNvidiaClonedVoiceId) as String?;

  String? get clonedVoiceName =>
      _db.getSetting(AppConstants.keyNvidiaClonedVoiceName) as String?;

  String get activeVoiceId =>
      clonedVoiceId ??
      (_db.getSetting(AppConstants.keyNvidiaVoiceId, defaultValue: 'aria')
          as String);

  bool get isConfigured => _apiKey.isNotEmpty;
}

// ── Data classes ───────────────────────────────────────────────────────────

class NvidiaVoicePreset {
  final String id;
  final String gender;
  final String style;
  const NvidiaVoicePreset({
    required this.id,
    required this.gender,
    required this.style,
  });
}

class CustomVoicePreset {
  final String id;
  final String name;
  final String voiceId;
  final String? description;

  CustomVoicePreset({
    required this.id,
    required this.name,
    required this.voiceId,
    this.description,
  });

  factory CustomVoicePreset.fromJson(Map<String, dynamic> j) =>
      CustomVoicePreset(
        id: j['id'] as String,
        name: j['name'] as String,
        voiceId: j['voiceId'] as String,
        description: j['description'] as String?,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'voiceId': voiceId,
        'description': description,
      };
}

class VoiceCloneResult {
  final bool success;
  final String? voiceId;
  final String? name;
  final String? error;

  const VoiceCloneResult._({
    required this.success,
    this.voiceId,
    this.name,
    this.error,
  });

  factory VoiceCloneResult.success({required String voiceId, required String name}) =>
      VoiceCloneResult._(success: true, voiceId: voiceId, name: name);

  factory VoiceCloneResult.error(String message) =>
      VoiceCloneResult._(success: false, error: message);
}
