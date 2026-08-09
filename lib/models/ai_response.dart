import 'dart:convert';

class AiResponse {
  final String dialogue;
  final String emotion;
  final String motionType;
  final List<String> extractedFacts;
  final bool hasError;
  final String? rawText;

  const AiResponse({
    required this.dialogue,
    this.emotion = 'neutral',
    this.motionType = 'idle',
    this.extractedFacts = const [],
    this.hasError = false,
    this.rawText,
  });

  factory AiResponse.fromJson(Map<String, dynamic> json) {
    return AiResponse(
      dialogue: (json['dialogue'] as String?) ?? '',
      emotion: (json['emotion'] as String?) ?? 'neutral',
      motionType: (json['motion_type'] as String?) ?? 'idle',
      extractedFacts: (json['extracted_facts'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
    );
  }

  factory AiResponse.fromRaw(String raw) {
    try {
      // Try to extract JSON from response — model sometimes wraps in markdown
      final jsonMatch = RegExp(
        r'\{[\s\S]*"dialogue"[\s\S]*\}',
        multiLine: true,
      ).firstMatch(raw);

      if (jsonMatch != null) {
        final jsonStr = jsonMatch.group(0)!;
        final parsed = jsonDecode(jsonStr) as Map<String, dynamic>;
        return AiResponse.fromJson(parsed);
      }
      // Fallback: treat entire text as dialogue
      return AiResponse(dialogue: raw, rawText: raw);
    } catch (_) {
      return AiResponse(dialogue: raw, rawText: raw, hasError: true);
    }
  }

  factory AiResponse.error(String message) {
    return AiResponse(
      dialogue: message,
      emotion: 'sad',
      motionType: 'idle',
      hasError: true,
    );
  }

  @override
  String toString() => 'AiResponse(emotion: $emotion, motion: $motionType, '
      'dialogue: ${dialogue.substring(0, dialogue.length.clamp(0, 40))}...)';
}
