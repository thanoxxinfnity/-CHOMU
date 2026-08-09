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
// dart:ui only used for ImageFilter

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
            child: _SimpleIconButton(
              icon: Icons.menu_rounded,
              onTap: () => _scaffoldKey.currentState?.openDrawer(),
            ),
          ),

          // Camera toggle
          Positioned(
            top: 72,
            right: 16,
            child: _CameraToggle(
              onTap: () {
                final companion = context.read<CompanionProvider>();
                if (companion.cameraOpen) companion.closeCamera();
                else companion.openCamera();
              },
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
            child: _SimpleIconButton(
              icon: _showMessages ? Icons.chat_rounded : Icons.chat_bubble_outline_rounded,
              onTap: () => setState(() => _showMessages = !_showMessages),
              active: _showMessages,
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

class _SimpleIconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  final bool active;
  const _SimpleIconButton({required this.icon, required this.onTap, this.active = false});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 40, height: 40,
        decoration: BoxDecoration(
          color: active
              ? AppTheme.accentPrimary.withOpacity(0.25)
              : Colors.black.withOpacity(0.55),
          shape: BoxShape.circle,
          border: Border.all(
            color: active
                ? AppTheme.accentPrimary.withOpacity(0.5)
                : Colors.white.withOpacity(0.15),
          ),
        ),
        child: Icon(icon,
          color: active ? AppTheme.accentPrimary : Colors.white70,
          size: 18),
      ),
    );
  }
}

class _CameraToggle extends StatelessWidget {
  final VoidCallback onTap;
  const _CameraToggle({required this.onTap});

  @override
  Widget build(BuildContext context) {
    final companion = context.watch<CompanionProvider>();
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 40, height: 40,
        decoration: BoxDecoration(
          color: companion.cameraOpen
              ? Colors.redAccent.withOpacity(0.3)
              : Colors.black.withOpacity(0.55),
          shape: BoxShape.circle,
          border: Border.all(
            color: companion.cameraOpen
                ? Colors.redAccent.withOpacity(0.6)
                : Colors.white.withOpacity(0.15),
          ),
        ),
        child: Icon(
          companion.cameraOpen ? Icons.videocam_off_rounded : Icons.videocam_rounded,
          color: companion.cameraOpen ? Colors.redAccent : Colors.white70,
          size: 18,
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

    return Container(
      decoration: BoxDecoration(
        color: Colors.black.withOpacity(0.75),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white.withOpacity(0.1)),
      ),
      child: ListView.builder(
        padding: const EdgeInsets.all(12),
        reverse: true,
        itemCount: messages.length,
        itemBuilder: (ctx, i) {
          final msg = messages[messages.length - 1 - i];
          return _MessageBubble(message: msg);
        },
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
