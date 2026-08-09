import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/theme.dart';
import '../providers/settings_provider.dart';
import '../providers/companion_provider.dart';
import '../services/nvidia_service.dart';
import '../services/nvidia_voice_service.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabs;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Settings'),
        backgroundColor: AppTheme.surface,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_rounded),
          onPressed: () => Navigator.pop(context),
        ),
        bottom: TabBar(
          controller: _tabs,
          indicatorColor: AppTheme.accentPrimary,
          labelColor: AppTheme.accentPrimary,
          unselectedLabelColor: AppTheme.textSecondary,
          tabs: const [
            Tab(text: 'NVIDIA NIM'),
            Tab(text: 'Classic API'),
            Tab(text: 'Companion'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: const [
          _NvidiaTab(),
          _ClassicApiTab(),
          _CompanionTab(),
        ],
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════════
//  Tab 1 — NVIDIA NIM
// ═══════════════════════════════════════════════════════════════════

class _NvidiaTab extends StatefulWidget {
  const _NvidiaTab();

  @override
  State<_NvidiaTab> createState() => _NvidiaTabState();
}

class _NvidiaTabState extends State<_NvidiaTab> {
  late TextEditingController _keyCtrl;
  bool _testingKey = false;
  String? _testResult;

  @override
  void initState() {
    super.initState();
    final s = context.read<SettingsProvider>();
    _keyCtrl = TextEditingController(text: s.nvidiaApiKey);
  }

  @override
  void dispose() {
    _keyCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = context.watch<SettingsProvider>();
    final isActive = s.isNvidiaActive;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // ── Provider toggle ──────────────────────────────────────────────
        _ProviderToggle(
          isNvidia: isActive,
          onToggle: (v) => s.save(newActiveProvider: v ? 'nvidia' : 'openai'),
        ),

        const SizedBox(height: 20),

        // ── API Key ───────────────────────────────────────────────────────
        _SectionHeader('API Key'),
        _SettingCard(children: [
          _TextField(
            label: 'NVIDIA API Key  (nvapi-…)',
            controller: _keyCtrl,
            hint: 'nvapi-xxxxxxxxxxxxxxxxxxxxxxxx',
            obscure: true,
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: ElevatedButton(
                  onPressed: _testingKey ? null : _saveKey,
                  child: const Text('Save Key'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppTheme.accentSecondary,
                    side: const BorderSide(color: AppTheme.accentSecondary),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                  onPressed: _testingKey ? null : _testKey,
                  child: _testingKey
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Test'),
                ),
              ),
            ],
          ),
          if (_testResult != null) ...[
            const SizedBox(height: 10),
            _InfoBox(text: _testResult!, isError: _testResult!.startsWith('✗')),
          ],
        ]),

        const SizedBox(height: 20),

        // ── Model Selection ───────────────────────────────────────────────
        _SectionHeader('LLM Model'),
        _SettingCard(children: [
          _ModelSelector(
            currentModel: s.nvidiaModel,
            onSelected: (m) => s.save(newNvidiaModel: m),
          ),
          const SizedBox(height: 16),
          _SwitchRow(
            label: 'Live Streaming Mode',
            subtitle: 'Tokens appear as they generate (SSE)',
            value: s.nvidiaLiveMode,
            onChanged: (v) => s.save(newNvidiaLiveMode: v),
          ),
          const SizedBox(height: 8),
          _SwitchRow(
            label: 'Vision Mode',
            subtitle: 'Auto-switch to vision model for image input',
            value: s.nvidiaVisionEnabled,
            onChanged: (v) => s.save(newNvidiaVisionEnabled: v),
          ),
        ]),

        const SizedBox(height: 20),

        // ── NVIDIA Voice ──────────────────────────────────────────────────
        _SectionHeader('NVIDIA Voice (NIM TTS)'),
        _SettingCard(children: [
          _SwitchRow(
            label: 'Use NVIDIA TTS',
            subtitle: 'Voice synthesized by NVIDIA NIM',
            value: s.ttsEngine == 'nvidia',
            onChanged: (v) => s.save(newTtsEngine: v ? 'nvidia' : 'device'),
          ),
          const SizedBox(height: 16),
          _VoicePresetSelector(
            currentVoiceId: s.nvidiaVoiceId,
            onSelected: (id) => s.save(newNvidiaVoiceId: id),
          ),
        ]),

        const SizedBox(height: 20),

        // ── Voice Cloning ─────────────────────────────────────────────────
        _SectionHeader('Voice Cloning'),
        const _VoiceCloningCard(),

        const SizedBox(height: 40),
      ],
    );
  }

  Future<void> _saveKey() async {
    final key = _keyCtrl.text.trim();
    await context.read<SettingsProvider>().save(newNvidiaApiKey: key);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('NVIDIA API key saved')),
      );
    }
  }

  Future<void> _testKey() async {
    final key = _keyCtrl.text.trim();
    if (key.isEmpty) {
      setState(() => _testResult = '✗ No API key entered');
      return;
    }

    setState(() { _testingKey = true; _testResult = null; });

    await context.read<SettingsProvider>().save(newNvidiaApiKey: key);

    try {
      final nvidia = NvidiaService();
      final response = await nvidia.chat(
        messages: [
          {'role': 'user', 'content': 'Reply with exactly: {"dialogue":"ok","emotion":"happy","motion_type":"idle","extracted_facts":[]}'}
        ],
        maxTokens: 64,
      );
      setState(() {
        _testResult = response.hasError
            ? '✗ ${response.dialogue}'
            : '✓ Connected! Model responded correctly.';
        _testingKey = false;
      });
    } catch (e) {
      setState(() {
        _testResult = '✗ $e';
        _testingKey = false;
      });
    }
  }
}

