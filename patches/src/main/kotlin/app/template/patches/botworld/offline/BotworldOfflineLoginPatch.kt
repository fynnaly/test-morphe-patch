package app.template.patches.botworld.offline

import app.morphe.patcher.patch.PatchException
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
 * Guard: the provider must still be present and not already disabled, so a
 * reshaped manifest fails loudly instead of shipping a no-op patch.
 */
private const val PLAY_GAMES_PROVIDER = "com.google.android.gms.games.provider.PlayGamesInitProvider"
private const val ANDROID = "http://schemas.android.com/apk/res/android"

private fun value(element: Element, name: String) =
    element.getAttributeNS(ANDROID, name).ifEmpty { element.getAttribute("android:$name") }

@Suppress("unused")
val botworldOfflineLoginPatch = resourcePatch {
    compatibleWith(BOTWORLD_COMPATIBILITY)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val providers = doc.documentElement.getElementsByTagName("provider")
            var touched = 0
            for (i in 0 until providers.length) {
                val provider = providers.item(i) as Element
                if (value(provider, "name") != PLAY_GAMES_PROVIDER) continue
                if (value(provider, "enabled") == "false") {
                    throw PatchException(
                        "Botworld: Play Games provider already disabled; use a clean Botworld 1.36.2 (171310).",
                    )
                }
                provider.setAttribute("android:enabled", "false")
                touched++
            }
            if (touched != 1) {
                throw PatchException(
                    "Botworld: Play Games provider not found ($touched matches); " +
                        "use a clean Botworld 1.36.2 (171310).",
                )
            }
        }
    }
}
