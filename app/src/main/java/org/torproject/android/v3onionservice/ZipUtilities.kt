package org.torproject.android.v3onionservice

import android.content.ContentResolver
import android.net.Uri

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipUtilities(
    private val files: Array<String>?,
    private val zipFile: Uri,
    private val contentResolver: ContentResolver
) {
    fun zip(): Boolean {
        return try {
            checkNotNull(contentResolver.openFileDescriptor(zipFile, "w")).use { pdf ->
                FileOutputStream(pdf.fileDescriptor).use { dest ->
                    ZipOutputStream(BufferedOutputStream(dest)).use { out ->
                        val data = ByteArray(BUFFER)
                        checkNotNull(files)
                        for (file in files) {
                            FileInputStream(file).use { fi ->
                                BufferedInputStream(fi, BUFFER).use { origin ->
                                    val entry = ZipEntry(file.substring(file.lastIndexOf("/") + 1))
                                    out.putNextEntry(entry)
                                    var count: Int
                                    while (origin.read(data, 0, BUFFER).also { count = it } != -1) {
                                        out.write(data, 0, count)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun unzip(outputPath: String): Boolean {
        return try {
            contentResolver.openInputStream(zipFile)?.use { inputStream ->
                val zis = ZipInputStream(BufferedInputStream(inputStream))
                extractFromZipInputStream(outputPath, zis)
            } ?: false
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun extractFromZipInputStream(outputPath: String, zis: ZipInputStream): Boolean {
        val outputDir = File(outputPath)
        return try {
            var ze: ZipEntry
            val buffer = ByteArray(1024)

            outputDir.mkdirs()

            while ((zis.nextEntry.also { ze = it }) != null) {
                val filename = ze.name

                if (!ONION_SERVICE_CONFIG_FILES.contains(filename)) { // *any* kind of foreign file
                    val writtenFiles = outputDir.listFiles()
                    if (writtenFiles != null) {
                        for (writtenFile in writtenFiles) {
                            writtenFile.delete()
                        }
                    }
                    outputDir.delete()
                    return false
                }

                // Need to create directories if not exists, or it will generate an Exception...
                if (ze.isDirectory) {
                    val fmd = File("$outputPath/$filename")
                    fmd.mkdirs()
                    continue
                }

                FileOutputStream("$outputPath/$filename").use { fout ->
                    var count: Int
                    while (zis.read(buffer).also { count = it } != -1) {
                        fout.write(buffer, 0, count)
                    }
                }

                zis.closeEntry()
            }

            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        } finally {
            zis.close()
        }
    }

    companion object {
        private const val BUFFER = 2048
        const val ZIP_MIME_TYPE: String = "application/zip"

        private val ONION_SERVICE_CONFIG_FILES: List<String> = mutableListOf(
            "config.json",
            "hostname",
            "hs_ed25519_public_key",
            "hs_ed25519_secret_key"
        )
    }
}
