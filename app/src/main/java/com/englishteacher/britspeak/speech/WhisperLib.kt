package com.englishteacher.britspeak.speech

/**
 * Thin JNI bridge to the bundled whisper.cpp native library (see `app/src/main/cpp`). A plain
 * Kotlin `object` so the JNI symbol names stay simple (no `$Companion`).
 */
object WhisperLib {
    @Volatile
    private var loaded = false

    /** Loads the native library once; returns false if it isn't present (so callers can degrade). */
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("whisper-jni")
            loaded = true
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** Initialises a model from a file path; returns a native pointer (0 on failure). */
    external fun initContext(modelPath: String): Long

    external fun freeContext(ptr: Long)

    /** Transcribes 16 kHz mono float samples ([-1,1]) to text using [numThreads] threads. */
    external fun transcribe(
        ptr: Long,
        numThreads: Int,
        samples: FloatArray,
    ): String
}
