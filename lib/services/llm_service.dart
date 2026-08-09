import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/ai_response.dart';
import '../models/message.dart';
import '../core/constants.dart';
import '../services/database_service.dart';
import '../services/nvidia_service.dart';

class LlmService {
  final DatabaseService _db = DatabaseService();
  final NvidiaService _nvidia = NvidiaService();

  String get _activeProvider =>
      _db.getSetting(AppConstants.keyActiveProvider, defaultValue: 'openai')
          as String;

  bool get _isNvidia => _activeProvider == 'nvidia';
  bool get _isLiveMode =>
      _db.getSetting(AppConstants.keyNvidiaLiveMode, defaultValue: false) as bool;

  // ── Entry point ────────────────────────────────────────────────────────────

  Future<AiResponse> sendMessage({
    required String userMessage,
    required String sessionId,
  }) async {
    final messages = _buildMessages(userMessage, sessionId);

    if (_isNvidia) {
      return _sendNvidia(messages);
    } else {
      return _sendGenericOpenAI(messages);
    }
  }

  // ── Streaming (Live Mode) ──────────────────────────────────────────────────

  Stream<String> sendMessageStream({
    required String userMessage,
    required String sessionId,
  }) {
    final messages = _buildMessages(userMessage, sessionId);
    return _nvidia.chatStream(messages: messages);
  }

  // ── NVIDIA NIM path ────────────────────────────────────────────────────────

  Future<AiResponse> _sendNvidia(
      List<Map<String, dynamic>> messages) async {
    return _nvidia.chat(messages: messages);
  }

  // ── Generic OpenAI-compatible path ────────────────────────────────────────

  Future<AiResponse> _sendGenericOpenAI(
      List<Map<String, dynamic>> messages) async {
    final endpoint =
        _db.getSetting(AppConstants.keyApiEndpoint,
            defaultValue: AppConstants.defaultApiEndpoint) as String;
    final apiKey =
        _db.getSetting(AppConstants.keyApiKey, defaultValue: '') as String;
    final model =
        _db.getSetting(AppConstants.keyModelName,
            defaultValue: AppConstants.defaultModelName) as String;

    final isGemini =
        endpoint.contains('generativelanguage.googleapis.com');

    if (isGemini) {
      return _callGemini(
          endpoint: endpoint,
          apiKey: apiKey,
          model: model,
          messages: messages);
    } else {
      return _callOpenAI(
          endpoint: endpoint,
          apiKey: apiKey,
          model: model,
          messages: messages);
    }
  }

  // ── OpenAI ────────────────────────────────────────────────────────────────

  Future<AiResponse> _callOpenAI({
    required String endpoint,
    required String apiKey,
    required String model,
    required List<Map<String, dynamic>> messages,
  }) async {
    final url = endpoint.endsWith('/chat/completions')
        ? Uri.parse(endpoint)
        : Uri.parse('$endpoint/chat/completions');

    try {
      final response = await http
          .post(
            url,
            headers: {
              'Content-Type': 'application/json',
              if (apiKey.isNotEmpty) 'Authorization': 'Bearer $apiKey',
            },
            body: jsonEncode({
              'model': model,
              'messages': messages,
              'temperature': 0.85,
              'max_tokens': 512,
              'response_format': {'type': 'json_object'},
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        final content =
            data['choices'][0]['message']['content'] as String? ?? '';
        return AiResponse.fromRaw(content);
      }
      final body = jsonDecode(response.body);
      return AiResponse.error(
          body['error']?['message'] as String? ??
              'API error ${response.statusCode}');
    } catch (e) {
      return AiResponse.error('$e');
    }
  }

  // ── Gemini ────────────────────────────────────────────────────────────────

  Future<AiResponse> _callGemini({
    required String endpoint,
    required String apiKey,
    required String model,
    required List<Map<String, dynamic>> messages,
  }) async {
    final systemMsg = messages.firstWhere(
        (m) => m['role'] == 'system',
        orElse: () => {'role': 'system', 'content': ''});
    final url = Uri.parse(
        'https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey');

    final contents = messages
        .where((m) => m['role'] != 'system')
        .map((m) => {
              'role': m['role'] == 'assistant' ? 'model' : 'user',
              'parts': [{'text': m['content']}],
            })
        .toList();

    try {
      final response = await http
          .post(
            url,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'system_instruction': {
                'parts': [{'text': systemMsg['content']}]
              },
              'contents': contents,
              'generationConfig': {
                'temperature': 0.85,
                'maxOutputTokens': 512,
                'responseMimeType': 'application/json',
              },
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        final content =
            data['candidates'][0]['content']['parts'][0]['text'] as String? ?? '';
        return AiResponse.fromRaw(content);
      }
      final body = jsonDecode(response.body);
      return AiResponse.error(
          body['error']?['message'] as String? ??
              'Gemini error ${response.statusCode}');
    } catch (e) {
      return AiResponse.error('$e');
    }
  }

  // ── Shared: build message list (public for streaming path) ───────────────

  List<Map<String, dynamic>> buildMessages(String userMessage, String sessionId) =>
      _buildMessages(userMessage, sessionId);

  List<Map<String, dynamic>> _buildMessages(
      String userMessage, String sessionId) {
    final companionName =
        _db.getSetting(AppConstants.keyCompanionName,
            defaultValue: AppConstants.defaultCompanionName) as String;
    final memoryContext = _db.buildMemoryContext();
    final systemPromptTemplate =
        _db.getSetting(AppConstants.keySystemPrompt,
            defaultValue: AppConstants.defaultSystemPrompt) as String;
    final systemPrompt = systemPromptTemplate
        .replaceAll('{companion_name}', companionName)
        .replaceAll('{memory_context}', memoryContext);

    final history = _db.getRecentMessages(sessionId, limit: 10);
    final messages = <Map<String, dynamic>>[
      {'role': 'system', 'content': systemPrompt},
    ];
    for (final msg in history) {
      messages.add({'role': msg.role, 'content': msg.content});
    }
    messages.add({'role': 'user', 'content': userMessage});
    return messages;
  }
}
