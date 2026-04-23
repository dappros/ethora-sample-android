pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ethora-sample-android"

include(":app")

// -----------------------------------------------------------------------
// Optional composite build for local SDK iteration.
//
// If an ethora-sdk-android checkout is available on disk, compile the
// sample against its source directly via includeBuild instead of
// pulling a pre-built AAR from JitPack. Keeps the
//   implementation("com.github.dappros:ethora-sdk-android:<ver>")
// line in app/build.gradle.kts unchanged — external consumers are
// unaffected — and removes the 3-5 minute JitPack roundtrip from the
// inner-loop SDK edit → rebuild sample flow.
//
// Resolution order (first match wins):
//   1. $ETHORA_SDK_LOCAL_PATH  — explicit override
//   2. ../ethora-sdk-android             — plain side-by-side clones
//   3. ../ethora-bdsm/product/ethora-sdk-android    — BDSM workspace
//   4. ../../ethora-bdsm/product/ethora-sdk-android — one level further up
//
// Opt out entirely: export ETHORA_SDK_LOCAL=0 (forces JitPack even
// when a sibling exists).
//
val localSdkOptOut = (System.getenv("ETHORA_SDK_LOCAL") == "0")
val localSdkOverride = System.getenv("ETHORA_SDK_LOCAL_PATH")
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }

val localSdkCandidates = listOfNotNull(
    localSdkOverride,
    rootDir.resolve("../ethora-sdk-android"),
    rootDir.resolve("../ethora-bdsm/product/ethora-sdk-android"),
    rootDir.resolve("../../ethora-bdsm/product/ethora-sdk-android")
)

val localSdkDir = if (localSdkOptOut) null
    else localSdkCandidates.firstOrNull { it.resolve("settings.gradle.kts").exists() }

if (localSdkDir != null) {
    logger.lifecycle(
        "ethora-sample-android: using local SDK source at ${localSdkDir.canonicalPath} " +
            "(set ETHORA_SDK_LOCAL=0 to use JitPack instead)"
    )
    includeBuild(localSdkDir) {
        dependencySubstitution {
            substitute(module("com.github.dappros:ethora-sdk-android"))
                .using(project(":ethora-component"))
        }
    }
} else {
    logger.lifecycle(
        "ethora-sample-android: no local ethora-sdk-android checkout found — " +
            "using the JitPack SDK artifact pinned in app/build.gradle.kts."
    )
}
