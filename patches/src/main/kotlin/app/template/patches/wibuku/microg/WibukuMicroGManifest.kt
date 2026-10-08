package app.template.patches.wibuku.microg

import app.morphe.patcher.patch.PatchException
import org.w3c.dom.Element

/**
 * Declare the verified original signer to MicroG-RE, without replacing
 * Android accounts.
 *
 * Wibuku 1.4.5 (APKPure repack) carries the generic AOSP debug-style
 * certificate (SHA-1 ec131df0ce4e569a0fef40a1f5e6ef74d4d1e8e7).
 * If you patch a Play-original APK instead, replace SIGNER with the SHA-1
 * of ITS signing certificate (uppercase hex without colons, lowercased here).
 */
internal object WibukuMicroGManifest {
    const val MICROG = "app.revanced.android.gms"
    const val PACKAGE = "wibuku.app.wibuku"
    // 1.4.5 repack (78) and 1.4.1 repack (74) share the same generic
    // AOSP signer (verified via androguard SHA-1 on both APKs).
    const val VERSION_CODE_145 = "78"
    const val VERSION_CODE_141 = "74"
    const val SIGNER = "ec131df0ce4e569a0fef40a1f5e6ef74d4d1e8e7"
    private const val ANDROID = "http://schemas.android.com/apk/res/android"

    private fun value(element: Element, name: String) =
        element.getAttributeNS(ANDROID, name).ifEmpty { element.getAttribute("android:$name") }

    fun install(manifest: Element) {
        fun requireShape(ok: Boolean, message: String) {
            if (!ok) throw PatchException("Google sign-in via MicroG-RE: $message")
        }
        requireShape(
            manifest.getAttribute("package") == PACKAGE &&
                (value(manifest, "versionCode") == VERSION_CODE_145 ||
                    value(manifest, "versionCode") == VERSION_CODE_141),
            "manifest target changed; use a clean Wibuku 1.4.5 (78) or 1.4.1 (74)",
        )
        val apps = manifest.getElementsByTagName("application")
        requireShape(apps.length == 1, "expected one application")
        val app = apps.item(0) as Element
        val metadata = mapOf(
            "$MICROG.SPOOFED_PACKAGE_NAME" to PACKAGE,
            "$MICROG.SPOOFED_PACKAGE_SIGNATURE" to SIGNER,
            "app.revanced.MICROG_PACKAGE_NAME" to MICROG,
        )
        val existing = app.getElementsByTagName("meta-data")
        requireShape((0 until existing.length).none {
            value(existing.item(it) as Element, "name") in metadata
        }, "MicroG metadata already present; use a clean APK")
        metadata.forEach { (name, text) ->
            app.appendChild(manifest.ownerDocument.createElement("meta-data").apply {
                setAttribute("android:name", name)
                setAttribute("android:value", text)
            })
        }
        val queries = manifest.getElementsByTagName("queries")
        requireShape(queries.length <= 1, "multiple queries elements")
        val query = (queries.item(0) as? Element)
            ?: manifest.ownerDocument.createElement("queries").also {
                manifest.appendChild(it)
            }
        val packages = query.getElementsByTagName("package")
        if ((0 until packages.length).none { value(packages.item(it) as Element, "name") == MICROG }) {
            query.appendChild(manifest.ownerDocument.createElement("package").apply {
                setAttribute("android:name", MICROG)
            })
        }
    }
}