// ── Provider Toggle ────────────────────────────────────────────────────────

class _ProviderToggle extends StatelessWidget {
  final bool isNvidia;
  final ValueChanged<bool> onToggle;
  const _ProviderToggle({required this.isNvidia, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: isNvidia
              ? [const Color(0xFF1A2F1A), const Color(0xFF0A1F0A)]
              : [AppTheme.surface, AppTheme.surfaceVariant],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isNvidia
              ? const Color(0xFF76B900).withOpacity(0.5)
              : AppTheme.glassBorder,
        ),
      ),
      child: Row(
        children: [
          // NVIDIA logo colour
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: const Color(0xFF76B900).withOpacity(0.15),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Center(
              child: Text('N',
                  style: TextStyle(
                    color: Color(0xFF76B900),
                    fontSize: 22,
                    fontWeight: FontWeight.w900,
                  )),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('NVIDIA NIM',
                    style: TextStyle(
                      color: AppTheme.textPrimary,
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    )),
                Text(
                  isNvidia ? 'Active provider' : 'Tap to activate',
                  style: TextStyle(
                    color:
                        isNvidia ? const Color(0xFF76B900) : AppTheme.textMuted,
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ),
          Switch(
            value: isNvidia,
            onChanged: onToggle,
            activeColor: const Color(0xFF76B900),
          ),
        ],
      ),
    );
  }
}

// ── Model Selector ─────────────────────────────────────────────────────────

class _ModelSelector extends StatelessWidget {
  final String currentModel;
  final ValueChanged<String> onSelected;
  const _ModelSelector({required this.currentModel, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Fast Models',
            style: TextStyle(
                color: AppTheme.accentPrimary, fontSize: 11, fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        ...NvidiaService.fastModels.entries
            .map((e) => _ModelTile(
                  modelId: e.key,
                  label: e.value,
                  isSelected: currentModel == e.key,
                  isVision: false,
                  onTap: () => onSelected(e.key),
                )),
        const SizedBox(height: 12),
        const Text('Vision Models',
            style: TextStyle(
                color: AppTheme.accentSecondary, fontSize: 11, fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        ...NvidiaService.visionModels.entries
            .map((e) => _ModelTile(
                  modelId: e.key,
                  label: e.value,
                  isSelected: currentModel == e.key,
                  isVision: true,
                  onTap: () => onSelected(e.key),
                )),
      ],
    );
  }
}

class _ModelTile extends StatelessWidget {
  final String modelId;
  final String label;
  final bool isSelected;
  final bool isVision;
  final VoidCallback onTap;
  const _ModelTile({
    required this.modelId,
    required this.label,
    required this.isSelected,
    required this.isVision,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final accent = isVision ? AppTheme.accentSecondary : AppTheme.accentPrimary;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        margin: const EdgeInsets.only(bottom: 6),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: isSelected ? accent.withOpacity(0.12) : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: isSelected ? accent.withOpacity(0.4) : AppTheme.glassBorder,
          ),
        ),
        child: Row(
          children: [
            Icon(
              isVision ? Icons.remove_red_eye_outlined : Icons.bolt_rounded,
              color: isSelected ? accent : AppTheme.textMuted,
              size: 16,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label,
                      style: TextStyle(
                        color: isSelected ? accent : AppTheme.textPrimary,
                        fontSize: 13,
                        fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                      )),
                  Text(modelId,
                      style: const TextStyle(
                          color: AppTheme.textMuted, fontSize: 10)),
                ],
              ),
            ),
            if (isSelected)
              Icon(Icons.check_circle_rounded, color: accent, size: 18),
          ],
        ),
      ),
    );
  }
}

