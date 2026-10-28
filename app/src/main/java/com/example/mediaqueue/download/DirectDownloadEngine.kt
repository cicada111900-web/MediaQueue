package com.example.mediaqueue.download

import com.example.mediaqueue.data.DownloadItem
import com.example.mediaqueue.data.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class DirectDownloadEngine(
    private val outputDir: File
) {
    private val jobs = ConcurrentHashMap<String, Job>()

    fun start(
        item: DownloadItem,
        onProgress: (DownloadItem) -> Unit,
        onFinished: (DownloadItem) -> Unit
    ) {
        if (jobs.containsKey(item.id)) return

        val job = CoroutineScope(Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null

            try {
                outputDir.mkdirs()

                val fileName = safeFileName(item)
                val finalFile = File(outputDir, fileName)
                val partFile = File(outputDir, "$fileName.part")

                connection = (URL(item.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    instanceFollowRedirects = true
                    setRequestProperty(
                        "Range",
                        "bytes=${partFile.length()}-"
                    )
                }

                connection.connect()

                val code = connection.responseCode
                if (code !in 200..299) {
                    throw Exception("Server returned HTTP $code")
                }

                val canResume = code == HttpURLConnection.HTTP_PARTIAL
                val startAt = if (canResume) partFile.length() else 0L

                RandomAccessFile(partFile, "rw").use { output ->
                    if (!canResume) {
                        output.setLength(0)
                    }
                    output.seek(startAt)

                    val totalFromHeader = connection.getHeaderField("Content-Range")
                        ?.substringAfterLast("/")
                        ?.toLongOrNull()

                    val totalBytes = totalFromHeader
                        ?: if (connection.contentLengthLong > 0) {
                            startAt + connection.contentLengthLong
                        } else {
                            0L
                        }

                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        var downloaded = startAt

                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count == -1) break

                            output.write(buffer, 0, count)
                            downloaded += count

                            val progress = if (totalBytes > 0) {
                                downloaded.toFloat() / totalBytes
                            } else {
                                0f
                            }

                            onProgress(
                                item.copy(
                                    title = item.title ?: fileName,
                                    status = DownloadStatus.DOWNLOADING,
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes,
                                    progressPercent = progress.coerceIn(0f, 1f)
                                )
                            )
                        }
                    }
                }

                if (partFile.renameTo(finalFile)) {
                    onFinished(
                        item.copy(
                            title = item.title ?: fileName,
                            status = DownloadStatus.COMPLETED,
                            downloadedBytes = finalFile.length(),
                            totalBytes = finalFile.length(),
                            progressPercent = 1f,
                            errorMessage = null
                        )
                    )
                } else {
                    throw Exception("Could not finish saving the file")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFinished(
                    item.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.message ?: "Download failed"
                    )
                )
            } finally {
                connection?.disconnect()
                jobs.remove(item.id)
            }
        }

        jobs[item.id] = job
    }

    fun stop(id: String) {
        jobs[id]?.cancel()
    }

    private fun safeFileName(item: DownloadItem): String {
        val raw = item.url.substringBefore("?").substringAfterLast("/")
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.takeIf {
            it.isNotBlank() && it != "." && it != ".."
        } ?: "download_${item.id.take(8)}.bin"
    }
}
