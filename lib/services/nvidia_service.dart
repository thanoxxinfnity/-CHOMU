import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/ai_response.dart';
import '../services/database_service.dart';
import '../core/constants.dart';

/// NVIDIA NIM API — LLM (fast + vision) with optional SSE streaming.
class NvidiaService {
  static const String _baseUrl = 'https://integrate.api.nvidia.com/v1';

  // ── Model Catalog ──────────────────────────────────────────────────────────
  static const Map<String, String> fastModels = {
    'meta/llama-3.1-8b-instruct':            'Llama 3.1 8B  (fastest)',
    'meta/llama-3.1-70b-instruct':           'Llama 3.1 70B (balanced)',
    'nvidia/llama-3.1-nemotron-70b-instruct':'Nemotron 70B  (NVIDIA tuned)',
    'mistralai/mixtral-8x7b-instruct-v0.1':  'Mixtral 8×7B',
    'mistralai/mixtral-8x22b-instruct-v0.1': 'Mixtral 8×22B',
    'google/gemma-2-9b-it':                  'Gemma 2 9B',
    'microsoft/phi-3-mini-128k-instruct':    'Phi-3 Mini 128K',
    'qwen/qwen2-7b-instruct':               'Qwen2 7B',
  };

  static const Map<String, String> visionModels = {
    'meta/llama-3.2-90b-vision-instruct':    'Llama 3.2 90B Vision',
    'meta/llama-3.2-11b-vision-instruct':    'Llama 3.2 11B Vision',
    'microsoft/phi-3.5-vision-instruct':     'Phi-3.5 Vision',
    'nvidia/llama-3.2-11b-vision-instruct':  'NVIDIA Llama Vision 11B',
  };

  static Map<String, String> get allModels => {
        ...fastModels,
        ...visionModels,
      };

  static bool isVisionModel(String model) => visionModels.containsKey(model);

  final DatabaseService _db = DatabaseService();

