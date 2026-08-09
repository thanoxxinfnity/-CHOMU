import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/ai_response.dart';
import '../models/message.dart';
import '../core/constants.dart';
import '../services/database_service.dart';

class LlmService {
  final DatabaseService _db = DatabaseService();

  Future<AiResponse> sendMessage({
    required String userMessage,
    required String sessionId,
    String? overrideEndpoint,
    String? overrideApiKey,
    String? overrideModel,
  }) async {
    final endpoint = overrideEndpoint ??
        _db.getSetting(AppConstants.keyApiEndpoint,
            defaultValue: AppConstants.defaultApiEndpoint) as String;
    final apiKey = overrideApiKey ??
        _db.getSetting(AppConstants.keyApiKey, defaultValue: '') as String;
    final model = overrideModel ??
        _db.getSetting(AppConstants.keyModelName,
            defaultValue: AppConstants.defaultModelName) as String;
    final companionName = _db.getSetting(AppConstants.keyCompanionName,
        defaultValue: AppConstants.defaultCompanionName) as String;

    final memoryContext = _db.buildMemoryContext();
    final systemPromptTemplate = _db.getSetting(AppConstants.keySystemPrompt,
        defaultValue: AppConstants.defaultSystemPrompt) as String;
    final systemPrompt = systemPromptTemplate
        .replaceAll('{companion_name}', companionName)
        .replaceAll('{memory_context}', memoryContext);

    // Build message history
    final history = _db.getRecentMessages(sessionId, limit: 10);
    final messages = <Map<String, String>>[
      {'role': 'system', 'content': systemPrompt},
    ];

    for (final msg in history) {
      messages.add({'role': msg.role, 'content': msg.content});
    }
    messages.add({'role': 'user', 'content': userMessage});

    try {
      final isGemini = endpoint.contains('generativelanguage.googleapis.com');

      if (isGemini) {
        return await _callGemini(
          endpoint: endpoint,
          apiKey: apiKey,
          model: model,
          messages: messages,
          systemPrompt: systemPrompt,
          userMessage: userMessage,
        );
      } else {
        return await _callOpenAI(
          endpoint: endpoint,
          apiKey: apiKey,
          model: model,
          messages: messages,
        );
      }
    } on http.ClientException catch (e) {
      return AiResponse.error('Network error: ${e.message}');
    } catch (e) {
      return AiResponse.error('Error: ${e.toString()}');
    }
  }

  Future<AiResponse> _callOpenAI({
    required String endpoint,
    required String apiKey,
    required String model,
    required List<Map<String, String>> messages,
  }) async {
    final url = endpoint.endsWith('/chat/completions')
        ? Uri.parse(endpoint)
        : Uri.parse('$endpoint/chat/completions');

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
    } else {
      final body = jsonDecode(response.body);
      final errMsg = body['error']?['message'] ?? 'API error ${response.statusCode}';
      return AiResponse.error(errMsg as String);
    }
  }

  Future<AiResponse> _callGemini({
    required String endpoint,
    required String apiKey,
    required String model,
    required List<Map<String, String>> messages,
    required String systemPrompt,
    required String userMessage,
  }) async {
    final url = Uri.parse(
        'https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey');

    // Convert to Gemini format
    final contents = messages
        .where((m) => m['role'] != 'system')
        .map((m) => {
              'role': m['role'] == 'assistant' ? 'model' : 'user',
              'parts': [
                {'text': m['content']}
              ],
            })
        .toList();

    final response = await http
        .post(
          url,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({
            'system_instruction': {
              'parts': [
                {'text': systemPrompt}
              ]
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
      final content = data['candidates'][0]['content']['parts'][0]['text']
          as String? ?? '';
      return AiResponse.fromRaw(content);
    } else {
      final body = jsonDecode(response.body);
      final errMsg = body['error']?['message'] ?? 'Gemini error ${response.statusCode}';
      return AiResponse.error(errMsg as String);
    }
  }
}
