// Generated by the protocol buffer compiler. DO NOT EDIT!
// NO CHECKED-IN PROTOBUF GENCODE
// source: observation_space.proto

// Generated files should ignore deprecation warnings
@file:Suppress("DEPRECATION")
package com.kyhsgeekcode.minecraft_env.proto;

@kotlin.jvm.JvmName("-initializeblockInfo")
public inline fun blockInfo(block: com.kyhsgeekcode.minecraft_env.proto.BlockInfoKt.Dsl.() -> kotlin.Unit): com.kyhsgeekcode.minecraft_env.proto.ObservationSpace.BlockInfo =
  com.kyhsgeekcode.minecraft_env.proto.BlockInfoKt.Dsl._create(com.kyhsgeekcode.minecraft_env.proto.ObservationSpace.BlockInfo.newBuilder()).apply { block() }._build()
/**
 * Protobuf type `BlockInfo`
 */
public object BlockInfoKt {
  @kotlin.OptIn(com.google.protobuf.kotlin.OnlyForUseByGeneratedProtoCode::class)
  @com.google.protobuf.kotlin.ProtoDslMarker
  public class Dsl private constructor(
    private val _builder: com.kyhsgeekcode.minecraft_env.proto.ObservationSpace.BlockInfo.Builder
  ) {
    public companion object {
      @kotlin.jvm.JvmSynthetic
      @kotlin.PublishedApi
      internal fun _create(builder: com.kyhsgeekcode.minecraft_env.proto.ObservationSpace.BlockInfo.Builder): Dsl = Dsl(builder)
    }

    @kotlin.jvm.JvmSynthetic
    @kotlin.PublishedApi
    internal fun _build(): com.kyhsgeekcode.minecraft_env.proto.ObservationSpace.BlockInfo = _builder.build()

    /**
     * `int32 x = 1;`
     */
    public var x: kotlin.Int
      @JvmName("getX")
      get() = _builder.getX()
      @JvmName("setX")
      set(value) {
        _builder.setX(value)
      }
    /**
     * `int32 x = 1;`
     */
    public fun clearX() {
      _builder.clearX()
    }

    /**
     * `int32 y = 2;`
     */
    public var y: kotlin.Int
      @JvmName("getY")
      get() = _builder.getY()
      @JvmName("setY")
      set(value) {
        _builder.setY(value)
      }
    /**
     * `int32 y = 2;`
     */
    public fun clearY() {
      _builder.clearY()
    }

    /**
     * `int32 z = 3;`
     */
    public var z: kotlin.Int
      @JvmName("getZ")
      get() = _builder.getZ()
      @JvmName("setZ")
      set(value) {
        _builder.setZ(value)
      }
    /**
     * `int32 z = 3;`
     */
    public fun clearZ() {
      _builder.clearZ()
    }

    /**
     * `string translation_key = 4;`
     */
    public var translationKey: kotlin.String
      @JvmName("getTranslationKey")
      get() = _builder.getTranslationKey()
      @JvmName("setTranslationKey")
      set(value) {
        _builder.setTranslationKey(value)
      }
    /**
     * `string translation_key = 4;`
     */
    public fun clearTranslationKey() {
      _builder.clearTranslationKey()
    }
  }
}
@kotlin.jvm.JvmSynthetic
public inline fun com.kyhsgeekcode.minecraft_env.proto.ObservationSpace.BlockInfo.copy(block: `com.kyhsgeekcode.minecraft_env.proto`.BlockInfoKt.Dsl.() -> kotlin.Unit): com.kyhsgeekcode.minecraft_env.proto.ObservationSpace.BlockInfo =
  `com.kyhsgeekcode.minecraft_env.proto`.BlockInfoKt.Dsl._create(this.toBuilder()).apply { block() }._build()
