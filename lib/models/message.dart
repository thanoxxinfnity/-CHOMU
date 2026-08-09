import 'package:hive/hive.dart';
import 'package:uuid/uuid.dart';

part 'message.g.dart';

@HiveType(typeId: 0)
class Message extends HiveObject {
  @HiveField(0)
  final String id;

  @HiveField(1)
  final String sessionId;

  @HiveField(2)
  final String role; // 'user' | 'assistant'

  @HiveField(3)
  final String content;

  @HiveField(4)
  final String? emotion;

  @HiveField(5)
  final String? motionType;

  @HiveField(6)
  final int timestamp;

  Message({
    String? id,
    required this.sessionId,
    required this.role,
    required this.content,
    this.emotion,
    this.motionType,
    int? timestamp,
  })  : id = id ?? const Uuid().v4(),
        timestamp = timestamp ?? DateTime.now().millisecondsSinceEpoch;

  bool get isUser => role == 'user';
  bool get isAssistant => role == 'assistant';

  DateTime get dateTime =>
      DateTime.fromMillisecondsSinceEpoch(timestamp);

  Map<String, dynamic> toJson() => {
        'id': id,
        'sessionId': sessionId,
        'role': role,
        'content': content,
        'emotion': emotion,
        'motionType': motionType,
        'timestamp': timestamp,
      };
}
