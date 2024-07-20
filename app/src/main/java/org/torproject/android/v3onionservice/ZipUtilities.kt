package org.torproject.android.v3onionservice

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@SuppressLint("NewApi")
class ZipUtilities(
    private val files: Array<String>?,
    private val zipFile: Uri,
    private val contentResolver: ContentResolver
) {
    fun zip(): Boolean {
        return try {
            val zipFilePath = Paths.get(zipFile.path!!)
            Files.newOutputStream(zipFilePath).use { out ->
                ZipOutputStream(out).use { zipOut ->
                    checkNotNull(files)
                    for (filePath in files) {
                        val file = Paths.get(filePath)
                        val zipEntry = ZipEntry(file.fileName.toString())
                        zipOut.putNextEntry(zipEntry)
                        Files.copy(file, zipOut)
                        zipOut.closeEntry()
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
            val zipFilePath = Paths.get(zipFile.path!!)
            val outputDir = Paths.get(outputPath)
            Files.newInputStream(zipFilePath).use { inStream ->
                ZipInputStream(inStream).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val filePath = outputDir.resolve(entry.name)
                        if (!entry.isDirectory) {
                            Files.copy(zipIn, filePath)
                        } else {
                            Files.createDirectories(filePath)
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    companion object {
        const val ZIP_MIME_TYPE: String = "application/zip"

        private val ONION_SERVICE_CONFIG_FILES: List<String> = mutableListOf(
            "config.json",
            "hostname",
            "hs_ed25519_public_key",
            "hs_ed25519_secret_key"
        )
    }
}
