package com.ridthewann.ubahvo.core

/**
 * Conservative guard for the bundled FFmpeg runtime.
 *
 * youtubedl-android 0.18.1 has a reported 16 KB-page compatibility problem in
 * libraries unpacked from its FFmpeg payload. Do not invoke this native runtime
 * on page sizes that have not been verified.
 */
object NativeMediaCompatibility {
    const val SUPPORTED_PAGE_SIZE_BYTES = 4096L

    fun supportsBundledFfmpeg(pageSizeBytes: Long?): Boolean =
        pageSizeBytes == SUPPORTED_PAGE_SIZE_BYTES

    fun userFacingMessage(pageSizeBytes: Long?): String {
        if (pageSizeBytes == null || pageSizeBytes <= 0L) {
            return "Konversi dinonaktifkan karena aplikasi tidak dapat memastikan kompatibilitas native perangkat ini."
        }

        val pageSizeKb = pageSizeBytes / 1024L
        return "Konversi dinonaktifkan pada perangkat dengan halaman memori ${pageSizeKb} KB. " +
            "Dependensi FFmpeg yang digunakan belum terverifikasi aman untuk ukuran ini. " +
            "Aplikasi melakukan ini untuk mencegah crash native; tunggu pembaruan kompatibilitas."
    }
}
