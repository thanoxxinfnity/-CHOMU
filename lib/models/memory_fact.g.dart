// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'memory_fact.dart';

// **************************************************************************
// TypeAdapterGenerator
// **************************************************************************

class MemoryFactAdapter extends TypeAdapter<MemoryFact> {
  @override
  final int typeId = 2;

  @override
  MemoryFact read(BinaryReader reader) {
    final numOfFields = reader.readByte();
    final fields = <int, dynamic>{
      for (int i = 0; i < numOfFields; i++) reader.readByte(): reader.read(),
    };
    return MemoryFact(
      id: fields[0] as String?,
      fact: fields[1] as String,
      createdAt: fields[2] as int?,
      updatedAt: fields[3] as int?,
      importance: fields[4] as int,
    );
  }

  @override
  void write(BinaryWriter writer, MemoryFact obj) {
    writer
      ..writeByte(5)
      ..writeByte(0)
      ..write(obj.id)
      ..writeByte(1)
      ..write(obj.fact)
      ..writeByte(2)
      ..write(obj.createdAt)
      ..writeByte(3)
      ..write(obj.updatedAt)
      ..writeByte(4)
      ..write(obj.importance);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is MemoryFactAdapter &&
          runtimeType == other.runtimeType &&
          typeId == other.typeId;
}
