package com.ridthewann.ubahvo.core

/**
 * Optional native URL preflight. The C++ JNI adapter delegates parsing to the small Rust core.
 * A null result from [tryNormalize] means the native library could not be used and the JVM parser
 * should be used instead. A non-null result with a null URL means the native parser rejected input.
 */
internal object NativeUrlCore {
    data class Result(val normalizedUrl: String?)

    private const val MAX_URL_LENGTH = 4096

    private val libraryAvailable: Boolean = try {
        System.loadLibrary("ubahvo_native")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: SecurityException) {
        false
    }

    fun tryNormalize(value: String): Result? {
        // JNI GetStringUTFChars uses modified UTF-8. Keep non-ASCII text on java.net.URI, whose
        // toASCIIString() behavior is well-defined for internationalized query parameters.
        if (!libraryAvailable || value.length > MAX_URL_LENGTH ||
            value.any { it.code !in 0x20..0x7E }
        ) {
            return null
        }

        return try {
            Result(normalizeNative(value))
        } catch (_: LinkageError) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private external fun normalizeNative(value: String): String?
}
