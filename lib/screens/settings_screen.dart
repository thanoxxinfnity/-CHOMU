import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/theme.dart';
import '../providers/settings_provider.dart';
import '../providers/companion_provider.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late TextEditingController _endpointCtrl;
  late TextEditingController _apiKeyCtrl;
  late TextEditingController _modelCtrl;
  late TextEditingController _nameCtrl;
  late TextEditingController _elKeyCtrl;
  late TextEditingController _elVoiceCtrl;

  @override
  void initState() {
    super.initState();
    final s = context.read<SettingsProvider>();
    _endpointCtrl = TextEditingController(text: s.apiEndpoint);
    _apiKeyCtrl = TextEditingController(text: s.apiKey);
    _modelCtrl = TextEditingController(text: s.modelName);
    _nameCtrl = TextEditingController(text: s.companionName);
    _elKeyCtrl = TextEditingController(text: s.elevenLabsKey);
    _elVoiceCtrl = TextEditingController(text: s.elevenLabsVoice);
  }

  @override
  void dispose() {
    _endpointCtrl.dispose();
    _apiKeyCtrl.dispose();
    _modelCtrl.dispose();
    _nameCtrl.dispose();
    _elKeyCtrl.dispose();
    _elVoiceCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsProvider>();

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Settings'),
        backgroundColor: AppTheme.surface,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_rounded),
          onPressed: () => Navigator.pop(context),
        ),
        actions: [
          TextButton(
            onPressed: _saveAll,
            child: const Text(
              'Save',
              style: TextStyle(color: AppTheme.accentPrimary),
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── Companion ──────────────────────────────────────────────────
          _SectionHeader('Companion'),
          _SettingCard(children: [
            _TextField(
              label: 'Name',
              controller: _nameCtrl,
              hint: 'Chomu',
            ),
            const SizedBox(height: 16),
            _ModelPickerRow(
              currentPath: settings.modelPath,
              isAsset: settings.modelIsAsset,
              onPick: _pickModelFile,
              onReset: _resetModel,
            ),
          ]),

          const SizedBox(height: 20),

          // ── LLM API ────────────────────────────────────────────────────
          _SectionHeader('LLM API'),
          _SettingCard(children: [
            _TextField(
              label: 'API Endpoint',
              controller: _endpointCtrl,
              hint: 'https://api.openai.com/v1',
              keyboardType: TextInputType.url,
            ),
            const SizedBox(height: 12),
            _TextField(
              label: 'API Key',
              controller: _apiKeyCtrl,
              hint: 'sk-…',
              obscure: true,
            ),
            const SizedBox(height: 12),
            _TextField(
              label: 'Model Name',
              controller: _modelCtrl,
              hint: 'gpt-4o-mini',
            ),
            const SizedBox(height: 12),
            _InfoBox(
              text: 'Supports OpenAI-compatible endpoints and Google Gemini.\n'
                  'Gemini: use https://generativelanguage.googleapis.com/v1beta',
            ),
          ]),

          const SizedBox(height: 20),

          // ── Text-to-Speech ─────────────────────────────────────────────
          _SectionHeader('Text-to-Speech'),
          _SettingCard(children: [
            _DropdownRow(
              label: 'TTS Engine',
              value: settings.ttsEngine,
              options: const ['device', 'elevenlabs'],
              labels: const ['Device (Free)', 'ElevenLabs API'],
              onChanged: (v) =>
                  settings.save(newTtsEngine: v),
            ),
            if (settings.ttsEngine == 'elevenlabs') ...[
              const SizedBox(height: 12),
              _TextField(
                label: 'ElevenLabs API Key',
                controller: _elKeyCtrl,
                hint: 'xi_…',
                obscure: true,
              ),
              const SizedBox(height: 12),
              _TextField(
                label: 'Voice ID',
                controller: _elVoiceCtrl,
                hint: 'EXAVITQu4vr4xnSDxMaL',
              ),
            ],
          ]),

          const SizedBox(height: 40),
        ],
      ),
    );
  }

  Future<void> _pickModelFile() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['vrm', 'glb', 'fbx'],
    );
    if (result != null && result.files.single.path != null) {
      final path = result.files.single.path!;
      await context.read<SettingsProvider>().save(
            newModelPath: path,
            newModelIsAsset: false,
          );
      if (mounted) {
        context.read<CompanionProvider>().loadModel(path, isAsset: false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Model loaded: ${path.split('/').last}')),
        );
      }
    }
  }

  Future<void> _resetModel() async {
    await context.read<SettingsProvider>().save(
          newModelPath: null,
          newModelIsAsset: true,
        );
    if (mounted) {
      context.read<CompanionProvider>().loadModel(
            'assets/models/default_companion.glb',
            isAsset: true,
          );
    }
  }

  Future<void> _saveAll() async {
    await context.read<SettingsProvider>().save(
          newEndpoint: _endpointCtrl.text.trim(),
          newApiKey: _apiKeyCtrl.text.trim(),
          newModelName: _modelCtrl.text.trim(),
          newCompanionName: _nameCtrl.text.trim(),
          newElevenLabsKey: _elKeyCtrl.text.trim(),
          newElevenLabsVoice: _elVoiceCtrl.text.trim(),
        );
    if (mounted) {
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Settings saved')),
      );
    }
  }
}

