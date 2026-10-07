group = "wibuku.morphe.patches"

patches {
    about {
        name = "Wibuku Morphe Patches"
        description = "Wibuku patches by zaq, for use with Morphe."
        source = "https://github.com/fynnaly/test-morphe-patch"
        author = "zaq"
        contact = "https://github.com/fynnaly"
        website = "https://morphe.software/add-source?github=fynnaly/test-morphe-patch"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath: Configuration by configurations.creating

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Generate patch metadata from the compiled bundle"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
