package com.example.digitaladdictionmonitor.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Maps an installed package to the same 5-category taxonomy the ml-service
 * side uses (social/productivity/entertainment/education/other -- see
 * ml-service/data/generator.py's CATEGORIES). Real devices don't ship with
 * that labelling, so this is a best-effort classifier: a hand-picked lookup
 * for common apps first (most reliable), falling back to Android's own
 * ApplicationInfo.category (coarser, and most apps don't declare one), and
 * finally "other" if nothing matches.
 */
object AppCategoryMapper {

    private val KNOWN_PACKAGES: Map<String, String> = mapOf(
        // social
        "com.instagram.android" to "social",
        "com.whatsapp" to "social",
        "com.snapchat.android" to "social",
        "com.facebook.katana" to "social",
        "com.twitter.android" to "social",
        "com.twitter.android.lite" to "social",
        "org.telegram.messenger" to "social",
        // productivity
        "com.google.android.apps.docs.editors.docs" to "productivity",
        "com.google.android.apps.docs" to "productivity",
        "notion.id" to "productivity",
        "com.microsoft.office.excel" to "productivity",
        "com.Slack" to "productivity",
        "com.todoist" to "productivity",
        "com.microsoft.office.outlook" to "productivity",
        // entertainment
        "com.google.android.youtube" to "entertainment",
        "com.netflix.mediaclient" to "entertainment",
        "com.spotify.music" to "entertainment",
        "com.zhiliaoapp.musically" to "entertainment", // TikTok
        "com.ss.android.ugc.trill" to "entertainment", // TikTok (alt package)
        "com.tencent.ig" to "entertainment", // PUBG Mobile
        "com.amazon.avod.thirdpartyclient" to "entertainment", // Prime Video
        // education
        "org.coursera.android" to "education",
        "org.khanacademy.android" to "education",
        "com.duolingo" to "education",
        "com.google.android.apps.classroom" to "education",
        "com.amazon.kindle" to "education",
        // other / utility
        "com.android.camera2" to "other",
        "com.android.settings" to "other",
        "com.android.dialer" to "other",
        "com.google.android.apps.maps" to "other",
        "com.android.chrome" to "other",
        "com.google.android.apps.photos" to "other"
    )

    fun categoryFor(context: Context, packageName: String): String {
        KNOWN_PACKAGES[packageName]?.let { return it }

        val info = try {
            context.packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return "other"
        }

        return when (info.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> "social"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
            ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_IMAGE -> "entertainment"
            else -> "other"
        }
    }

    fun displayNameFor(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