// ── Reusable Widgets ───────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader(this.title);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 0, 0, 10),
      child: Text(
        title.toUpperCase(),
        style: const TextStyle(
          color: AppTheme.accentPrimary,
          fontSize: 11,
          fontWeight: FontWeight.w700,
          letterSpacing: 1.2,
        ),
      ),
    );
  }
}

class _SettingCard extends StatelessWidget {
  final List<Widget> children;
  const _SettingCard({required this.children});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: AppTheme.glassCard(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: children,
      ),
    );
  }
}

class _TextField extends StatelessWidget {
  final String label;
  final TextEditingController controller;
  final String hint;
  final bool obscure;
  final TextInputType? keyboardType;

  const _TextField({
    required this.label,
    required this.controller,
    required this.hint,
    this.obscure = false,
    this.keyboardType,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            color: AppTheme.textSecondary,
            fontSize: 12,
            fontWeight: FontWeight.w500,
          ),
        ),
        const SizedBox(height: 6),
        TextField(
          controller: controller,
          obscureText: obscure,
          keyboardType: keyboardType,
          style: const TextStyle(color: AppTheme.textPrimary, fontSize: 14),
          decoration: InputDecoration(hintText: hint),
        ),
      ],
    );
  }
}

class _DropdownRow extends StatelessWidget {
  final String label;
  final String value;
  final List<String> options;
  final List<String> labels;
  final ValueChanged<String> onChanged;

  const _DropdownRow({
    required this.label,
    required this.value,
    required this.options,
    required this.labels,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(
          label,
          style: const TextStyle(
            color: AppTheme.textSecondary,
            fontSize: 13,
          ),
        ),
        const Spacer(),
        DropdownButton<String>(
          value: value,
          dropdownColor: AppTheme.surfaceVariant,
          style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13),
          underline: const SizedBox.shrink(),
          items: List.generate(
            options.length,
            (i) => DropdownMenuItem(
              value: options[i],
              child: Text(labels[i]),
            ),
          ),
          onChanged: (v) { if (v != null) onChanged(v); },
        ),
      ],
    );
  }
}

class _ModelPickerRow extends StatelessWidget {
  final String? currentPath;
  final bool isAsset;
  final VoidCallback onPick;
  final VoidCallback onReset;

  const _ModelPickerRow({
    required this.currentPath,
    required this.isAsset,
    required this.onPick,
    required this.onReset,
  });

  @override
  Widget build(BuildContext context) {
    final displayName = isAsset
        ? 'Default (built-in)'
        : currentPath?.split('/').last ?? 'None';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '3D Model File',
          style: TextStyle(
            color: AppTheme.textSecondary,
            fontSize: 12,
            fontWeight: FontWeight.w500,
          ),
        ),
        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: AppTheme.glassCard(borderRadius: BorderRadius.circular(12)),
          child: Row(
            children: [
              const Icon(Icons.view_in_ar, color: AppTheme.accentPrimary, size: 18),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  displayName,
                  style: const TextStyle(
                    color: AppTheme.textPrimary,
                    fontSize: 13,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 8),
              GestureDetector(
                onTap: onReset,
                child: const Icon(
                  Icons.restart_alt,
                  color: AppTheme.textMuted,
                  size: 18,
                ),
              ),
              const SizedBox(width: 8),
              ElevatedButton(
                onPressed: onPick,
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  minimumSize: Size.zero,
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  textStyle: const TextStyle(fontSize: 12),
                ),
                child: const Text('Browse'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        const Text(
          'Supported: .vrm, .glb, .fbx',
          style: TextStyle(color: AppTheme.textMuted, fontSize: 11),
        ),
      ],
    );
  }
}

class _InfoBox extends StatelessWidget {
  final String text;
  const _InfoBox({required this.text});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppTheme.accentPrimary.withOpacity(0.08),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppTheme.accentPrimary.withOpacity(0.2)),
      ),
      child: Text(
        text,
        style: const TextStyle(
          color: AppTheme.textSecondary,
          fontSize: 11,
          height: 1.5,
        ),
      ),
    );
  }
}
