import 'package:flutter/material.dart';
import '../models/message.dart';
import '../models/session.dart';
import '../models/ai_response.dart';
import '../services/database_service.dart';
import '../services/llm_service.dart';
import '../services/tts_service.dart';
import '../services/audio_service.dart';
import '../services/viseme_service.dart';
import 'companion_provider.dart';

class ChatProvider extends ChangeNotifier {
  final DatabaseService _db = DatabaseService();
  final LlmService _llm = LlmService();
  final TtsService _tts = TtsService();
  final AudioService _audio = AudioService();
  late VisemeService _viseme;

  ChatSession? _currentSession;
  List<Message> _messages = [];
  bool _isLoading = false;
  bool _isListening = false;
  String _pendingTranscript = '';
  CompanionProvider? _companion;

  List<Message> get messages => _messages;
  bool get isLoading => _isLoading;
  bool get isListening => _isListening;
  String get pendingTranscript => _pendingTranscript;
  ChatSession? get currentSession => _currentSession;

  Future<void> initialize(CompanionProvider companion) async {
    _companion = companion;
    await _tts.initialize();
    await _audio.initialize();

    _viseme = VisemeService(
      onWeightsUpdate: (weights) => companion.updateVisemes(weights),
    );
    _audio.visemeService = _viseme;

    _tts.onComplete = () {
      _companion?.setState(CompanionState.idle);
      _audio.stopPlayback();
    };

    _audio.onAmplitudeUpdate = (rms) {
      // viseme service handles it via audio.visemeService
    };

    await _startOrResumeSession();
  }

  Future<void> _startOrResumeSession() async {
    final sessions = _db.getAllSessions();
    if (sessions.isNotEmpty) {
      _currentSession = sessions.first;
    } else {
      _currentSession = await _db.createSession(title: 'First Chat');
    }
    _loadMessages();
  }

  void _loadMessages() {
    if (_currentSession == null) return;
    _messages = _db.getMessagesForSession(_currentSession!.id);
    notifyListeners();
  }

  Future<void> sendTextMessage(String text) async {
    if (text.trim().isEmpty || _isLoading) return;
    await _sendMessage(text.trim());
  }

  Future<void> _sendMessage(String text) async {
    final userMsg = Message(
      sessionId: _currentSession!.id,
      role: 'user',
      content: text,
    );
    await _db.saveMessage(userMsg);
    _messages.add(userMsg);

    // Auto-title from first message
    if (_currentSession!.messageCount <= 1) {
      final title = text.length > 40 ? '${text.substring(0, 40)}…' : text;
      await _db.updateSessionTitle(_currentSession!.id, title);
      _currentSession!.title = title;
    }

    _isLoading = true;
    _companion?.setState(CompanionState.thinking);
    notifyListeners();

    try {
      final response = await _llm.sendMessage(
        userMessage: text,
        sessionId: _currentSession!.id,
      );

      // Save facts to long-term memory
      if (response.extractedFacts.isNotEmpty) {
        await _db.saveFactsBatch(response.extractedFacts);
      }

      // Save AI message
      final aiMsg = Message(
        sessionId: _currentSession!.id,
        role: 'assistant',
        content: response.dialogue,
        emotion: response.emotion,
        motionType: response.motionType,
      );
      await _db.saveMessage(aiMsg);
      _messages.add(aiMsg);
      _companion?.applyAiResponse(response);

      _isLoading = false;
      _companion?.setState(CompanionState.speaking);
      notifyListeners();

      await _tts.speak(response.dialogue);
    } catch (e) {
      _isLoading = false;
      _companion?.setState(CompanionState.error);
      notifyListeners();
    }
  }

  Future<bool> startListening() async {
    if (_isListening) return false;
    final started = await _audio.startRecording();
    if (started) {
      _isListening = true;
      _companion?.setState(CompanionState.listening);
      notifyListeners();
    }
    return started;
  }

  Future<void> stopListening({required Function(String) onTranscript}) async {
    if (!_isListening) return;
    final path = await _audio.stopRecording();
    _isListening = false;
    _companion?.setState(CompanionState.thinking);
    notifyListeners();

    if (path != null && path.isNotEmpty) {
      // In production: send to Whisper or Google STT
      // For now we pass back the path for the caller to handle
      onTranscript('[Audio recorded: $path]');
    }
  }

  Future<void> newSession() async {
    _currentSession = await _db.createSession();
    _messages = [];
    _companion?.setState(CompanionState.idle);
    _companion?.setEmotion('neutral');
    notifyListeners();
  }

  Future<void> loadSession(String sessionId) async {
    _currentSession = _db.getAllSessions()
        .firstWhere((s) => s.id == sessionId);
    _loadMessages();
  }

  List<ChatSession> getAllSessions() => _db.getAllSessions();

  Future<void> deleteSession(String sessionId) async {
    await _db.deleteSession(sessionId);
    if (_currentSession?.id == sessionId) {
      await _startOrResumeSession();
    }
    notifyListeners();
  }

  Future<void> stopSpeaking() async {
    await _tts.stop();
    _companion?.setState(CompanionState.idle);
  }

  void setPendingTranscript(String text) {
    _pendingTranscript = text;
    notifyListeners();
  }

  void clearPendingTranscript() {
    _pendingTranscript = '';
    notifyListeners();
  }
}