// ── Voice Preset Selector ──────────────────────────────────────────────────

class _VoicePresetSelector extends StatelessWidget {
  final String currentVoiceId;
  final ValueChanged<String> onSelected;
  const _VoicePresetSelector(
      {required this.currentVoiceId, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Voice Preset',
            style: TextStyle(
                color: AppTheme.textSecondary,
                fontSize: 12,
                fontWeight: FontWeight.w500)),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: NvidiaVoiceService.builtinPresets.entries.map((e) {
            final isSelected = currentVoiceId == e.value.id;
            return GestureDetector(
              onTap: () => onSelected(e.value.id),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: isSelected
                      ? AppTheme.accentPrimary.withOpacity(0.15)
                      : AppTheme.glassBg,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: isSelected
                        ? AppTheme.accentPrimary.withOpacity(0.5)
                        : AppTheme.glassBorder,
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      e.value.gender == 'female'
                          ? Icons.face_2
                          : e.value.gender == 'male'
                              ? Icons.face
                              : Icons.face_3,
                      size: 14,
                      color: isSelected
                          ? AppTheme.accentPrimary
                          : AppTheme.textSecondary,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      e.key,
                      style: TextStyle(
                        fontSize: 12,
                        color: isSelected
                            ? AppTheme.accentPrimary
                            : AppTheme.textPrimary,
                        fontWeight: isSelected
                            ? FontWeight.w600
                            : FontWeight.w400,
                      ),
                    ),
                  ],
                ),
              ),
            );
          }).toList(),
        ),
      ],
    );
  }
}

// ── Voice Cloning Card ─────────────────────────────────────────────────────

class _VoiceCloningCard extends StatefulWidget {
  const _VoiceCloningCard();

  @override
  State<_VoiceCloningCard> createState() => _VoiceCloningCardState();
}

class _VoiceCloningCardState extends State<_VoiceCloningCard> {
  final NvidiaVoiceService _voiceService = NvidiaVoiceService();
  final TextEditingController _nameCtrl = TextEditingController();
  String? _selectedPath;
  bool _isCloning = false;
  String? _cloneResult;

  @override
  void initState() {
    super.initState();
    _nameCtrl.text = _voiceService.clonedVoiceName ?? '';
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final clonedName = _voiceService.clonedVoiceName;

    return _SettingCard(children: [
      // Active cloned voice banner
      if (clonedName != null) ...[
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppTheme.success.withOpacity(0.1),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: AppTheme.success.withOpacity(0.3)),
          ),
          child: Row(
            children: [
              const Icon(Icons.mic, color: AppTheme.success, size: 18),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Cloned Voice Active',
                        style: TextStyle(
                            color: AppTheme.success,
                            fontSize: 12,
                            fontWeight: FontWeight.w600)),
                    Text(clonedName,
                        style: const TextStyle(
                            color: AppTheme.textPrimary, fontSize: 13)),
                  ],
                ),
              ),
              GestureDetector(
                onTap: _clearClonedVoice,
                child: const Icon(Icons.delete_outline,
                    color: AppTheme.error, size: 18),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
      ],

      // Name field
      _TextField(
        label: 'Voice Name',
        controller: _nameCtrl,
        hint: 'e.g. My Voice',
      ),
      const SizedBox(height: 12),

