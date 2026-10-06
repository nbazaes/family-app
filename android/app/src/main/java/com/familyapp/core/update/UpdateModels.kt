package com.familyapp.core.update

import com.google.gson.annotations.SerializedName

/**
 * DTO representing a GitHub Release from GitHub REST API.
 * e.g. GET https://api.github.com/repos/nbazaes/family-app/releases/latest
 */
data class GitHubReleaseDto(
    @SerializedName("tag_name")
    val tagName: String,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("body")
    val body: String? = null,

    @SerializedName("published_at")
    val publishedAt: String? = null,

    @SerializedName("assets")
    val assets: List<GitHubAssetDto> = emptyList()
)

data class GitHubAssetDto(
    @SerializedName("name")
    val name: String,

    @SerializedName("browser_download_url")
    val browserDownloadUrl: String,

    @SerializedName("size")
    val size: Long = 0L,

    @SerializedName("content_type")
    val contentType: String? = null
)

/**
 * Domain model representing an available app update.
 */
data class AppUpdateInfo(
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val publishedAt: String?
)

/**
 * Semantic Version helper for comparing versions like "0.2.0", "v0.3.0", etc.
 */
object SemVer {
    /**
     * Returns true if [remoteVersion] is strictly newer than [currentVersion].
     */
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        val cleanRemote = cleanVersion(remoteVersion)
        val cleanCurrent = cleanVersion(currentVersion)

        if (cleanRemote == cleanCurrent) return false

        val remoteParts = parseParts(cleanRemote)
        val currentParts = parseParts(cleanCurrent)

        val maxLength = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        return false
    }

    private fun cleanVersion(version: String): String {
        return version.trim().removePrefix("v").removePrefix("V").split("-")[0].trim()
    }

    private fun parseParts(version: String): List<Int> {
        return version.split(".")
            .mapNotNull { it.toIntOrNull() }
    }
}
