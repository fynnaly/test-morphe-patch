package app.template.patches.botworld.offline

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.BOTWORLD_COMPATIBILITY
import org.w3c.dom.Element

/**
 * Runs Botworld 1.36.2 without Play Games sign-in (guest/offline).
 *
 * Shape (verified from the APK manifest): Play Games SDK v2 auto-initializes
 * via com.google.android.gms.games.provider.PlayGamesInitProvider. The game
 * itself issues no in-app sign-in calls resolvable from DEX (login originates
 * in il2cpp), so there is no bytecode redirect point. Disabling the init
 * provider keeps the SDK uninitialized and the game runs as guest.
 *
 * Follows the repo's resource-patch pattern (private anonymous resourcePatch
 * as a dependency of a named bytecodePatch, like the Wibuku MicroG route):
 * the metadata generator only lists named patches, so a standalone
 * resourcePatch would never appear in Morphe Manager.
 *
 * Guard: the provider must still be present and not already disabled, so a
 * reshaped manifest fails loudly instead of shipping a no-op patch.
 */
private const val PLAY_GAMES_PROVIDER = "com.google.android.gms.games.provider.PlayGamesInitProvider"
private const val MOBILE_ADS_PROVIDER = "com.google.android.gms.ads.MobileAdsInitProvider"
private const val APPLOVIN_PROVIDER = "com.applovin.sdk.AppLovinInitProvider"
private const val PLAY_GAMES_SDK = "Lcom/google/android/gms/games/PlayGamesSdk;"
private const val ANDROID = "http://schemas.android.com/apk/res/android"

private fun value(element: Element, name: String) =
    element.getAttributeNS(ANDROID, name).ifEmpty { element.getAttribute("android:$name") }

private val botworldOfflineResources = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val providers = doc.documentElement.getElementsByTagName("provider")
            val targets = setOf(PLAY_GAMES_PROVIDER, MOBILE_ADS_PROVIDER, APPLOVIN_PROVIDER)
            val touched = mutableSetOf<String>()
            for (i in 0 until providers.length) {
                val provider = providers.item(i) as Element
                val name = value(provider, "name")
                if (name !in targets) continue
                if (value(provider, "enabled") == "false") {
                    throw PatchException(
                        "Botworld: provider $name already disabled; use a clean Botworld 1.36.2 (171310).",
                    )
                }
                provider.setAttribute("android:enabled", "false")
                touched.add(name)
            }
            val missing = targets - touched
            if (missing.isNotEmpty()) {
                throw PatchException(
                    "Botworld: providers not found ($missing); " +
                        "use a clean Botworld 1.36.2 (171310).",
                )
            }
        }
    }
}

@Suppress("unused")
val botworldOfflineLoginPatch = bytecodePatch(
    name = "Play offline (guest)",
    description = "Disables Play Games sign-in plus the AdMob/AppLovin init " +
        "providers so the game runs fully offline as guest: no SDK ad " +
        "loading, no login. Rewards come from the local Skip patches. " +
        "Does not grant items or currency.",
    default = true,
) {
    compatibleWith(BOTWORLD_COMPATIBILITY)
    dependsOn(botworldOfflineResources)

    execute {
        // Guard: the Play Games SDK must still be bundled; a future Botworld
        // without it (or with a renamed SDK) must fail here instead of
        // shipping a manifest edit against an unknown login flow.
        try {
            classDefBy(PLAY_GAMES_SDK)
        } catch (e: Exception) {
            throw PatchException(
                "Botworld: Play Games SDK gone; use a clean Botworld 1.36.2 (171310).",
            )
        }
    }
}
