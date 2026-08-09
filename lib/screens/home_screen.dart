import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../core/theme.dart';
import '../providers/chat_provider.dart';
import '../providers/companion_provider.dart';
import '../providers/settings_provider.dart';
import '../widgets/companion_viewer.dart';
import '../widgets/chat_bar.dart';
import '../widgets/top_action_bar.dart';
import '../widgets/memory_drawer.dart';
import 'dart:ui';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey();
  bool _showMessages = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarColor: Colors.transparent,
    ));

    WidgetsBinding.instance.addPostFrameCallback((_) {
      final companion = context.read<CompanionProvider>();
      companion.initialize();
      context.read<ChatProvider>().initialize(companion);
      context.read<SettingsProvider>().load();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      key: _scaffoldKey,
      backgroundColor: AppTheme.background,
      extendBody: true,
      extendBodyBehindAppBar: true,
      drawer: const SessionDrawer(),
      body: Stack(
        children: [
          // Full-screen 3D Canvas
          const Positioned.fill(
            child: CompanionViewer(),
          ),

          // Background gradient vignette
          Positioned.fill(
            child: IgnorePointer(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: RadialGradient(
                    center: const Alignment(0, -0.3),
                    radius: 1.2,
                    colors: [
                      Colors.transparent,
                      AppTheme.background.withOpacity(0.3),
                    ],
                  ),
                ),
              ),
            ),
          ),

          // Top action bar
          const Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: TopActionBar(),
          ),

          // Floating history toggle
          Positioned(
            top: 72,
            left: 16,
            child: _HistoryToggle(
              onTap: () => _scaffoldKey.currentState?.openDrawer(),
            ),
          ),

          // Messages overlay (when toggled)
          if (_showMessages)
            Positioned(
              left: 16,
              right: 16,
              bottom: 120,
              top: 130,
              child: _MessagesOverlay(),
            ),

          // Chat bubble toggle
          Positioned(
            bottom: 100,
            right: 16,
            child: _BubbleToggle(
              isShowing: _showMessages,
              onTap: () => setState(() => _showMessages = !_showMessages),
            ),
          ),

          // Chat bar — pinned bottom
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: const ChatBar(),
          ),
        ],
      ),
    );
  }
}

class _HistoryToggle extends StatelessWidget {
  final VoidCallback onTap;
  const _HistoryToggle({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: ClipOval(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
          child: Container(
            width: 38,
            height: 38,
            decoration: AppTheme.glassCard(borderRadius: BorderRadius.circular(19)),
            child: const Icon(
              Icons.menu_rounded,
              color: AppTheme.textSecondary,
              size: 18,
            ),
          ),
        ),
      ),
    );
  }
}

class _BubbleToggle extends StatelessWidget {
  final bool isShowing;
  final VoidCallback onTap;
  const _BubbleToggle({required this.isShowing, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: ClipOval(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: isShowing
                  ? AppTheme.accentPrimary.withOpacity(0.2)
                  : AppTheme.glassBg,
              shape: BoxShape.circle,
              border: Border.all(
                color: isShowing
                    ? AppTheme.accentPrimary.withOpacity(0.4)
                    : AppTheme.glassBorder,
              ),
            ),
            child: Icon(
              isShowing ? Icons.chat_rounded : Icons.chat_bubble_outline_rounded,
              color: isShowing ? AppTheme.accentPrimary : AppTheme.textSecondary,
              size: 18,
            ),
          ),
        ),
      ),
    );
  }
}

class _MessagesOverlay extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final chat = context.watch<ChatProvider>();
    final messages = chat.messages;

    if (messages.isEmpty) return const SizedBox.shrink();

    return ClipRRect(
      borderRadius: BorderRadius.circular(20),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
        child: Container(
          decoration: AppTheme.glassCard(),
          child: ListView.builder(
            padding: const EdgeInsets.all(12),
            reverse: true,
            itemCount: messages.length,
            itemBuilder: (ctx, i) {
              final msg = messages[messages.length - 1 - i];
              return _MessageBubble(message: msg);
            },
          ),
        ),
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  final dynamic message;
  const _MessageBubble({required this.message});

  @override
  Widget build(BuildContext context) {
    final isUser = message.isUser as bool;
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 3),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        constraints: BoxConstraints(
          maxWidth: MediaQuery.of(context).size.width * 0.72,
        ),
        decoration: BoxDecoration(
          color: isUser ? AppTheme.userBubble : AppTheme.aiBubble,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(16),
            topRight: const Radius.circular(16),
            bottomLeft: Radius.circular(isUser ? 16 : 4),
            bottomRight: Radius.circular(isUser ? 4 : 16),
          ),
          border: Border.all(
            color: isUser
                ? AppTheme.accentPrimary.withOpacity(0.2)
                : AppTheme.glassBorder,
          ),
        ),
        child: Text(
          message.content as String,
          style: const TextStyle(
            color: AppTheme.textPrimary,
            fontSize: 13,
            height: 1.5,
          ),
        ),
      ),
    );
  }
}
