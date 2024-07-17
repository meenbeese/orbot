package org.torproject.android.ui.v3onionservice

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipUtilities(
    private val files: Array<String>?,
    private val zipFile: Uri,
    private val contentResolver: ContentResolver
) {
    fun zip(): Boolean {
        try {
            var origin: BufferedInputStream
            val pdf = checkNotNull(
                contentResolver.openFileDescriptor(
                    zipFile, "w"
                )
            )
            val dest = FileOutputStream(pdf.fileDescriptor)
            val out = ZipOutputStream(BufferedOutputStream(dest))
            val data = ByteArray(BUFFER)
            checkNotNull(files)
            for (file in files) {
                val fi = FileInputStream(file)
                origin = BufferedInputStream(fi, BUFFER)
                val entry = ZipEntry(file.substring(file.lastIndexOf("/") + 1))
                out.putNextEntry(entry)
                var count: Int
                while ((origin.read(data, 0, BUFFER).also { count = it }) != -1) {
                    out.write(data, 0, count)
                }
                origin.close()
            }
            out.close()
            dest.close()
            pdf.close()
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun unzipLegacy(outputPath: String, zipFile: File?): Boolean {
        try {
            val fis = FileInputStream((zipFile))
            val zis = ZipInputStream(BufferedInputStream(fis))
            val returnVal = extractFromZipInputStream(outputPath, zis)
            fis.close()
            return returnVal
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    fun unzip(outputPath: String): Boolean {
        val `is`: InputStream?
        try {
            `is` = contentResolver.openInputStream(zipFile)
            val zis = ZipInputStream(BufferedInputStream(`is`))
            val returnVal = extractFromZipInputStream(outputPath, zis)
            checkNotNull(`is`)
            `is`.close()
            return returnVal
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    private fun extractFromZipInputStream(outputPath: String, zis: ZipInputStream): Boolean {
        val outputDir = File(outputPath)
        try {
            var ze: ZipEntry
            val buffer = ByteArray(1024)
            var count: Int

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

                val fout = FileOutputStream("$outputPath/$filename")

                while ((zis.read(buffer).also { count = it }) != -1) {
                    fout.write(buffer, 0, count)
                }

                fout.close()
                zis.closeEntry()
            }

            zis.close()
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
        return true
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