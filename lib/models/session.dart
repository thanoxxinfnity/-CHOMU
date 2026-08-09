import 'package:hive/hive.dart';
import 'package:uuid/uuid.dart';

part 'session.g.dart';

@HiveType(typeId: 1)
class ChatSession extends HiveObject {
  @HiveField(0)
  final String id;

  @HiveField(1)
  String title;

  @HiveField(2)
  final int createdAt;

  @HiveField(3)
  int updatedAt;

  @HiveField(4)
  int messageCount;

  ChatSession({
    String? id,
    String? title,
    int? createdAt,
    int? updatedAt,
    this.messageCount = 0,
  })  : id = id ?? const Uuid().v4(),
        title = title ?? 'New Conversation',
        createdAt = createdAt ?? DateTime.now().millisecondsSinceEpoch,
        updatedAt = updatedAt ?? DateTime.now().millisecondsSinceEpoch;

  DateTime get createdDate =>
      DateTime.fromMillisecondsSinceEpoch(createdAt);

  DateTime get updatedDate =>
      DateTime.fromMillisecondsSinceEpoch(updatedAt);

  void touch() {
    updatedAt = DateTime.now().millisecondsSinceEpoch;
    messageCount++;
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'createdAt': createdAt,
        'updatedAt': updatedAt,
        'messageCount': messageCount,
      };
}
