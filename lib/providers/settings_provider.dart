import 'package:flutter/material.dart';
import '../core/constants.dart';
import '../services/database_service.dart';

class SettingsProvider extends ChangeNotifier {
  final DatabaseService _db = DatabaseService();

  late String apiEndpoint;
  late String apiKey;
  late String modelName;
  late String companionName;
  late String ttsEngine;
  late String elevenLabsKey;
  late String elevenLabsVoice;
  late String? modelPath;
  late bool modelIsAsset;

  void load() {
    apiEndpoint = _db.getSetting(AppConstants.keyApiEndpoint,
        defaultValue: AppConstants.defaultApiEndpoint) as String;
    apiKey = _db.getSetting(AppConstants.keyApiKey, defaultValue: '') as String;
    modelName = _db.getSetting(AppConstants.keyModelName,
        defaultValue: AppConstants.defaultModelName) as String;
    companionName = _db.getSetting(AppConstants.keyCompanionName,
        defaultValue: AppConstants.defaultCompanionName) as String;
    ttsEngine =
        _db.getSetting(AppConstants.keyTtsEngine, defaultValue: 'device')
            as String;
    elevenLabsKey =
        _db.getSetting(AppConstants.keyElevenLabsKey, defaultValue: '')
            as String;
    elevenLabsVoice = _db.getSetting(AppConstants.keyElevenLabsVoice,
        defaultValue: 'EXAVITQu4vr4xnSDxMaL') as String;
    modelPath = _db.getSetting(AppConstants.keyModelPath) as String?;
    modelIsAsset =
        _db.getSetting(AppConstants.keyModelIsAsset, defaultValue: true)
            as bool;
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
  }) async {
    if (newEndpoint != null) {
      apiEndpoint = newEndpoint;
      await _db.setSetting(AppConstants.keyApiEndpoint, newEndpoint);
    }
    if (newApiKey != null) {
      apiKey = newApiKey;
      await _db.setSetting(AppConstants.keyApiKey, newApiKey);
    }
    if (newModelName != null) {
      modelName = newModelName;
      await _db.setSetting(AppConstants.keyModelName, newModelName);
    }
    if (newCompanionName != null) {
      companionName = newCompanionName;
      await _db.setSetting(AppConstants.keyCompanionName, newCompanionName);
    }
    if (newTtsEngine != null) {
      ttsEngine = newTtsEngine;
      await _db.setSetting(AppConstants.keyTtsEngine, newTtsEngine);
    }
    if (newElevenLabsKey != null) {
      elevenLabsKey = newElevenLabsKey;
      await _db.setSetting(AppConstants.keyElevenLabsKey, newElevenLabsKey);
    }
    if (newElevenLabsVoice != null) {
      elevenLabsVoice = newElevenLabsVoice;
      await _db.setSetting(
          AppConstants.keyElevenLabsVoice, newElevenLabsVoice);
    }
    if (newModelPath != null) {
      modelPath = newModelPath;
      await _db.setSetting(AppConstants.keyModelPath, newModelPath);
    }
    if (newModelIsAsset != null) {
      modelIsAsset = newModelIsAsset;
      await _db.setSetting(AppConstants.keyModelIsAsset, newModelIsAsset);
    }
    notifyListeners();
  }
}
