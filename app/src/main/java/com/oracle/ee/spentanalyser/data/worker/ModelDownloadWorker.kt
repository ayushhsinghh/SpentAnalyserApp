package com.oracle.ee.spentanalyser.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.oracle.ee.spentanalyser.MainActivity
import com.oracle.ee.spentanalyser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import com.oracle.ee.spentanalyser.data.api.ModelApiService

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val KEY_URL = "URL"
        const val KEY_FILE_NAME = "FILE_NAME"
        const val KEY_PROGRESS = "PROGRESS"

        private const val DEFAULT_BUFFER_SIZE = 8192
        private const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "model_download_channel"
        private var channelCreated = false
    }

    init {
        if (!channelCreated) {
            val channel = NotificationChannel(
                FOREGROUND_NOTIFICATION_CHANNEL_ID,
                "Model Downloading",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Notifications for AI model downloading" }
            notificationManager.createNotificationChannel(channel)
            channelCreated = true
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val fileUrl = inputData.getString(KEY_URL)
        val fileName = inputData.getString(KEY_FILE_NAME)

        if (fileUrl.isNullOrEmpty() || fileName.isNullOrEmpty()) {
            return@withContext Result.failure()
        }

        try {
            setForeground(createForegroundInfo(0, fileName))

            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("X-Api-Key", ModelApiService.API_KEY)
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Timber.e("HTTP error code: ${connection.responseCode}")
                return@withContext Result.failure()
            }

            val fileLength = connection.contentLengthLong
            val finalOutputFile = File(applicationContext.filesDir, fileName)
            val tempOutputFile = File(applicationContext.filesDir, "$fileName.tmp")
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempOutputFile)

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastUpdateMs = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isStopped) {
                    outputStream.close()
                    inputStream.close()
                    tempOutputFile.delete()
                    return@withContext Result.failure()
                }

                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                val currentMs = System.currentTimeMillis()
                // Update progress every 500ms
                if (currentMs - lastUpdateMs > 500) {
                    val progress = if (fileLength > 0) {
                        ((totalBytesRead * 100) / fileLength).toInt()
                    } else {
                        0
                    }

                    setProgress(
                        Data.Builder()
                            .putInt(KEY_PROGRESS, progress)
                            .build()
                    )

                    setForeground(createForegroundInfo(progress, fileName))
                    lastUpdateMs = currentMs
                }
            }

            outputStream.close()
            inputStream.close()
            
            if (tempOutputFile.exists() && tempOutputFile.length() > 0) {
                if (tempOutputFile.renameTo(finalOutputFile)) {
                    Timber.d("Download Worker successfully completed downloading $fileName")
                    return@withContext Result.success()
                } else {
                    Timber.e("Failed to rename temporary file to $fileName")
                    tempOutputFile.delete()
                    return@withContext Result.failure()
                }
            } else {
                Timber.e("Downloaded file is empty")
                tempOutputFile.delete()
                return@withContext Result.failure()
            }
        } catch (e: IOException) {
            val fileName = inputData.getString(KEY_FILE_NAME)
            if (fileName != null) {
                val tempOutputFile = File(applicationContext.filesDir, "$fileName.tmp")
                if (tempOutputFile.exists()) {
                    tempOutputFile.delete()
                }
            }
            Timber.e(e, "Error downloading model")
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0, inputData.getString(KEY_FILE_NAME) ?: "Model")
    }

    private fun createForegroundInfo(progress: Int, fileName: String): ForegroundInfo {
        val title = "Downloading Intelligence"
        val content = if (progress > 0) "$progress% completed" else "Starting download..."

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, FOREGROUND_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .build()

        return ForegroundInfo(
            id.hashCode(),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}
