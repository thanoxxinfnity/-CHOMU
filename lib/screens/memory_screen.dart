import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../core/theme.dart';
import '../models/memory_fact.dart';
import '../services/database_service.dart';

class MemoryScreen extends StatefulWidget {
  const MemoryScreen({super.key});

  @override
  State<MemoryScreen> createState() => _MemoryScreenState();
}

class _MemoryScreenState extends State<MemoryScreen> {
  final DatabaseService _db = DatabaseService();
  List<MemoryFact> _facts = [];

  @override
  void initState() {
    super.initState();
    _loadFacts();
  }

  void _loadFacts() {
    setState(() {
      _facts = _db.getAllFacts();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Long-Term Memory'),
        backgroundColor: AppTheme.surface,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_rounded),
          onPressed: () => Navigator.pop(context),
        ),
        actions: [
          if (_facts.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.delete_sweep_outlined, color: AppTheme.error),
              tooltip: 'Clear all',
              onPressed: _confirmClearAll,
            ),
        ],
      ),
      body: _facts.isEmpty
          ? _EmptyMemoryView()
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                _InfoHeader(count: _facts.length),
                const SizedBox(height: 16),
                ..._facts.map(
                  (f) => _FactCard(
                    fact: f,
                    onDelete: () => _deleteFact(f.id),
                    onEdit: (newText) => _editFact(f, newText),
                  ),
                ),
                const SizedBox(height: 80),
              ],
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _showAddFactDialog,
        backgroundColor: AppTheme.accentPrimary,
        foregroundColor: AppTheme.textPrimary,
        icon: const Icon(Icons.add),
        label: const Text('Add fact'),
      ),
    );
  }

  Future<void> _deleteFact(String id) async {
    await _db.deleteFact(id);
    _loadFacts();
  }

  Future<void> _editFact(MemoryFact fact, String newText) async {
    fact.update(newText);
    await fact.save();
    _loadFacts();
  }

  Future<void> _confirmClearAll() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surfaceVariant,
        title: const Text(
          'Clear All Memory?',
          style: TextStyle(color: AppTheme.textPrimary),
        ),
        content: const Text(
          'This will permanently delete all facts the AI has learned about you.',
          style: TextStyle(color: AppTheme.textSecondary),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text(
              'Clear',
              style: TextStyle(color: AppTheme.error),
            ),
          ),
        ],
      ),
    );

    if (confirm == true) {
      await _db.clearAllMemory();
      _loadFacts();
    }
  }

  Future<void> _showAddFactDialog() async {
    final ctrl = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surfaceVariant,
        title: const Text(
          'Add Memory Fact',
          style: TextStyle(color: AppTheme.textPrimary),
        ),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          style: const TextStyle(color: AppTheme.textPrimary),
          decoration: const InputDecoration(
            hintText: 'e.g. "User likes cats"',
          ),
          maxLines: 3,
          minLines: 1,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, ctrl.text.trim()),
            child: const Text('Add'),
          ),
        ],
      ),
    );

    if (result != null && result.isNotEmpty) {
      await _db.saveFact(result);
      _loadFacts();
    }
  }
}

class _InfoHeader extends StatelessWidget {
  final int count;
  const _InfoHeader({required this.count});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppTheme.accentPrimary.withOpacity(0.1),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppTheme.accentPrimary.withOpacity(0.2)),
      ),
      child: Row(
        children: [
          const Icon(Icons.psychology, color: AppTheme.accentPrimary, size: 22),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '$count fact${count != 1 ? 's' : ''} stored',
                  style: const TextStyle(
                    color: AppTheme.textPrimary,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const Text(
                  'These facts are included in every conversation',
                  style: TextStyle(
                    color: AppTheme.textSecondary,
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _FactCard extends StatelessWidget {
  final MemoryFact fact;
  final VoidCallback onDelete;
  final ValueChanged<String> onEdit;

  const _FactCard({
    required this.fact,
    required this.onDelete,
    required this.onEdit,
  });

  @override
  Widget build(BuildContext context) {
    final fmt = DateFormat('MMM d, y');
    return Dismissible(
      key: Key(fact.id),
      direction: DismissDirection.endToStart,
      onDismissed: (_) => onDelete(),
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 16),
        margin: const EdgeInsets.only(bottom: 10),
        decoration: BoxDecoration(
          color: AppTheme.error.withOpacity(0.15),
          borderRadius: BorderRadius.circular(14),
        ),
        child: const Icon(Icons.delete_outline, color: AppTheme.error),
      ),
      child: Container(
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.all(14),
        decoration: AppTheme.glassCard(borderRadius: BorderRadius.circular(14)),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 28,
              height: 28,
              decoration: BoxDecoration(
                color: AppTheme.accentPrimary.withOpacity(0.15),
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(
                Icons.lightbulb_outline,
                color: AppTheme.accentPrimary,
                size: 16,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    fact.fact,
                    style: const TextStyle(
                      color: AppTheme.textPrimary,
                      fontSize: 14,
                      height: 1.4,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    fmt.format(DateTime.fromMillisecondsSinceEpoch(fact.createdAt)),
                    style: const TextStyle(
                      color: AppTheme.textMuted,
                      fontSize: 11,
                    ),
                  ),
                ],
              ),
            ),
            IconButton(
              icon: const Icon(Icons.edit_outlined, size: 18),
              color: AppTheme.textMuted,
              onPressed: () => _showEditDialog(context),
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showEditDialog(BuildContext context) async {
    final ctrl = TextEditingController(text: fact.fact);
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surfaceVariant,
        title: const Text(
          'Edit Fact',
          style: TextStyle(color: AppTheme.textPrimary),
        ),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          style: const TextStyle(color: AppTheme.textPrimary),
          maxLines: 3,
          minLines: 1,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, ctrl.text.trim()),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    if (result != null && result.isNotEmpty) {
      onEdit(result);
    }
  }
}

class _EmptyMemoryView extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.psychology_outlined, color: AppTheme.textMuted, size: 56),
          SizedBox(height: 16),
          Text(
            'No memories yet',
            style: TextStyle(
              color: AppTheme.textPrimary,
              fontSize: 18,
              fontWeight: FontWeight.w600,
            ),
          ),
          SizedBox(height: 8),
          Text(
            'Facts the AI learns about you\nwill appear here',
            style: TextStyle(
              color: AppTheme.textMuted,
              fontSize: 13,
              height: 1.5,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}
