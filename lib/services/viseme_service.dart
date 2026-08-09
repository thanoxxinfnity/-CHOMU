import 'dart:async';
import 'dart:math' as math;

/// Maps audio amplitude → viseme blendshape weights in real-time.
/// Uses RMS-based mouth open/close with simple phoneme approximation.
class VisemeService {
  static const double _silenceThreshold = 0.01;
  static const double _maxAmplitude = 0.8;

  // Current viseme weights (0.0–1.0)
  Map<String, double> currentWeights = {
    'aa': 0.0,
    'ih': 0.0,
    'oh': 0.0,
    'ou': 0.0,
    'E': 0.0,
    'pp': 0.0,
    'FF': 0.0,
    'kk': 0.0,
    'SS': 0.0,
    'nn': 0.0,
    'RR': 0.0,
    'dd': 0.0,
    'th': 0.0,
    'ch': 0.0,
  };

  double _smoothedAmplitude = 0.0;
  double _mouthOpenTarget = 0.0;
  Timer? _decayTimer;
  int _frameCount = 0;

  final Function(Map<String, double> weights) onWeightsUpdate;

  VisemeService({required this.onWeightsUpdate});

  /// Feed raw audio amplitude (0.0–1.0) from the audio engine.
  void processAmplitude(double rmsAmplitude) {
    // Smooth the amplitude to avoid jitter
    _smoothedAmplitude =
        _smoothedAmplitude * 0.6 + rmsAmplitude.clamp(0.0, 1.0) * 0.4;

    _frameCount++;

    if (_smoothedAmplitude < _silenceThreshold) {
      _closeMouth();
      return;
    }

    // Normalize amplitude
    final norm = (_smoothedAmplitude / _maxAmplitude).clamp(0.0, 1.0);

    // Cycle through phoneme groups to simulate natural speech variation
    // In real implementation, use a proper phoneme detector
    final phase = (_frameCount * 0.3) % (2 * math.pi);
    final phase2 = (_frameCount * 0.17) % (2 * math.pi);

    _resetWeights();

    // Primary mouth open = amplitude-driven
    _mouthOpenTarget = norm;

    // Oscillate between vowel classes based on timing
    final vowelCycle = _frameCount % 5;
    switch (vowelCycle) {
      case 0:
      case 1:
        currentWeights['aa'] = norm * (0.6 + 0.4 * math.sin(phase).abs());
        break;
      case 2:
        currentWeights['oh'] = norm * (0.5 + 0.3 * math.cos(phase2).abs());
        break;
      case 3:
        currentWeights['ih'] = norm * 0.7;
        break;
      case 4:
        currentWeights['E'] = norm * 0.6;
        break;
    }

    // Add some lip movement variety at higher amplitudes
    if (norm > 0.5) {
      currentWeights['pp'] = (norm - 0.5) * 0.4 * math.sin(phase * 2).abs();
      currentWeights['FF'] = (norm - 0.5) * 0.2;
    }

    onWeightsUpdate(Map.from(currentWeights));
  }

  /// Process a specific phoneme directly (for phoneme-aware TTS).
  void processPhoneme(String phoneme, double weight) {
    _resetWeights();
    final viseme = _phonemeToViseme(phoneme);
    currentWeights[viseme] = weight.clamp(0.0, 1.0);
    onWeightsUpdate(Map.from(currentWeights));
  }

  void _closeMouth() {
    _resetWeights();
    onWeightsUpdate(Map.from(currentWeights));
  }

  void _resetWeights() {
    for (final key in currentWeights.keys) {
      currentWeights[key] = 0.0;
    }
  }

  String _phonemeToViseme(String phoneme) {
    const map = {
      'AA': 'aa', 'AE': 'aa', 'AH': 'aa', 'AO': 'oh',
      'AW': 'oh', 'AY': 'aa', 'B': 'pp', 'CH': 'ch',
      'D': 'dd', 'DH': 'th', 'EH': 'E', 'ER': 'RR',
      'EY': 'E', 'F': 'FF', 'G': 'kk', 'HH': 'aa',
      'IH': 'ih', 'IY': 'ih', 'JH': 'ch', 'K': 'kk',
      'L': 'nn', 'M': 'pp', 'N': 'nn', 'NG': 'nn',
      'OW': 'oh', 'OY': 'oh', 'P': 'pp', 'R': 'RR',
      'S': 'SS', 'SH': 'SS', 'T': 'dd', 'TH': 'th',
      'UH': 'oh', 'UW': 'ou', 'V': 'FF', 'W': 'ou',
      'Y': 'ih', 'Z': 'SS', 'ZH': 'SS',
    };
    return map[phoneme.toUpperCase()] ?? 'aa';
  }

  /// Generate a smooth blink event weight (0→1→0 over ~150ms)
  double computeBlinkWeight(int elapsedMs) {
    const blinkDurationMs = 150;
    final t = (elapsedMs / blinkDurationMs).clamp(0.0, 1.0);
    // Ease in-out blink curve
    return t < 0.5
        ? 4 * t * t * t
        : 1 - math.pow(-2 * t + 2, 3) / 2;
  }

  void dispose() {
    _decayTimer?.cancel();
  }
}
