import 'package:hive/hive.dart';
import 'package:uuid/uuid.dart';

part 'memory_fact.g.dart';

@HiveType(typeId: 2)
class MemoryFact extends HiveObject {
  @HiveField(0)
  final String id;

  @HiveField(1)
  String fact;

  @HiveField(2)
  final int createdAt;

  @HiveField(3)
  int updatedAt;

  @HiveField(4)
  int importance; // 1-5

  MemoryFact({
    String? id,
    required this.fact,
    int? createdAt,
    int? updatedAt,
    this.importance = 3,
  })  : id = id ?? const Uuid().v4(),
        createdAt = createdAt ?? DateTime.now().millisecondsSinceEpoch,
        updatedAt = updatedAt ?? DateTime.now().millisecondsSinceEpoch;

  void update(String newFact) {
    fact = newFact;
    updatedAt = DateTime.now().millisecondsSinceEpoch;
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'fact': fact,
        'createdAt': createdAt,
        'updatedAt': updatedAt,
        'importance': importance,
      };
}
