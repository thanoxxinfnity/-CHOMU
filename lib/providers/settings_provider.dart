import 'package:flutter/material.dart';
import '../core/constants.dart';
import '../services/database_service.dart';

class SettingsProvider extends ChangeNotifier {
  final DatabaseService _db = DatabaseService();

  // All fields initialized with defaults — never late — prevents LateInitializationError
  String apiEndpoint = AppConstants.defaultApiEndpoint;
  String apiKey = '';
  String modelName = AppConstants.defaultModelName;
  String companionName = AppConstants.defaultCompanionName;
  String ttsEngine = 'device';
  String elevenLabsKey = '';
  String elevenLabsVoice = 'EXAVITQu4vr4xnSDxMaL';
  String? modelPath;
  bool modelIsAsset = true;

  // NVIDIA NIM
  String nvidiaApiKey = '';
  String nvidiaModel = 'meta/llama-3.1-70b-instruct';
  bool nvidiaLiveMode = false;
  bool nvidiaVisionEnabled = false;
  String nvidiaVoiceId = 'aria';
  String? nvidiaClonedVoiceId;
  String? nvidiaClonedVoiceName;

  // Active provider
  String activeProvider = 'nvidia'; // default to nvidia

  bool get isNvidiaActive => activeProvider == 'nvidia';
  bool get isNvidiaVoiceActive => ttsEngine == 'nvidia';

  void load() {
    try {
      apiEndpoint = _db.getSetting(AppConstants.keyApiEndpoint,
          defaultValue: AppConstants.defaultApiEndpoint) as String? ?? AppConstants.defaultApiEndpoint;
      apiKey = _db.getSetting(AppConstants.keyApiKey, defaultValue: '') as String? ?? '';
      modelName = _db.getSetting(AppConstants.keyModelName,
          defaultValue: AppConstants.defaultModelName) as String? ?? AppConstants.defaultModelName;
      companionName = _db.getSetting(AppConstants.keyCompanionName,
          defaultValue: AppConstants.defaultCompanionName) as String? ?? AppConstants.defaultCompanionName;
      ttsEngine = _db.getSetting(AppConstants.keyTtsEngine,
          defaultValue: 'device') as String? ?? 'device';
      elevenLabsKey = _db.getSetting(AppConstants.keyElevenLabsKey, defaultValue: '') as String? ?? '';
      elevenLabsVoice = _db.getSetting(AppConstants.keyElevenLabsVoice,
          defaultValue: 'EXAVITQu4vr4xnSDxMaL') as String? ?? 'EXAVITQu4vr4xnSDxMaL';
      modelPath = _db.getSetting(AppConstants.keyModelPath) as String?;
      modelIsAsset = _db.getSetting(AppConstants.keyModelIsAsset,
          defaultValue: true) as bool? ?? true;

      nvidiaApiKey = _db.getSetting(AppConstants.keyNvidiaApiKey, defaultValue: '') as String? ?? '';
      nvidiaModel = _db.getSetting(AppConstants.keyNvidiaModel,
          defaultValue: 'meta/llama-3.1-70b-instruct') as String? ?? 'meta/llama-3.1-70b-instruct';
      nvidiaLiveMode = _db.getSetting(AppConstants.keyNvidiaLiveMode,
          defaultValue: false) as bool? ?? false;
      nvidiaVisionEnabled = _db.getSetting(AppConstants.keyNvidiaVisionEnabled,
          defaultValue: false) as bool? ?? false;
      nvidiaVoiceId = _db.getSetting(AppConstants.keyNvidiaVoiceId,
          defaultValue: 'aria') as String? ?? 'aria';
      nvidiaClonedVoiceId = _db.getSetting(AppConstants.keyNvidiaClonedVoiceId) as String?;
      nvidiaClonedVoiceName = _db.getSetting(AppConstants.keyNvidiaClonedVoiceName) as String?;

      activeProvider = _db.getSetting(AppConstants.keyActiveProvider,
          defaultValue: 'nvidia') as String? ?? 'nvidia';

      notifyListeners();
    } catch (e) {
      debugPrint('[Settings] load error (using defaults): $e');
    }
  }

