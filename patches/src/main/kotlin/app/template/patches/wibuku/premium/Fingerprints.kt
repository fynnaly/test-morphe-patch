package app.template.patches.wibuku.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Targets AppUser.isPremium()Z of Wibuku 1.4.5.
 *
 * Shape (verified from the APK DEX): the method reads the `premium` long
 * field (a server-issued expiry timestamp), compares it against the current
 * time and returns 1 when premium is still in the future.
 *
 * Stable anchors:
 *   1. the AppUser class owns a `premium` long field read by this method
 *   2. the method calls the app's clock helper right before cmp-long
 */
object WibukuIsPremiumFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = emptyList(),
    definingClass = "Lwibuku/app/wibuku/model/user/AppUser;",
    name = "isPremium",
)
