/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.R
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object AppUpdater {

    private const val APK_MIME = "application/vnd.android.package-archive"

    private const val UPDATE_DIR = "updates"

    private fun apkDir(context: Context): File = File(context.cacheDir, UPDATE_DIR)

    /**
     * Download [asset] into the app cache.
     *
     * [onProgress] is invoked from a background thread with `(downloadedBytes, totalBytes)`,
     * `totalBytes` may be `0` when the server does not report the content length.
     */
    suspend fun downloadApk(
        context: Context,
        asset: GithubAsset,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val dir = apkDir(context).apply { mkdirs() }
        // clean up previously downloaded packages
        dir.listFiles()?.forEach { it.delete() }
        val tempFile = File(dir, "${asset.name}.part")
        val apkFile = File(dir, asset.name)
        val conn = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "fcitx5-android/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val total = conn.contentLength.toLong().takeIf { it > 0 } ?: asset.size
            conn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPercent = -1
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            if (tempFile.renameTo(apkFile)) {
                apkFile
            } else {
                tempFile.copyTo(apkFile, overwrite = true)
                tempFile.delete()
                apkFile
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Launch the system package installer for the downloaded APK.
     *
     * When the user has not granted this app permission to install unknown apps,
     * the corresponding settings page is opened instead.
     */
    fun install(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.toast(R.string.grant_install_permission)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.update.fileprovider",
            apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