      // File picker
      GestureDetector(
        onTap: _pickAudioFile,
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: AppTheme.glassCard(borderRadius: BorderRadius.circular(12)),
          child: Row(
            children: [
              const Icon(Icons.audio_file_outlined,
                  color: AppTheme.accentPrimary, size: 22),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _selectedPath != null
                          ? _selectedPath!.split('/').last
                          : 'Select reference audio',
                      style: TextStyle(
                        color: _selectedPath != null
                            ? AppTheme.textPrimary
                            : AppTheme.textMuted,
                        fontSize: 13,
                      ),
                    ),
                    const Text('10-30 sec clear speech, .wav or .mp3',
                        style: TextStyle(
                            color: AppTheme.textMuted, fontSize: 10)),
                  ],
                ),
              ),
              const Icon(Icons.folder_open_rounded,
                  color: AppTheme.textSecondary, size: 18),
            ],
          ),
        ),
      ),

      const SizedBox(height: 12),

      SizedBox(
        width: double.infinity,
        child: ElevatedButton.icon(
          icon: _isCloning
              ? const SizedBox(
                  width: 16, height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
              : const Icon(Icons.auto_awesome, size: 18),
          label: Text(_isCloning ? 'Cloning…' : 'Clone Voice'),
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFF76B900),
            padding: const EdgeInsets.symmetric(vertical: 14),
          ),
          onPressed: (_isCloning || _selectedPath == null) ? null : _cloneVoice,
        ),
      ),

      if (_cloneResult != null) ...[
        const SizedBox(height: 10),
        _InfoBox(
          text: _cloneResult!,
          isError: _cloneResult!.startsWith('✗'),
        ),
      ],
    ]);
  }

  Future<void> _pickAudioFile() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.audio,
    );
    if (result?.files.single.path != null) {
      setState(() => _selectedPath = result!.files.single.path);
    }
  }

  Future<void> _cloneVoice() async {
    final name = _nameCtrl.text.trim();
    if (name.isEmpty) {
      setState(() => _cloneResult = '✗ Enter a voice name first');
      return;
    }
    setState(() { _isCloning = true; _cloneResult = null; });

    final result = await _voiceService.cloneVoice(
      audioFilePath: _selectedPath!,
      voiceName: name,
    );

    if (mounted) {
      setState(() {
        _isCloning = false;
        _cloneResult = result.success
            ? '✓ Voice cloned! ID: ${result.voiceId}'
            : '✗ ${result.error}';
      });

      if (result.success) {
        await context.read<SettingsProvider>().save(
          newNvidiaClonedVoiceId: result.voiceId,
          newNvidiaClonedVoiceName: result.name,
        );
      }
    }
  }

  Future<void> _clearClonedVoice() async {
    await _voiceService.clearClonedVoice();
    if (mounted) {
      await context.read<SettingsProvider>().save(
        newNvidiaClonedVoiceId: '',
        newNvidiaClonedVoiceName: '',
      );
      setState(() {});
    }
  }
}

// ═══════════════════════════════════════════════════════════════════
//  Tab 2 — Classic API (OpenAI / Gemini)
// ═══════════════════════════════════════════════════════════════════

class _ClassicApiTab extends StatefulWidget {
  const _ClassicApiTab();
  @override
  State<_ClassicApiTab> createState() => _ClassicApiTabState();
}

class _ClassicApiTabState extends State<_ClassicApiTab> {
  late TextEditingController _endpointCtrl;
  late TextEditingController _keyCtrl;
  late TextEditingController _modelCtrl;
  late TextEditingController _elKeyCtrl;
  late TextEditingController _elVoiceCtrl;

  @override
  void initState() {
    super.initState();
    final s = context.read<SettingsProvider>();
    _endpointCtrl = TextEditingController(text: s.apiEndpoint);
    _keyCtrl = TextEditingController(text: s.apiKey);
    _modelCtrl = TextEditingController(text: s.modelName);
    _elKeyCtrl = TextEditingController(text: s.elevenLabsKey);
    _elVoiceCtrl = TextEditingController(text: s.elevenLabsVoice);
  }

