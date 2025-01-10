package com.kyhsgeekcode.minecraftenv

import org.lwjgl.openal.AL10
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.SOFTLoopback.alcLoopbackOpenDeviceSOFT
import org.lwjgl.openal.SOFTLoopback.alcRenderSamplesSOFT
import org.lwjgl.system.MemoryStack

class AudioCapturer {
    private var device: Long = 0
    private var loopbackDevice: Long = 0
    private var bufferId: Int = 0
    private var context: Long = 0
    private val tickLength = 50 // milliseconds
    private var numSamples = 0
    private lateinit var samples: ShortArray
    val enabled = checkCaptureAudio()

    fun checkCaptureAudio(): Boolean {
        // Check audio capture support
        // alcLoopbackOpenDeviceSOFT
        // alcRenderSamplesSOFT
        val defaultDeviceSpecifier = ALC10.alcGetString(0, ALC10.ALC_DEFAULT_DEVICE_SPECIFIER)
        println("Default device: $defaultDeviceSpecifier")
        MemoryStack.stackPush().use { stack ->
            // Open OpenAL Device
            device = ALC10.alcOpenDevice(defaultDeviceSpecifier)
            if (device == 0L) {
                println("Failed to open default device.")
                return
            }
            println("Opened device: $defaultDeviceSpecifier")

            // Create OpenAL context
            val attributes = stack.callocInt(1) // Empty attribute list
            context = ALC10.alcCreateContext(device, attributes)
            ALC10.alcMakeContextCurrent(context)
            println("Created and set OpenAL context.")

            // Check if ALC_SOFT_loopback is supported
            if (!ALC10.alcIsExtensionPresent(device, "ALC_SOFT_loopback")) {
                println("ALC_SOFT_loopback is not supported.")
                ALC10.alcDestroyContext(context)
                ALC10.alcCloseDevice(device)
                return
            }
            println("ALC_SOFT_loopback is supported.")

            // Open loopback device
            loopbackDevice = alcLoopbackOpenDeviceSOFT(null as CharSequence?)
            if (loopbackDevice == 0L) {
                println("Failed to open loopback device.")
                ALC10.alcDestroyContext(context)
                ALC10.alcCloseDevice(device)
                return
            }
            println("Opened loopback device.")
            val buffer = IntArray(1)
            AL10.alGenBuffers(buffer)
            bufferId = buffer[0]

            // TODO: Stereo or Mono?
            val format = AL10.AL_FORMAT_STEREO16 // 16-bit stereo
            val sampleRate = 44100 // 44.1 kHz
            numSamples = tickLength * sampleRate / 1000 // Number of samples to render
            samples = ShortArray(numSamples)
        }
    }

    fun captureAudio(): ShortArray {
        MemoryStack.stackPush().use { stack ->
            // Render samples from loopback device
            alcRenderSamplesSOFT(loopbackDevice, samples, numSamples)
            return samples
        }
    }

    fun close() {
        AL10.alDeleteBuffers(bufferId)
        ALC10.alcCloseDevice(device)
        ALC10.alcCloseDevice(loopbackDevice)
        ALC10.alcDestroyContext(context)
    }
}
