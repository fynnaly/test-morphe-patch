package app.template.patches.botworld.gms

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.BOTWORLD_COMPATIBILITY
import app.template.patches.shared.replaceBody
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Forces the GMS static availability probe to true.
 *
 * Botworld 1.36.2's C# gate may call the GMS static
 * RewardedAd.isAdAvailable(Context, String)Z directly instead of going
 * through the Unity bridge's instance method (which the AdMob patch
 * already forces). Patching only the bridge leaves that direct static
 * call untouched. This patch covers the static itself (2 instructions),
 * so every caller — bridge or direct — sees an available slot.
 *
 * Verified from the APK DEX (classes2.dex):
 * Lcom/google/android/gms/ads/rewarded/RewardedAd;
 * isAdAvailable(Landroid/content/Context; Ljava/lang/String;)Z public static.
 */
private const val GMS_REWARDED = "Lcom/google/android/gms/ads/rewarded/RewardedAd;"

private object GmsAvailableFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
    ),
    definingClass = GMS_REWARDED,
    name = "isAdAvailable",
)

@Suppress("unused")
val botworldGmsStaticPatch = bytecodePatch(
    name = "Force GMS rewarded available",
    description = "Forces the GMS static RewardedAd.isAdAvailable() probe to " +
        "true so any direct static callers (bypassing the Unity bridge) " +
        "also take the reward path. Pairs with the Skip patches; IAP " +
        "purchases still require real payment.",
    default = true,
) {
    compatibleWith(BOTWORLD_COMPATIBILITY)

    execute {
        val available = GmsAvailableFingerprint.method
        if (available.parameterTypes != listOf(
            "Landroid/content/Context;",
            "Ljava/lang/String;",
        )
        ) {
            throw PatchException(
                "Botworld: GMS availability shape changed; use a clean Botworld 1.36.2 (171310).",
            )
        }
        available.replaceBody("const/4 v0, 0x1\nreturn v0")
        if (available.implementation?.instructions?.none {
            it.opcode == Opcode.CONST_4
        } == true
        ) {
            throw PatchException("Botworld: GMS availability body did not apply.")
        }
    }
}
