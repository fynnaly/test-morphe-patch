package app.template.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val WIBUKU_COMPATIBILITY = Compatibility(
        name = "Wibuku",
        description = "Nonton anime.",
        packageName = "wibuku.app.wibuku",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x7C4DFF,
        targets = listOf(
            AppTarget(version = "1.4.5", versionCode = 78),
            AppTarget(version = "1.4.1", versionCode = 74),
        ),
    )
}