  Future<void> save({
    String? newEndpoint,
    String? newApiKey,
    String? newModelName,
    String? newCompanionName,
    String? newTtsEngine,
    String? newElevenLabsKey,
    String? newElevenLabsVoice,
    String? newModelPath,
    bool? newModelIsAsset,
    String? newNvidiaApiKey,
    String? newNvidiaModel,
    bool? newNvidiaLiveMode,
    bool? newNvidiaVisionEnabled,
    String? newNvidiaVoiceId,
    String? newNvidiaClonedVoiceId,
    String? newNvidiaClonedVoiceName,
    String? newActiveProvider,
  }) async {
    Future<void> put(String key, dynamic value) async {
      if (value != null) await _db.setSetting(key, value);
    }

    try {
      if (newEndpoint != null) { apiEndpoint = newEndpoint; await put(AppConstants.keyApiEndpoint, newEndpoint); }
      if (newApiKey != null) { apiKey = newApiKey; await put(AppConstants.keyApiKey, newApiKey); }
      if (newModelName != null) { modelName = newModelName; await put(AppConstants.keyModelName, newModelName); }
      if (newCompanionName != null) { companionName = newCompanionName; await put(AppConstants.keyCompanionName, newCompanionName); }
      if (newTtsEngine != null) { ttsEngine = newTtsEngine; await put(AppConstants.keyTtsEngine, newTtsEngine); }
      if (newElevenLabsKey != null) { elevenLabsKey = newElevenLabsKey; await put(AppConstants.keyElevenLabsKey, newElevenLabsKey); }
      if (newElevenLabsVoice != null) { elevenLabsVoice = newElevenLabsVoice; await put(AppConstants.keyElevenLabsVoice, newElevenLabsVoice); }
      if (newModelPath != null) { modelPath = newModelPath; await put(AppConstants.keyModelPath, newModelPath); }
      if (newModelIsAsset != null) { modelIsAsset = newModelIsAsset; await put(AppConstants.keyModelIsAsset, newModelIsAsset); }
      if (newNvidiaApiKey != null) { nvidiaApiKey = newNvidiaApiKey; await put(AppConstants.keyNvidiaApiKey, newNvidiaApiKey); }
      if (newNvidiaModel != null) { nvidiaModel = newNvidiaModel; await put(AppConstants.keyNvidiaModel, newNvidiaModel); }
      if (newNvidiaLiveMode != null) { nvidiaLiveMode = newNvidiaLiveMode; await put(AppConstants.keyNvidiaLiveMode, newNvidiaLiveMode); }
      if (newNvidiaVisionEnabled != null) { nvidiaVisionEnabled = newNvidiaVisionEnabled; await put(AppConstants.keyNvidiaVisionEnabled, newNvidiaVisionEnabled); }
      if (newNvidiaVoiceId != null) { nvidiaVoiceId = newNvidiaVoiceId; await put(AppConstants.keyNvidiaVoiceId, newNvidiaVoiceId); }
      if (newNvidiaClonedVoiceId != null) { nvidiaClonedVoiceId = newNvidiaClonedVoiceId; await put(AppConstants.keyNvidiaClonedVoiceId, newNvidiaClonedVoiceId); }
      if (newNvidiaClonedVoiceName != null) { nvidiaClonedVoiceName = newNvidiaClonedVoiceName; await put(AppConstants.keyNvidiaClonedVoiceName, newNvidiaClonedVoiceName); }
      if (newActiveProvider != null) { activeProvider = newActiveProvider; await put(AppConstants.keyActiveProvider, newActiveProvider); }
    } catch (e) {
      debugPrint('[Settings] save error: $e');
    }

    notifyListeners();
  }
}
