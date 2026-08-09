import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import '../models/message.dart';
import '../models/session.dart';
import '../models/ai_response.dart';
import '../services/database_service.dart';
import '../services/llm_service.dart';
import '../services/tts_service.dart';
import '../services/audio_service.dart';
import '../services/viseme_service.dart';
import '../services/nvidia_service.dart';
import '../core/constants.dart';
import 'companion_provider.dart';

class ChatProvider extends ChangeNotifier {
  final DatabaseService _db = DatabaseService();
  final LlmService _llm = LlmService();
  final TtsService _tts = TtsService();
  final AudioService _audio = AudioService();
  final NvidiaService _nvidia = NvidiaService();
  late VisemeService _viseme;

  ChatSession? _currentSession;
  List<Message> _messages = [];
  bool _isLoading = false;
  bool _isListening = false;
  String _streamBuffer = '';        // accumulates live-mode tokens
  bool _isStreaming = false;
  StreamSubscription<String>? _streamSub;
  CompanionProvider? _companion;

  List<Message> get messages => _messages;
  bool get isLoading => _isLoading;
  bool get isListening => _isListening;
  bool get isStreaming => _isStreaming;
  String get streamBuffer => _streamBuffer;
  ChatSession? get currentSession => _currentSession;

  bool get _liveMode =>
      _db.getSetting(AppConstants.keyNvidiaLiveMode, defaultValue: false)
          as bool;
  bool get _isNvidiaActive =>
      (_db.getSetting(AppConstants.keyActiveProvider, defaultValue: 'openai')
          as String) ==
      'nvidia';

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

  // ── Send ───────────────────────────────────────────────────────────────────

  Future<void> sendTextMessage(String text) async {
    if (text.trim().isEmpty || _isLoading || _isStreaming) return;

    final userMsg = Message(
      sessionId: _currentSession!.id,
      role: 'user',
      content: text.trim(),
    );
    await _db.saveMessage(userMsg);
    _messages.add(userMsg);

    if (_currentSession!.messageCount <= 1) {
      final title =
          text.length > 40 ? '${text.substring(0, 40)}…' : text;
      await _db.updateSessionTitle(_currentSession!.id, title);
      _currentSession!.title = title;
    }

    _companion?.setState(CompanionState.thinking);

    if (_isNvidiaActive && _liveMode) {
      await _sendLiveStream(text.trim());
    } else {
      await _sendBlocking(text.trim());
    }
  }

  // ── Blocking (standard) send ───────────────────────────────────────────────

  Future<void> _sendBlocking(String text) async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _llm.sendMessage(
        userMessage: text,
        sessionId: _currentSession!.id,
      );

      if (response.extractedFacts.isNotEmpty) {
        await _db.saveFactsBatch(response.extractedFacts);
      }

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

  // ── Live streaming send (NVIDIA NIM SSE) ──────────────────────────────────

  Future<void> _sendLiveStream(String text) async {
    _isStreaming = true;
    _streamBuffer = '';
    notifyListeners();

    // Add a placeholder assistant message that updates as tokens stream in
    final placeholderId = 'streaming_${DateTime.now().millisecondsSinceEpoch}';
    final placeholderMsg = Message(
      sessionId: _currentSession!.id,
      role: 'assistant',
      content: '…',
    );
    _messages.add(placeholderMsg);
    notifyListeners();

    final messages = _llm.buildMessages(text, _currentSession!.id);

    _streamSub = _nvidia.chatStream(messages: messages).listen(
      (chunk) {
        _streamBuffer += chunk;
        // Update placeholder message content live
        _messages.last = Message(
          sessionId: _currentSession!.id,
          role: 'assistant',
          content: _streamBuffer,
        );
        notifyListeners();
      },
      onDone: () async {
        _isStreaming = false;

        // Parse the final accumulated JSON response
        final response = AiResponse.fromRaw(_streamBuffer);

        if (response.extractedFacts.isNotEmpty) {
          await _db.saveFactsBatch(response.extractedFacts);
        }

        // Replace placeholder with real persisted message
        _messages.removeLast();
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
        _companion?.setState(CompanionState.speaking);
        notifyListeners();

        await _tts.speak(response.dialogue);
      },
      onError: (e) {
        _isStreaming = false;
        _isLoading = false;
        _companion?.setState(CompanionState.error);
        notifyListeners();
      },
      cancelOnError: true,
    );
  }

  // ── Camera Vision ─────────────────────────────────────────────────────────

  Future<void> sendCameraFrame(String base64Jpeg) async {
    if (_isLoading || _isStreaming) return;
    _isLoading = true;
    _companion?.setState(CompanionState.thinking);
    notifyListeners();

    try {
      final companionName = _db.getSetting(
        AppConstants.keyCompanionName,
        defaultValue: AppConstants.defaultCompanionName,
      ) as String;

      final systemPrompt = 'You are $companionName, a caring AI companion. '
          'The user just showed you their camera. Look at the image carefully and '
          'respond warmly and naturally in a conversational way. Be observant, '
          'playful, and engage with what you see. Keep your response under 80 words. '
          'Respond in JSON: {"dialogue":"...", "emotion":"happy|sad|surprised|excited|neutral|thinking", "motion":"head_nod|wave_hand|idle"}';

      AiResponse response;
      if (_nvidia.isConfigured) {
        response = await _nvidia.chatWithImage(
          systemPrompt: systemPrompt,
          userText: 'What do you see? Tell me about it!',
          imageBase64: base64Jpeg,
          mimeType: 'image/jpeg',
        );
      } else {
        response = AiResponse(
          dialogue: 'Oh wow, I can see you! But I need an NVIDIA API key in Settings to really analyze what\'s in front of the camera.',
          emotion: 'surprised',
          motionType: 'head_nod',
        );
      }

      final userMsg = Message(
        sessionId: _currentSession!.id,
        role: 'user',
        content: '[Camera image shared]',
      );
      await _db.saveMessage(userMsg);
      _messages.add(userMsg);

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
      _companion?.setState(CompanionState.speaking);
      _companion?.setCameraStatus('AI responded ✓');

      _isLoading = false;
      notifyListeners();

      await _tts.speak(response.dialogue);
    } catch (e) {
      _isLoading = false;
      _companion?.setState(CompanionState.idle);
      _companion?.setCameraStatus('Error: $e');
      notifyListeners();
    }
  }

  // ── Microphone ────────────────────────────────────────────────────────────

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
      // Try NVIDIA Parakeet ASR first
      if (_isNvidiaActive && _nvidia.isConfigured) {
        try {
          final bytes = await File(path).readAsBytes();
          final transcript = await _nvidia.transcribeAudio(bytes);
          if (transcript != null && transcript.isNotEmpty) {
            onTranscript(transcript);
            return;
          }
        } catch (_) {}
      }
      // Fallback: return path for manual STT
      onTranscript('Voice recorded — type your message or re-record.');
    }
  }

  // ── Session management ────────────────────────────────────────────────────

  Future<void> newSession() async {
    await _streamSub?.cancel();
    _isStreaming = false;
    _currentSession = await _db.createSession();
    _messages = [];
    _companion?.setState(CompanionState.idle);
    _companion?.setEmotion('neutral');
    notifyListeners();
  }

  Future<void> loadSession(String sessionId) async {
    await _streamSub?.cancel();
    _isStreaming = false;
    _currentSession = _db.getAllSessions().firstWhere((s) => s.id == sessionId);
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
    await _streamSub?.cancel();
    _isStreaming = false;
    _companion?.setState(CompanionState.idle);
  }

  @override
  void dispose() {
    _streamSub?.cancel();
    super.dispose();
  }
}
