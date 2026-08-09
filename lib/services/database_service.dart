import 'package:hive_flutter/hive_flutter.dart';
import '../models/message.dart';
import '../models/session.dart';
import '../models/memory_fact.dart';
import '../core/constants.dart';

class DatabaseService {
  static final DatabaseService _instance = DatabaseService._internal();
  factory DatabaseService() => _instance;
  DatabaseService._internal();

  late Box<Message> _messagesBox;
  late Box<ChatSession> _sessionsBox;
  late Box<MemoryFact> _memoryBox;
  late Box<dynamic> _settingsBox;

  Future<void> initialize() async {
    await Hive.initFlutter();
    Hive.registerAdapter(MessageAdapter());
    Hive.registerAdapter(ChatSessionAdapter());
    Hive.registerAdapter(MemoryFactAdapter());

    _messagesBox = await Hive.openBox<Message>(AppConstants.hiveBoxSessions);
    _sessionsBox = await Hive.openBox<ChatSession>('chat_sessions');
    _memoryBox = await Hive.openBox<MemoryFact>(AppConstants.hiveBoxMemory);
    _settingsBox = await Hive.openBox<dynamic>(AppConstants.hiveBoxSettings);
  }

  // ── Sessions ──────────────────────────────────────────────────────────────

  Future<ChatSession> createSession({String? title}) async {
    final session = ChatSession(title: title);
    await _sessionsBox.put(session.id, session);
    return session;
  }

  Future<void> updateSessionTitle(String sessionId, String title) async {
    final session = _sessionsBox.get(sessionId);
    if (session != null) {
      session.title = title;
      await session.save();
    }
  }

  List<ChatSession> getAllSessions() {
    final sessions = _sessionsBox.values.toList();
    sessions.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    return sessions;
  }

  Future<void> deleteSession(String sessionId) async {
    await _sessionsBox.delete(sessionId);
    // Delete all messages for this session
    final toDelete = _messagesBox.values
        .where((m) => m.sessionId == sessionId)
        .map((m) => m.key)
        .toList();
    await _messagesBox.deleteAll(toDelete);
  }

  // ── Messages ──────────────────────────────────────────────────────────────

  Future<void> saveMessage(Message message) async {
    await _messagesBox.put(message.id, message);
    final session = _sessionsBox.get(message.sessionId);
    if (session != null) {
      session.touch();
      await session.save();
    }
  }

  List<Message> getMessagesForSession(String sessionId) {
    final msgs = _messagesBox.values
        .where((m) => m.sessionId == sessionId)
        .toList();
    msgs.sort((a, b) => a.timestamp.compareTo(b.timestamp));
    return msgs;
  }

  List<Message> getRecentMessages(String sessionId, {int limit = 20}) {
    final all = getMessagesForSession(sessionId);
    if (all.length <= limit) return all;
    return all.sublist(all.length - limit);
  }

  // ── Long-Term Memory ──────────────────────────────────────────────────────

  Future<void> saveFact(String fact) async {
    // Deduplicate: check for very similar existing fact (simple substring match)
    final existing = _memoryBox.values
        .where((f) =>
            f.fact.toLowerCase().contains(fact.toLowerCase().substring(
                  0,
                  (fact.length * 0.6).round().clamp(1, fact.length),
                )))
        .toList();

    if (existing.isNotEmpty) {
      existing.first.update(fact);
      await existing.first.save();
    } else {
      final memFact = MemoryFact(fact: fact);
      await _memoryBox.put(memFact.id, memFact);
    }
  }

  Future<void> saveFactsBatch(List<String> facts) async {
    for (final fact in facts) {
      if (fact.trim().isNotEmpty) {
        await saveFact(fact.trim());
      }
    }
  }

  List<MemoryFact> getAllFacts() {
    final facts = _memoryBox.values.toList();
    facts.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    return facts;
  }

  String buildMemoryContext() {
    final facts = getAllFacts();
    if (facts.isEmpty) return 'No facts stored yet.';
    return facts.map((f) => '- ${f.fact}').join('\n');
  }

  Future<void> deleteFact(String factId) async {
    await _memoryBox.delete(factId);
  }

  Future<void> clearAllMemory() async {
    await _memoryBox.clear();
  }

  // ── Settings ──────────────────────────────────────────────────────────────

  dynamic getSetting(String key, {dynamic defaultValue}) {
    return _settingsBox.get(key, defaultValue: defaultValue);
  }

  Future<void> setSetting(String key, dynamic value) async {
    await _settingsBox.put(key, value);
  }

  Future<void> clearAll() async {
    await _messagesBox.clear();
    await _sessionsBox.clear();
    await _memoryBox.clear();
  }
}
