package com.yu.syncon.util

object CategoryMapper {

    val ALL_CATEGORIES = listOf(
        "Social Media",
        "Entertainment",
        "Browser",
        "Communication",
        "Productivity",
        "Games",
        "System / Utility",
        "Other"
    )

    private val PACKAGE_CATEGORY_MAP = mapOf(
        // Social Media
        "com.instagram.android" to "Social Media",
        "com.instagram.barcelona" to "Social Media",
        "com.facebook.katana" to "Social Media",
        "com.facebook.lite" to "Social Media",
        "com.twitter.android" to "Social Media",
        "com.snapchat.android" to "Social Media",
        "com.zhiliaoapp.musically" to "Social Media",
        "com.ss.android.ugc.trill" to "Social Media",
        "com.reddit.frontpage" to "Social Media",
        "com.pinterest" to "Social Media",
        "com.linkedin.android" to "Social Media",

        // Entertainment
        "com.google.android.youtube" to "Entertainment",
        "com.google.android.apps.youtube.music" to "Entertainment",
        "com.netflix.mediaclient" to "Entertainment",
        "com.amazon.avod.thirdpartyclient" to "Entertainment",
        "com.spotify.music" to "Entertainment",
        "com.disney.disneyplus" to "Entertainment",
        "tv.twitch.android.app" to "Entertainment",
        "com.soundcloud.android" to "Entertainment",
        "com.crunchyroll.crunchyroid" to "Entertainment",

        // Browser
        "com.android.chrome" to "Browser",
        "org.mozilla.firefox" to "Browser",
        "com.microsoft.emmx" to "Browser",
        "com.brave.browser" to "Browser",
        "com.opera.browser" to "Browser",
        "com.sec.android.app.sbrowser" to "Browser",
        "com.duckduckgo.mobile.android" to "Browser",

        // Communication
        "com.whatsapp" to "Communication",
        "com.whatsapp.w4b" to "Communication",
        "org.telegram.messenger" to "Communication",
        "com.google.android.gm" to "Communication",
        "com.microsoft.office.outlook" to "Communication",
        "com.google.android.apps.messaging" to "Communication",
        "com.android.mms" to "Communication",
        "com.discord" to "Communication",
        "com.Slack" to "Communication",
        "org.thoughtcrime.securesms" to "Communication",
        "com.google.android.dialer" to "Communication",
        "com.android.dialer" to "Communication",

        // Productivity
        "com.google.android.apps.docs.editors.docs" to "Productivity",
        "com.google.android.apps.docs.editors.sheets" to "Productivity",
        "com.google.android.apps.docs.editors.slides" to "Productivity",
        "com.google.android.apps.docs" to "Productivity",
        "notion.id" to "Productivity",
        "com.microsoft.office.word" to "Productivity",
        "com.microsoft.office.excel" to "Productivity",
        "com.microsoft.office.powerpoint" to "Productivity",
        "com.microsoft.office.onenote" to "Productivity",
        "com.microsoft.office.officehubrow" to "Productivity",
        "com.google.android.keep" to "Productivity",
        "com.google.android.calendar" to "Productivity",

        // Games
        "com.supercell.clashofclans" to "Games",
        "com.supercell.clashroyale" to "Games",
        "com.supercell.brawlstars" to "Games",
        "com.kiloo.subwaysurf" to "Games",
        "com.king.candycrushsaga" to "Games",
        "com.roblox.client" to "Games",
        "com.mojang.minecraftpe" to "Games",
        "com.epicgames.portal" to "Games",

        // System / Utility
        "com.android.settings" to "System / Utility",
        "com.google.android.GoogleCamera" to "System / Utility",
        "com.android.camera" to "System / Utility",
        "com.google.android.deskclock" to "System / Utility",
        "com.android.deskclock" to "System / Utility",
        "com.google.android.calculator" to "System / Utility",
        "com.android.calculator2" to "System / Utility",
        "com.google.android.apps.nbu.files" to "System / Utility",
        "com.android.documentsui" to "System / Utility",
        "com.android.vending" to "System / Utility",
        "com.android.systemui" to "System / Utility"
    )

    /**
     * Resolves default category for an app by package name.
     * Falls back to "Other" if unmapped.
     */
    fun getDefaultCategory(packageName: String): String {
        return PACKAGE_CATEGORY_MAP[packageName] ?: "Other"
    }
}
