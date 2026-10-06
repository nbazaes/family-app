package com.familyapp.core.update

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdateService(
    private val repoOwner: String = "nbazaes",
    private val repoName: String = "family-app"
) {
    companion object {
        private const val TAG = "UpdateService"
    }

    private val gson = Gson()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Checks if a newer release is available on GitHub.
     * Returns [AppUpdateInfo] if a newer version with an APK asset exists, or null otherwise.
     */
    suspend fun checkLatestRelease(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "FamilyApp-Android")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub releases API returned error code: ${response.code}")
                    return@withContext null
                }

                val bodyString = response.body?.string() ?: return@withContext null
                val release = gson.fromJson(bodyString, GitHubReleaseDto::class.java)

                if (!SemVer.isNewer(release.tagName, currentVersion)) {
                    Log.d(TAG, "Current version ($currentVersion) is up-to-date with remote (${release.tagName})")
                    return@withContext null
                }

                // Find APK asset
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                if (apkAsset == null) {
                    Log.w(TAG, "Release ${release.tagName} found but has no .apk asset")
                    return@withContext null
                }

                return@withContext AppUpdateInfo(
                    versionName = release.tagName.removePrefix("v").removePrefix("V"),
                    releaseTitle = release.name ?: release.tagName,
                    releaseNotes = release.body.orEmpty(),
                    downloadUrl = apkAsset.browserDownloadUrl,
                    fileSizeBytes = apkAsset.size,
                    publishedAt = release.publishedAt
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates: ${e.message}", e)
            throw e
        }
    }

    /**
     * Downloads the APK file to [destinationFile] and reports download progress.
     *
     * @param downloadUrl The direct URL to download the APK
     * @param destinationFile The file destination on disk
     * @param onProgress Callback invoked periodically with (progress: Float, bytesRead: Long, totalBytes: Long)
     */
    suspend fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
        onProgress: suspend (progress: Float, bytesRead: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "FamilyApp-Android")
            .build()

        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
        destinationFile.parentFile?.mkdirs()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to download APK: HTTP ${response.code}")
                }

                val responseBody = response.body ?: throw IOException("Empty response body when downloading APK")
                val totalBytes = responseBody.contentLength()

                responseBody.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead = 0L
                        var read: Int
                        var lastReportedTime = System.currentTimeMillis()

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read

                            val currentTime = System.currentTimeMillis()
                            // Throttle progress updates to UI every 100ms or when complete
                            if (currentTime - lastReportedTime > 100 || (totalBytes > 0 && bytesRead == totalBytes)) {
                                lastReportedTime = currentTime
                                val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else -1f
                                onProgress(progress, bytesRead, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }
            }

            // Atomically replace target file
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                // Fallback copy if rename fails
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }

            return@withContext destinationFile
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Log.e(TAG, "Error downloading APK: ${e.message}", e)
            throw e
        }
    }
}
