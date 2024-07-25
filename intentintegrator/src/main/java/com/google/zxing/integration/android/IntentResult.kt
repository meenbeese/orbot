/*
 * Copyright 2009 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.integration.android

/**
 * Encapsulates the result of a barcode scan invoked through [IntentIntegrator].
 *
 * @author Sean Owen
 */
data class IntentResult(
    internal val contents: String? = null,
    internal val formatName: String? = null,
    internal val rawBytes: ByteArray? = null,
    internal val orientation: Int? = null,
    internal val errorCorrectionLevel: String? = null
) {
    /**
     * @return raw content of barcode
     */
    fun getContents(): String? {
        return contents
    }

    /**
     * @return name of format, like "QR_CODE", "UPC_A". See [BarcodeFormat] for more format names.
     */
    fun getFormatName(): String? {
        return formatName
    }

    /**
     * @return raw bytes of the barcode content, if applicable, or null otherwise
     */
    fun getRawBytes(): ByteArray? {
        return rawBytes
    }

    /**
     * @return rotation of the image, in degrees, which resulted in a successful scan. May be null.
     */
    fun getOrientation(): Int? {
        return orientation
    }

    /**
     * @return name of the error correction level used in the barcode, if applicable
     */
    fun getErrorCorrectionLevel(): String? {
        return errorCorrectionLevel
    }

    override fun toString(): String {
        val rawBytesLength = rawBytes?.size ?: 0
        return "Format: $formatName\n" +
                "Contents: $contents\n" +
                "Raw bytes: ($rawBytesLength bytes)\n" +
                "Orientation: $orientation\n" +
                "EC level: $errorCorrectionLevel\n"
    }
}
