/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.ui.common.withLoadingDialog
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.AppUpdater
import org.fcitx.fcitx5.android.utils.Const
import org.fcitx.fcitx5.android.utils.UpdateChecker
import org.fcitx.fcitx5.android.utils.UpdateInfo
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.formatDateTime
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import org.fcitx.fcitx5.android.utils.toast

class AboutFragment : PaddingPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addPreference(R.string.privacy_policy) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Const.privacyPolicyUrl)))
            }
            addPreference(
                R.string.open_source_licenses,
                R.string.licenses_of_third_party_libraries
            ) {
                navigateWithAnim(SettingsRoute.License)
            }
            addPreference(R.string.source_code, R.string.github_repo) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Const.githubRepo)))
            }
            addPreference(R.string.license, Const.licenseSpdxId) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Const.licenseUrl)))
            }
            addCategory(R.string.version) {
                isIconSpaceReserved = false
                addPreference(R.string.current_version, Const.versionName)
                addPreference(
                    R.string.check_for_updates,
                    R.string.check_for_updates_summary
                ) {
                    checkForUpdates()
                }
                addPreference(R.string.build_git_hash, BuildConfig.BUILD_GIT_HASH) {
                    val commit = BuildConfig.BUILD_GIT_HASH.substringBefore('-')
                    val uri = Uri.parse("${Const.githubRepo}/commit/${commit}")
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                addPreference(R.string.build_time, formatDateTime(BuildConfig.BUILD_TIME))
            }
        }
    }

    private fun checkForUpdates() {
        val ctx = requireContext()
        lifecycleScope.withLoadingDialog(ctx, R.string.check_for_updates) {
            UpdateChecker.check().fold(
                onSuccess = { info ->
                    if (info == null) {
                        ctx.toast(R.string.already_latest_version)
                    } else {
                        showUpdateDialog(ctx, info)
                    }
                },
                onFailure = { ctx.toast(it) }
            )
        }
    }

    private fun showUpdateDialog(ctx: Context, info: UpdateInfo) {
        val notes = info.release.body?.trim().orEmpty()
        val message = buildString {
            append(ctx.getString(R.string.update_available_message, info.version))
            if (notes.isNotEmpty()) {
                append("\n\n")
                append(notes)
            }
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.update_available)
            .setMessage(message)
            .setPositiveButton(
                if (info.apk != null) {
                    R.string.download_and_install
                } else {
                    R.string.open_release_page
                }
            ) { _, _ ->
                if (info.apk != null) {
                    downloadAndInstall(ctx, info)
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.release.htmlUrl)))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadAndInstall(ctx: Context, info: UpdateInfo) {
        val asset = info.apk ?: return
        lifecycleScope.launch {
            val dialog = AlertDialog.Builder(ctx)
                .setTitle(R.string.downloading_update)
                .setMessage(R.string.please_wait)
                .setCancelable(false)
                .create()
            dialog.show()
            try {
                val apk = AppUpdater.downloadApk(ctx, asset) { downloaded, total ->
                    val text = if (total > 0) {
                        "${downloaded * 100 / total}%"
                    } else {
                        getString(R.string.please_wait)
                    }
                    withContext(Dispatchers.Main) { dialog.setMessage(text) }
                }
                dialog.dismiss()
                AppUpdater.install(ctx, apk)
            } catch (e: Exception) {
                dialog.dismiss()
                ctx.toast(e)
            }
        }
    }
}
