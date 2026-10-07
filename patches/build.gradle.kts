group = "wibuku.morphe.patches"

patches {
    about {
        name = "Wibuku Morphe Patches"
        description = "Wibuku patches by zaq, for use with Morphe."
        source = "https://github.com/<owner>/wibuku-morphe-patches"
        author = "zaq"
        contact = "https://github.com/<owner>"
        website = "https://morphe.software/add-source?github=<owner>/wibuku-morphe-patches"
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
