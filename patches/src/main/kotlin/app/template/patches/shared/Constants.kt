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

    val BOTWORLD_COMPATIBILITY = Compatibility(
        name = "Botworld",
        description = "Botworld Adventure.",
        packageName = "com.featherweightgames.fx",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x4CAF50,
        targets = listOf(
            AppTarget(version = "1.36.2", versionCode = 171310),
        ),
    )
}
