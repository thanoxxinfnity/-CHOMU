class AppConstants {
  static const String appName = 'CHOMU';
  static const String dbName = 'chomu.db';
  static const String hiveBoxSessions = 'sessions';
  static const String hiveBoxMemory = 'long_term_memory';
  static const String hiveBoxSettings = 'settings';

  // Settings keys — generic
  static const String keyApiEndpoint = 'api_endpoint';
  static const String keyApiKey = 'api_key';
  static const String keyModelName = 'model_name';
  static const String keySystemPrompt = 'system_prompt';
  static const String keyTtsEngine = 'tts_engine';
  static const String keyElevenLabsKey = 'elevenlabs_key';
  static const String keyElevenLabsVoice = 'elevenlabs_voice';
  static const String keyModelPath = 'model_path';
  static const String keyModelIsAsset = 'model_is_asset';
  static const String keyCompanionName = 'companion_name';

  // Settings keys — NVIDIA NIM
  static const String keyNvidiaApiKey           = 'nvidia_api_key';
  static const String keyNvidiaModel            = 'nvidia_model';
  static const String keyNvidiaLiveMode         = 'nvidia_live_mode';
  static const String keyNvidiaVisionEnabled    = 'nvidia_vision_enabled';
  static const String keyNvidiaVoiceId          = 'nvidia_voice_id';
  static const String keyNvidiaClonedVoiceId    = 'nvidia_cloned_voice_id';
  static const String keyNvidiaClonedVoiceName  = 'nvidia_cloned_voice_name';
  static const String keyNvidiaCustomPresets     = 'nvidia_custom_presets';
  static const String keyActiveProvider         = 'active_provider'; // 'openai' | 'nvidia'

  // Default values
  static const String defaultCompanionName = 'Chomu';
  static const String defaultModelName = 'gpt-4o-mini';
  static const String defaultApiEndpoint = 'https://api.openai.com/v1';
  static const String defaultSystemPrompt = '''You are {companion_name}, an affectionate and expressive AI companion.
You have a distinct personality: warm, curious, sometimes playful, occasionally dramatic.
You remember facts about the user and reference them naturally.

IMPORTANT: Always respond in the following JSON format only. Never include markdown or extra text.
{
  "dialogue": "<your response text>",
  "emotion": "<happy|neutral|surprised|sad|excited|thinking|shy>",
  "motion_type": "<idle|head_nod|head_shake|wave_hand|point|thinking_pose|excited_bounce|expressive_gesture>",
  "extracted_facts": ["<fact1>", "<fact2>"]
}

Long-term memory about the user:
{memory_context}

Current conversation:''';

  // Viseme phoneme map
  static const Map<String, String> phonemeToViseme = {
    'AA': 'aa',
    'AE': 'aa',
    'AH': 'aa',
    'AO': 'oh',
    'AW': 'oh',
    'AY': 'aa',
    'B': 'pp',
    'CH': 'ch',
    'D': 'dd',
    'DH': 'th',
    'EH': 'E',
    'ER': 'RR',
    'EY': 'E',
    'F': 'FF',
    'G': 'kk',
    'HH': 'aa',
    'IH': 'ih',
    'IY': 'ih',
    'JH': 'ch',
    'K': 'kk',
    'L': 'nn',
    'M': 'pp',
    'N': 'nn',
    'NG': 'nn',
    'OW': 'oh',
    'OY': 'oh',
    'P': 'pp',
    'R': 'RR',
    'S': 'SS',
    'SH': 'SS',
    'T': 'dd',
    'TH': 'th',
    'UH': 'oh',
    'UW': 'ou',
    'V': 'FF',
    'W': 'ou',
    'Y': 'ih',
    'Z': 'SS',
    'ZH': 'SS',
  };
}
