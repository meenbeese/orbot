package org.torproject.android.core

/**
 * Encapsulates the result of a barcode scan invoked through [IntentIntegrator].
 *
 * @author Sean Owen
 */
data class IntentResult(
    val contents: String? = null,
    val formatName: String? = null,
    val rawBytes: ByteArray? = null,
    val orientation: Int? = null,
    val errorCorrectionLevel: String? = null
) {
    override fun toString(): String {
        val rawBytesLength = rawBytes?.size ?: 0
        return "Format: $formatName\n" +
                "Contents: $contents\n" +
                "Raw bytes: ($rawBytesLength bytes)\n" +
                "Orientation: $orientation\n" +
                "EC level: $errorCorrectionLevel\n"
    }
}