  String get _apiKey =>
      _db.getSetting(AppConstants.keyNvidiaApiKey, defaultValue: '') as String;
  String get _model =>
      _db.getSetting(AppConstants.keyNvidiaModel,
          defaultValue: 'meta/llama-3.1-70b-instruct') as String;

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'Authorization': 'Bearer $_apiKey',
      };

  // ── Standard (non-streaming) Chat ─────────────────────────────────────────

  Future<AiResponse> chat({
    required List<Map<String, dynamic>> messages,
    double temperature = 0.85,
    int maxTokens = 512,
  }) async {
    final body = jsonEncode({
      'model': _model,
      'messages': messages,
      'temperature': temperature,
      'max_tokens': maxTokens,
      'stream': false,
    });

    try {
      final response = await http
          .post(
            Uri.parse('$_baseUrl/chat/completions'),
            headers: _headers,
            body: body,
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        final content =
            data['choices'][0]['message']['content'] as String? ?? '';
        return AiResponse.fromRaw(content);
      } else {
        final err = _extractError(response.body);
        return AiResponse.error('NIM error ${response.statusCode}: $err');
      }
    } on TimeoutException {
      return AiResponse.error('NVIDIA NIM request timed out.');
    } catch (e) {
      return AiResponse.error('NVIDIA NIM: $e');
    }
  }

  // ── Vision Chat (image + text) ─────────────────────────────────────────────

  Future<AiResponse> chatWithImage({
    required String systemPrompt,
    required String userText,
    required String imageBase64,
    String mimeType = 'image/jpeg',
    List<Map<String, dynamic>> history = const [],
  }) async {
    final model = isVisionModel(_model)
        ? _model
        : 'meta/llama-3.2-90b-vision-instruct'; // auto-switch

    final messages = <Map<String, dynamic>>[
      {'role': 'system', 'content': systemPrompt},
      ...history,
      {
        'role': 'user',
        'content': [
          {
            'type': 'text',
            'text': userText,
          },
          {
            'type': 'image_url',
            'image_url': {
              'url': 'data:$mimeType;base64,$imageBase64',
            },
          },
        ],
      },
    ];

    final body = jsonEncode({
      'model': model,
      'messages': messages,
      'temperature': 0.7,
      'max_tokens': 512,
      'stream': false,
    });

    try {
      final response = await http
          .post(
            Uri.parse('$_baseUrl/chat/completions'),
            headers: _headers,
            body: body,
          )
          .timeout(const Duration(seconds: 45));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        final content =
            data['choices'][0]['message']['content'] as String? ?? '';
        return AiResponse.fromRaw(content);
      } else {
        return AiResponse.error(_extractError(response.body));
      }
    } catch (e) {
      return AiResponse.error('Vision error: $e');
    }
  }

  // ── Streaming Chat (Live Mode) ─────────────────────────────────────────────
  //
  // Yields chunks of text as they arrive via SSE.
  // The caller should accumulate chunks, then parse the final JSON response.

  Stream<String> chatStream({
    required List<Map<String, dynamic>> messages,
    double temperature = 0.85,
    int maxTokens = 512,
  }) async* {
    final body = jsonEncode({
      'model': _model,
      'messages': messages,
      'temperature': temperature,
      'max_tokens': maxTokens,
      'stream': true,
    });

    final request = http.Request(
      'POST',
      Uri.parse('$_baseUrl/chat/completions'),
    )
      ..headers.addAll({
        ..._headers,
        'Accept': 'text/event-stream',
      })
      ..body = body;

    try {
      final streamedResponse = await http.Client().send(request).timeout(
            const Duration(seconds: 60),
          );

      if (streamedResponse.statusCode != 200) {
        final err = await streamedResponse.stream.bytesToString();
        yield* Stream.error('NIM stream error: ${_extractError(err)}');
        return;
      }

      // Parse SSE: each line is "data: {json}" or "data: [DONE]"
      String buffer = '';
      await for (final chunk
          in streamedResponse.stream.transform(utf8.decoder)) {
        buffer += chunk;
        final lines = buffer.split('\n');
        buffer = lines.removeLast(); // keep incomplete last line

        for (final line in lines) {
          if (!line.startsWith('data: ')) continue;
          final data = line.substring(6).trim();
          if (data == '[DONE]') return;
          try {
            final json = jsonDecode(data) as Map<String, dynamic>;
            final delta =
                json['choices']?[0]?['delta']?['content'] as String?;
            if (delta != null && delta.isNotEmpty) {
              yield delta;
            }
          } catch (_) {
            // skip malformed SSE line
          }
        }
      }
    } catch (e) {
      yield* Stream.error('Stream error: $e');
    }
  }

  // ── STT via NVIDIA Parakeet (ASR) ─────────────────────────────────────────

  Future<String?> transcribeAudio(List<int> audioBytes) async {
    // NVIDIA Parakeet ASR endpoint
    const asrUrl =
        'https://integrate.api.nvidia.com/v1/audio/transcriptions';
    try {
      final request = http.MultipartRequest('POST', Uri.parse(asrUrl))
        ..headers['Authorization'] = 'Bearer $_apiKey'
        ..fields['model'] = 'nvidia/parakeet-ctc-1.1b-asr'
        ..files.add(http.MultipartFile.fromBytes(
          'file',
          audioBytes,
          filename: 'audio.m4a',
        ));

      final response = await request.send().timeout(const Duration(seconds: 30));
      if (response.statusCode == 200) {
        final body = await response.stream.bytesToString();
        final json = jsonDecode(body) as Map<String, dynamic>;
        return json['text'] as String?;
      }
    } catch (_) {}
    return null;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  String _extractError(String body) {
    try {
      final json = jsonDecode(body) as Map<String, dynamic>;
      return json['detail'] as String? ??
          json['error']?['message'] as String? ??
          body.substring(0, body.length.clamp(0, 200));
    } catch (_) {
      return body.substring(0, body.length.clamp(0, 200));
    }
  }

  bool get isConfigured => _apiKey.isNotEmpty;
}