  @override
  void dispose() {
    _endpointCtrl.dispose();
    _keyCtrl.dispose();
    _modelCtrl.dispose();
    _elKeyCtrl.dispose();
    _elVoiceCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = context.watch<SettingsProvider>();
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _SectionHeader('LLM Endpoint'),
        _SettingCard(children: [
          _TextField(label: 'API Endpoint', controller: _endpointCtrl,
              hint: 'https://api.openai.com/v1', keyboardType: TextInputType.url),
          const SizedBox(height: 12),
          _TextField(label: 'API Key', controller: _keyCtrl, hint: 'sk-…', obscure: true),
          const SizedBox(height: 12),
          _TextField(label: 'Model Name', controller: _modelCtrl, hint: 'gpt-4o-mini'),
          const SizedBox(height: 12),
          const _InfoBox(text:
            'Supports OpenAI-compatible + Google Gemini.\n'
            'Gemini: https://generativelanguage.googleapis.com/v1beta'),
        ]),
        const SizedBox(height: 20),
        _SectionHeader('Text-to-Speech'),
        _SettingCard(children: [
          _DropdownRow(
            label: 'TTS Engine',
            value: s.ttsEngine == 'nvidia' ? 'device' : s.ttsEngine,
            options: const ['device', 'elevenlabs'],
            labels: const ['Device (Free)', 'ElevenLabs API'],
            onChanged: (v) => s.save(newTtsEngine: v),
          ),
          if (s.ttsEngine == 'elevenlabs') ...[
            const SizedBox(height: 12),
            _TextField(label: 'ElevenLabs Key', controller: _elKeyCtrl, hint: 'xi_…', obscure: true),
            const SizedBox(height: 12),
            _TextField(label: 'Voice ID', controller: _elVoiceCtrl, hint: 'EXAVITQu4vr4xnSDxMaL'),
          ],
        ]),
        const SizedBox(height: 20),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: _saveAll,
            child: const Text('Save'),
          ),
        ),
        const SizedBox(height: 40),
      ],
    );
  }

  Future<void> _saveAll() async {
    await context.read<SettingsProvider>().save(
      newEndpoint: _endpointCtrl.text.trim(),
      newApiKey: _keyCtrl.text.trim(),
      newModelName: _modelCtrl.text.trim(),
      newElevenLabsKey: _elKeyCtrl.text.trim(),
      newElevenLabsVoice: _elVoiceCtrl.text.trim(),
    );
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Saved')));
    }
  }
}

// ═══════════════════════════════════════════════════════════════════
//  Tab 3 — Companion (name + 3D model)
// ═══════════════════════════════════════════════════════════════════

class _CompanionTab extends StatefulWidget {
  const _CompanionTab();
  @override
  State<_CompanionTab> createState() => _CompanionTabState();
}

class _CompanionTabState extends State<_CompanionTab> {
  late TextEditingController _nameCtrl;

  @override
  void initState() {
    super.initState();
    _nameCtrl = TextEditingController(
        text: context.read<SettingsProvider>().companionName);
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = context.watch<SettingsProvider>();
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _SectionHeader('Identity'),
        _SettingCard(children: [
          _TextField(label: 'Companion Name', controller: _nameCtrl, hint: 'Chomu'),
          const SizedBox(height: 12),
          ElevatedButton(
            onPressed: () async {
              await s.save(newCompanionName: _nameCtrl.text.trim());
              if (mounted) ScaffoldMessenger.of(context)
                .showSnackBar(const SnackBar(content: Text('Name saved')));
            },
            child: const Text('Save Name'),
          ),
        ]),
        const SizedBox(height: 20),
        _SectionHeader('3D Model'),
        _SettingCard(children: [
          _ModelPickerRow(
            currentPath: s.modelPath,
            isAsset: s.modelIsAsset,
            onPick: () => _pickModelFile(s),
            onReset: () => _resetModel(s),
          ),
        ]),
        const SizedBox(height: 40),
      ],
    );
  }

  Future<void> _pickModelFile(SettingsProvider s) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['vrm', 'glb', 'fbx'],
    );
    if (result?.files.single.path != null) {
      final path = result!.files.single.path!;
      await s.save(newModelPath: path, newModelIsAsset: false);
      if (mounted) {
        context.read<CompanionProvider>().loadModel(path, isAsset: false);
      }
    }
  }

  Future<void> _resetModel(SettingsProvider s) async {
    await s.save(newModelPath: null, newModelIsAsset: true);
    if (mounted) {
      context.read<CompanionProvider>().loadModel(
            'assets/models/default_companion.glb',
            isAsset: true,
          );
    }
  }
}

