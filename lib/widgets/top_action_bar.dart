import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/theme.dart';
import '../providers/companion_provider.dart';
import '../providers/settings_provider.dart';
import '../screens/settings_screen.dart';
import '../screens/memory_screen.dart';

class TopActionBar extends StatelessWidget {
  const TopActionBar({super.key});

  @override
  Widget build(BuildContext context) {
    final companion = context.watch<CompanionProvider>();
    final settings = context.watch<SettingsProvider>();

    return SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Row(
          children: [
            // Companion name / status
            Expanded(
              child: _StatusChip(
                name: settings.companionName,
                state: companion.state,
              ),
            ),

            const SizedBox(width: 8),

            // Memory Manager
            _TopBarButton(
              icon: Icons.psychology_outlined,
              tooltip: 'Memory',
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const MemoryScreen()),
              ),
            ),

            const SizedBox(width: 8),

            // Settings
            _TopBarButton(
              icon: Icons.tune_rounded,
              tooltip: 'Settings',
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const SettingsScreen()),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  final String name;
  final CompanionState state;

  const _StatusChip({required this.name, required this.state});

  String get _stateLabel {
    switch (state) {
      case CompanionState.listening:
        return 'Listening…';
      case CompanionState.thinking:
        return 'Thinking…';
      case CompanionState.speaking:
        return 'Speaking';
      case CompanionState.error:
        return 'Error';
      case CompanionState.idle:
        return 'Ready';
    }
  }

  Color get _stateColor {
    switch (state) {
      case CompanionState.listening:
        return AppTheme.accentSecondary;
      case CompanionState.thinking:
        return AppTheme.accentPrimary;
      case CompanionState.speaking:
        return AppTheme.success;
      case CompanionState.error:
        return AppTheme.error;
      case CompanionState.idle:
        return AppTheme.textMuted;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.black.withOpacity(0.72),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppTheme.glassBorder),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            width: 7,
            height: 7,
            decoration: BoxDecoration(
              color: _stateColor,
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(
                  color: _stateColor.withOpacity(0.6),
                  blurRadius: 6,
                  spreadRadius: 1,
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                name,
                style: const TextStyle(
                  color: AppTheme.textPrimary,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
              Text(
                _stateLabel,
                style: TextStyle(
                  color: _stateColor,
                  fontSize: 10,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _TopBarButton extends StatelessWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;

  const _TopBarButton({
    required this.icon,
    required this.tooltip,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            color: Colors.black.withOpacity(0.72),
            shape: BoxShape.circle,
            border: Border.all(color: AppTheme.glassBorder),
          ),
          child: Icon(icon, color: AppTheme.textSecondary, size: 20),
        ),
      ),
    );
  }
}
