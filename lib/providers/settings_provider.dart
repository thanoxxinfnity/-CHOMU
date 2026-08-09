import 'package:flutter/material.dart';
import '../core/constants.dart';
import '../services/database_service.dart';

class SettingsProvider extends ChangeNotifier {
  final DatabaseService _db = DatabaseService();

  // Generic
  late String apiEndpoint;
  late String apiKey;
  late String modelName;
  late String companionName;
  late String ttsEngine;
  late String elevenLabsKey;
  late String elevenLabsVoice;
  late String? modelPath;
  late bool modelIsAsset;

  // NVIDIA NIM
  late String nvidiaApiKey;
  late String nvidiaModel;
  late bool nvidiaLiveMode;
  late bool nvidiaVisionEnabled;
  late String nvidiaVoiceId;
  late String? nvidiaClonedVoiceId;
  late String? nvidiaClonedVoiceName;

  // Active provider
  late String activeProvider; // 'openai' | 'nvidia'

  void load() {
    apiEndpoint = _db.getSetting(AppConstants.keyApiEndpoint,
        defaultValue: AppConstants.defaultApiEndpoint) as String;
    apiKey = _db.getSetting(AppConstants.keyApiKey, defaultValue: '') as String;
    modelName = _db.getSetting(AppConstants.keyModelName,
        defaultValue: AppConstants.defaultModelName) as String;
    companionName = _db.getSetting(AppConstants.keyCompanionName,
        defaultValue: AppConstants.defaultCompanionName) as String;
    ttsEngine = _db.getSetting(AppConstants.keyTtsEngine,
        defaultValue: 'device') as String;
    elevenLabsKey =
        _db.getSetting(AppConstants.keyElevenLabsKey, defaultValue: '') as String;
    elevenLabsVoice = _db.getSetting(AppConstants.keyElevenLabsVoice,
        defaultValue: 'EXAVITQu4vr4xnSDxMaL') as String;
    modelPath = _db.getSetting(AppConstants.keyModelPath) as String?;
    modelIsAsset = _db.getSetting(AppConstants.keyModelIsAsset,
        defaultValue: true) as bool;

    // NVIDIA
    nvidiaApiKey =
        _db.getSetting(AppConstants.keyNvidiaApiKey, defaultValue: '') as String;
    nvidiaModel = _db.getSetting(AppConstants.keyNvidiaModel,
        defaultValue: 'meta/llama-3.1-70b-instruct') as String;
    nvidiaLiveMode = _db.getSetting(AppConstants.keyNvidiaLiveMode,
        defaultValue: false) as bool;
    nvidiaVisionEnabled = _db.getSetting(AppConstants.keyNvidiaVisionEnabled,
        defaultValue: false) as bool;
    nvidiaVoiceId = _db.getSetting(AppConstants.keyNvidiaVoiceId,
        defaultValue: 'aria') as String;
    nvidiaClonedVoiceId =
        _db.getSetting(AppConstants.keyNvidiaClonedVoiceId) as String?;
    nvidiaClonedVoiceName =
        _db.getSetting(AppConstants.keyNvidiaClonedVoiceName) as String?;

    activeProvider = _db.getSetting(AppConstants.keyActiveProvider,
        defaultValue: 'openai') as String;
  }

  bool get isNvidiaActive => activeProvider == 'nvidia';
  bool get isNvidiaVoiceActive => ttsEngine == 'nvidia';

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
    // NVIDIA
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

    if (newEndpoint != null) { apiEndpoint = newEndpoint; await put(AppConstants.keyApiEndpoint, newEndpoint); }
    if (newApiKey != null) { apiKey = newApiKey; await put(AppConstants.keyApiKey, newApiKey); }
    if (newModelName != null) { modelName = newModelName; await put(AppConstants.keyModelName, newModelName); }
    if (newCompanionName != null) { companionName = newCompanionName; await put(AppConstants.keyCompanionName, newCompanionName); }
    if (newTtsEngine != null) { ttsEngine = newTtsEngine; await put(AppConstants.keyTtsEngine, newTtsEngine); }
    if (newElevenLabsKey != null) { elevenLabsKey = newElevenLabsKey; await put(AppConstants.keyElevenLabsKey, newElevenLabsKey); }
    if (newElevenLabsVoice != null) { elevenLabsVoice = newElevenLabsVoice; await put(AppConstants.keyElevenLabsVoice, newElevenLabsVoice); }
    if (newModelPath != null) { modelPath = newModelPath; await put(AppConstants.keyModelPath, newModelPath); }
    if (newModelIsAsset != null) { modelIsAsset = newModelIsAsset; await put(AppConstants.keyModelIsAsset, newModelIsAsset); }

    // NVIDIA
    if (newNvidiaApiKey != null) { nvidiaApiKey = newNvidiaApiKey; await put(AppConstants.keyNvidiaApiKey, newNvidiaApiKey); }
    if (newNvidiaModel != null) { nvidiaModel = newNvidiaModel; await put(AppConstants.keyNvidiaModel, newNvidiaModel); }
    if (newNvidiaLiveMode != null) { nvidiaLiveMode = newNvidiaLiveMode; await put(AppConstants.keyNvidiaLiveMode, newNvidiaLiveMode); }
    if (newNvidiaVisionEnabled != null) { nvidiaVisionEnabled = newNvidiaVisionEnabled; await put(AppConstants.keyNvidiaVisionEnabled, newNvidiaVisionEnabled); }
    if (newNvidiaVoiceId != null) { nvidiaVoiceId = newNvidiaVoiceId; await put(AppConstants.keyNvidiaVoiceId, newNvidiaVoiceId); }
    if (newNvidiaClonedVoiceId != null) { nvidiaClonedVoiceId = newNvidiaClonedVoiceId; await put(AppConstants.keyNvidiaClonedVoiceId, newNvidiaClonedVoiceId); }
    if (newNvidiaClonedVoiceName != null) { nvidiaClonedVoiceName = newNvidiaClonedVoiceName; await put(AppConstants.keyNvidiaClonedVoiceName, newNvidiaClonedVoiceName); }
    if (newActiveProvider != null) { activeProvider = newActiveProvider; await put(AppConstants.keyActiveProvider, newActiveProvider); }

    notifyListeners();
  }
}
