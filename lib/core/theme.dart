import 'package:flutter/material.dart';

class AppTheme {
  // Core palette
  static const Color background = Color(0xFF0A0A0F);
  static const Color surface = Color(0xFF12121A);
  static const Color surfaceVariant = Color(0xFF1A1A26);
  static const Color glassBg = Color(0x1AFFFFFF);
  static const Color glassBorder = Color(0x26FFFFFF);
  static const Color accentPrimary = Color(0xFF7C6BF8);
  static const Color accentSecondary = Color(0xFFFF6B9D);
  static const Color accentGlow = Color(0x557C6BF8);
  static const Color textPrimary = Color(0xFFF0F0FF);
  static const Color textSecondary = Color(0xFF8A8AAA);
  static const Color textMuted = Color(0xFF4A4A6A);
  static const Color userBubble = Color(0xFF2A2A3E);
  static const Color aiBubble = Color(0xFF1E1E30);
  static const Color error = Color(0xFFFF4466);
  static const Color success = Color(0xFF44FF88);

  static ThemeData get dark {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: background,
      colorScheme: const ColorScheme.dark(
        primary: accentPrimary,
        secondary: accentSecondary,
        surface: surface,
        error: error,
        onPrimary: textPrimary,
        onSurface: textPrimary,
      ),
      textTheme: _textTheme,
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: glassBg,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: glassBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: glassBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: accentPrimary, width: 1.5),
        ),
        hintStyle: const TextStyle(color: textMuted),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: accentPrimary,
          foregroundColor: textPrimary,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
        ),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        titleTextStyle: TextStyle(
          color: textPrimary,
          fontSize: 18,
          fontWeight: FontWeight.w600,
        ),
        iconTheme: IconThemeData(color: textSecondary),
      ),
    );
  }

  static const TextTheme _textTheme = TextTheme(
    headlineLarge: TextStyle(
      color: textPrimary,
      fontSize: 28,
      fontWeight: FontWeight.w700,
    ),
    headlineMedium: TextStyle(
      color: textPrimary,
      fontSize: 22,
      fontWeight: FontWeight.w600,
    ),
    titleLarge: TextStyle(
      color: textPrimary,
      fontSize: 18,
      fontWeight: FontWeight.w600,
    ),
    titleMedium: TextStyle(
      color: textPrimary,
      fontSize: 16,
      fontWeight: FontWeight.w500,
    ),
    bodyLarge: TextStyle(
      color: textPrimary,
      fontSize: 15,
      height: 1.6,
    ),
    bodyMedium: TextStyle(
      color: textSecondary,
      fontSize: 13,
      height: 1.5,
    ),
    labelLarge: TextStyle(
      color: textPrimary,
      fontSize: 14,
      fontWeight: FontWeight.w500,
    ),
  );

  // Glassmorphism decoration
  static BoxDecoration glassCard({
    BorderRadius? borderRadius,
    Color? borderColor,
    double blur = 20,
  }) {
    return BoxDecoration(
      color: glassBg,
      borderRadius: borderRadius ?? BorderRadius.circular(20),
      border: Border.all(
        color: borderColor ?? glassBorder,
        width: 1,
      ),
    );
  }

  // Glow effect
  static List<BoxShadow> glowShadow(Color color, {double intensity = 0.4}) {
    return [
      BoxShadow(
        color: color.withOpacity(intensity),
        blurRadius: 20,
        spreadRadius: 2,
      ),
      BoxShadow(
        color: color.withOpacity(intensity * 0.5),
        blurRadius: 40,
        spreadRadius: 4,
      ),
    ];
  }
}
