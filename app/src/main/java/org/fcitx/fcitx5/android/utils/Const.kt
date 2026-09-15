/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.utils

import org.fcitx.fcitx5.android.BuildConfig

object Const {
    const val versionName = "${BuildConfig.VERSION_NAME}-${BuildConfig.BUILD_TYPE}"

    /**
     * Release tag of the current build, e.g. `0.1.3` from `0.1.3-0-g048f581c`,
     * or from `0.1.3-5-g1234567` when the build is ahead of the release.
     */
    val versionTag: String = BuildConfig.VERSION_NAME.substringBefore('-')

    /**
     * Base application id, without any build type suffix such as `.debug`.
     * Used to match the APK assets published on GitHub.
     */
    const val baseApplicationId = "org.fcitx.fcitx5.android"

    /**
     * GitHub repository that hosts the releases this app updates from.
     */
    const val githubOwner = "colorsakura"
    const val githubRepoName = "fcitx5-android"
    const val githubRepo = "https://github.com/$githubOwner/$githubRepoName"
    const val githubLatestReleaseApi =
        "https://api.github.com/repos/$githubOwner/$githubRepoName/releases/latest"
    const val licenseSpdxId = "LGPL-2.1-or-later"
    const val licenseUrl = "https://www.gnu.org/licenses/old-licenses/lgpl-2.1"
    const val privacyPolicyUrl = "https://fcitx5-android.github.io/privacy/"
    const val faqUrl = "https://fcitx5-android.github.io/faq/"
}
