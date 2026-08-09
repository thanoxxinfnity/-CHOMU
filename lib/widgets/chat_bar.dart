import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:provider/provider.dart';
import '../core/theme.dart';
import '../providers/chat_provider.dart';
import '../providers/companion_provider.dart';

class ChatBar extends StatefulWidget {
  const ChatBar({super.key});

  @override
  State<ChatBar> createState() => _ChatBarState();
}

class _ChatBarState extends State<ChatBar> {
  final TextEditingController _controller = TextEditingController();
  final FocusNode _focusNode = FocusNode();
  bool _hasText = false;

  @override
  void initState() {
    super.initState();
    _controller.addListener(() {
      setState(() => _hasText = _controller.text.trim().isNotEmpty);
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    final companion = context.watch<CompanionProvider>();
    final isListening = chat.isListening;
    final isLoading = chat.isLoading;
    final isSpeaking = companion.state == CompanionState.speaking;

    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(28),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
          child: Container(
            decoration: BoxDecoration(
              color: AppTheme.glassBg,
              borderRadius: BorderRadius.circular(28),
              border: Border.all(color: AppTheme.glassBorder),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.3),
                  blurRadius: 20,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
              child: Row(
                children: [
                  // Mic / Stop button
                  _MicButton(
                    isListening: isListening,
                    isLoading: isLoading,
                    onTap: () => _handleMicTap(chat),
                  ),
                  const SizedBox(width: 8),

                  // Text input
                  Expanded(
                    child: TextField(
                      controller: _controller,
                      focusNode: _focusNode,
                      enabled: !isLoading,
                      maxLines: 3,
                      minLines: 1,
                      textCapitalization: TextCapitalization.sentences,
                      style: const TextStyle(
                        color: AppTheme.textPrimary,
                        fontSize: 15,
                      ),
                      decoration: InputDecoration(
                        hintText: isListening
                            ? 'Listening…'
                            : isLoading
                                ? 'Waiting for reply…'
                                : 'Say something…',
                        hintStyle: TextStyle(
                          color: isListening
                              ? AppTheme.accentSecondary
                              : AppTheme.textMuted,
                        ),
                        border: InputBorder.none,
                        enabledBorder: InputBorder.none,
                        focusedBorder: InputBorder.none,
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 4,
                          vertical: 6,
                        ),
                        isDense: true,
                      ),
                      onSubmitted: (_) => _sendMessage(chat),
                    ),
                  ),

                  const SizedBox(width: 8),

                  // Send / Stop speaking button
                  if (isSpeaking)
                    _ActionButton(
                      icon: Icons.stop_rounded,
                      color: AppTheme.accentSecondary,
                      onTap: () => chat.stopSpeaking(),
                      tooltip: 'Stop',
                    )
                  else
                    _ActionButton(
                      icon: Icons.send_rounded,
                      color: _hasText && !isLoading
                          ? AppTheme.accentPrimary
                          : AppTheme.textMuted,
                      onTap: _hasText && !isLoading
                          ? () => _sendMessage(chat)
                          : null,
                      tooltip: 'Send',
                    ),
                ],
              ),
            ),
          ),
        ),
      ).animate().slideY(begin: 1, duration: 400.ms, curve: Curves.easeOutBack),
    );
  }

  void _sendMessage(ChatProvider chat) {
    final text = _controller.text.trim();
    if (text.isEmpty) return;
    _controller.clear();
    _focusNode.unfocus();
    chat.sendTextMessage(text);
  }

  Future<void> _handleMicTap(ChatProvider chat) async {
    if (chat.isListening) {
      await chat.stopListening(onTranscript: (transcript) {
        // For now, show a snackbar — production would use STT
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(transcript),
            action: SnackBarAction(
              label: 'Send',
              onPressed: () => chat.sendTextMessage(transcript),
            ),
          ),
        );
      });
    } else {
      final started = await chat.startListening();
      if (!started && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Microphone permission required.'),
          ),
        );
      }
    }
  }
}

class _MicButton extends StatelessWidget {
  final bool isListening;
  final bool isLoading;
  final VoidCallback onTap;

  const _MicButton({
    required this.isListening,
    required this.isLoading,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: isLoading ? null : onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        width: 44,
        height: 44,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: isListening
              ? AppTheme.accentSecondary.withOpacity(0.3)
              : AppTheme.glassBg,
          border: Border.all(
            color: isListening
                ? AppTheme.accentSecondary
                : AppTheme.glassBorder,
            width: 1.5,
          ),
          boxShadow: isListening
              ? AppTheme.glowShadow(AppTheme.accentSecondary, intensity: 0.3)
              : [],
        ),
        child: Icon(
          isListening ? Icons.mic : Icons.mic_none_rounded,
          color: isListening ? AppTheme.accentSecondary : AppTheme.textSecondary,
          size: 20,
        ),
      )
          .animate(target: isListening ? 1 : 0)
          .scale(
            begin: const Offset(1, 1),
            end: const Offset(1.1, 1.1),
            duration: 600.ms,
            curve: Curves.easeInOut,
          ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  final IconData icon;
  final Color color;
  final VoidCallback? onTap;
  final String tooltip;

  const _ActionButton({
    required this.icon,
    required this.color,
    required this.tooltip,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: onTap != null
                ? color.withOpacity(0.15)
                : Colors.transparent,
            border: Border.all(
              color: onTap != null ? color.withOpacity(0.4) : AppTheme.glassBorder,
            ),
          ),
          child: Icon(icon, color: color, size: 20),
        ),
      ),
    );
  }
}
