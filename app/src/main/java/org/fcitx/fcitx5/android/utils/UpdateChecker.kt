/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.utils

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList()
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0L
)

data class UpdateInfo(
    val release: GithubRelease,
    /** APK asset matching the current device ABI, or `null` if not found */
    val apk: GithubAsset?
) {
    val version: String get() = release.tagName
}

object UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    /** Tag of the current build, e.g. `0.1.3` from `0.1.3-0-g048f581c` */
    private val currentVersion: String
        get() = Const.versionTag

    /**
     * Query the latest GitHub release.
     *
     * The result is `null` when the running version is up to date.
     */
    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchLatestRelease()
            if (!isNewer(release.tagName, currentVersion)) return@runCatching null
            UpdateInfo(release, release.assets.apkForDevice())
        }
    }

    private fun fetchLatestRelease(): GithubRelease {
        val conn = (URL(Const.githubLatestReleaseApi).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "fcitx5-android/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return json.decodeFromString(GithubRelease.serializer(), body)
        } finally {
            conn.disconnect()
        }
    }

    /** Pick the release APK matching the first supported ABI, in priority order */
    private fun List<GithubAsset>.apkForDevice(): GithubAsset? {
        val prefix = "${Const.baseApplicationId}-"
        for (abi in Build.SUPPORTED_ABIS) {
            firstOrNull { it.name.startsWith(prefix) && it.name.endsWith("-$abi-release.apk") }
                ?.let { return it }
        }
        return null
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = parseVersion(remote) ?: return false
        val currentParts = parseVersion(current) ?: return false
        return compare(remoteParts, currentParts) > 0
    }

    private fun parseVersion(version: String): List<Int>? {
        val segments = version.trim().removePrefix("v").split('.')
        val parts = ArrayList<Int>(segments.size)
        for (segment in segments) {
            val number = segment.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
            parts.add(number)
        }
        return parts
    }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }
}
