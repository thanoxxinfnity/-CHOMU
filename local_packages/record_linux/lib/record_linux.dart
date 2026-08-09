import 'dart:async';
import 'dart:typed_data';
import 'package:record_platform_interface/record_platform_interface.dart';

class RecordLinux extends RecordPlatform {
  static void registerWith() {}

  @override
  Future<void> create(String recorderId) async {}

  @override
  Future<void> dispose(String recorderId) async {}

  @override
  Future<bool> hasPermission(String recorderId, {bool request = true}) async => false;

  @override
  Future<bool> isPaused(String recorderId) async => false;

  @override
  Future<bool> isRecording(String recorderId) async => false;

  @override
  Future<void> pause(String recorderId) async {}

  @override
  Future<void> resume(String recorderId) async {}

  @override
  Future<void> start(String recorderId, RecordConfig config,
      {required String path}) async {}

  @override
  Future<Stream<Uint8List>> startStream(
      String recorderId, RecordConfig config) async {
    return const Stream.empty();
  }

  @override
  Future<String?> stop(String recorderId) async => null;

  @override
  Future<Amplitude> getAmplitude(String recorderId) async =>
      Amplitude(current: -160, max: -160);

  @override
  Future<bool> isEncoderSupported(
      String recorderId, AudioEncoder encoder) async => false;

  @override
  Future<List<InputDevice>> listInputDevices(String recorderId) async => [];

  @override
  Future<void> cancel(String recorderId) async {}

  @override
  Stream<RecordState> onStateChanged(String recorderId) => const Stream.empty();
}