// ═══════════════════════════════════════════════════════════════════
//  Shared UI helpers
// ═══════════════════════════════════════════════════════════════════

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader(this.title);
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(4, 0, 0, 10),
    child: Text(title.toUpperCase(),
        style: const TextStyle(
            color: AppTheme.accentPrimary,
            fontSize: 11,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.2)),
  );
}

class _SettingCard extends StatelessWidget {
  final List<Widget> children;
  const _SettingCard({required this.children});
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(16),
    decoration: AppTheme.glassCard(),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: children),
  );
}

class _TextField extends StatelessWidget {
  final String label;
  final TextEditingController controller;
  final String hint;
  final bool obscure;
  final TextInputType? keyboardType;
  const _TextField({
    required this.label, required this.controller,
    required this.hint, this.obscure = false, this.keyboardType,
  });
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(label, style: const TextStyle(color: AppTheme.textSecondary, fontSize: 12, fontWeight: FontWeight.w500)),
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

class _SwitchRow extends StatelessWidget {
  final String label;
  final String subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;
  const _SwitchRow({
    required this.label, required this.subtitle,
    required this.value, required this.onChanged,
  });
  @override
  Widget build(BuildContext context) => Row(
    children: [
      Expanded(child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13, fontWeight: FontWeight.w500)),
          Text(subtitle, style: const TextStyle(color: AppTheme.textMuted, fontSize: 11)),
        ],
      )),
      Switch(value: value, onChanged: onChanged, activeColor: AppTheme.accentPrimary),
    ],
  );
}

class _DropdownRow extends StatelessWidget {
  final String label;
  final String value;
  final List<String> options;
  final List<String> labels;
  final ValueChanged<String> onChanged;
  const _DropdownRow({
    required this.label, required this.value,
    required this.options, required this.labels, required this.onChanged,
  });
  @override
  Widget build(BuildContext context) => Row(
    children: [
      Text(label, style: const TextStyle(color: AppTheme.textSecondary, fontSize: 13)),
      const Spacer(),
      DropdownButton<String>(
        value: value,
        dropdownColor: AppTheme.surfaceVariant,
        style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13),
        underline: const SizedBox.shrink(),
        items: List.generate(options.length,
          (i) => DropdownMenuItem(value: options[i], child: Text(labels[i]))),
        onChanged: (v) { if (v != null) onChanged(v); },
      ),
    ],
  );
}

class _InfoBox extends StatelessWidget {
  final String text;
  final bool isError;
  const _InfoBox({required this.text, this.isError = false});
  @override
  Widget build(BuildContext context) {
    final color = isError ? AppTheme.error : AppTheme.accentPrimary;
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: color.withOpacity(0.08),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withOpacity(0.2)),
      ),
      child: Text(text, style: TextStyle(color: isError ? AppTheme.error : AppTheme.textSecondary, fontSize: 11, height: 1.5)),
    );
  }
}

class _ModelPickerRow extends StatelessWidget {
  final String? currentPath;
  final bool isAsset;
  final VoidCallback onPick;
  final VoidCallback onReset;
  const _ModelPickerRow({
    required this.currentPath, required this.isAsset,
    required this.onPick, required this.onReset,
  });
  @override
  Widget build(BuildContext context) {
    final displayName = isAsset
        ? 'Default (built-in)'
        : currentPath?.split('/').last ?? 'None';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('3D Model File', style: TextStyle(color: AppTheme.textSecondary, fontSize: 12, fontWeight: FontWeight.w500)),
        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: AppTheme.glassCard(borderRadius: BorderRadius.circular(12)),
          child: Row(
            children: [
              const Icon(Icons.view_in_ar, color: AppTheme.accentPrimary, size: 18),
              const SizedBox(width: 10),
              Expanded(child: Text(displayName, style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13), overflow: TextOverflow.ellipsis)),
              const SizedBox(width: 8),
              GestureDetector(onTap: onReset, child: const Icon(Icons.restart_alt, color: AppTheme.textMuted, size: 18)),
              const SizedBox(width: 8),
              ElevatedButton(
                onPressed: onPick,
                style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8), minimumSize: Size.zero, tapTargetSize: MaterialTapTargetSize.shrinkWrap, textStyle: const TextStyle(fontSize: 12)),
                child: const Text('Browse'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 6),
        const Text('Supported: .vrm, .glb, .fbx', style: TextStyle(color: AppTheme.textMuted, fontSize: 11)),
      ],
    );
  }
}
