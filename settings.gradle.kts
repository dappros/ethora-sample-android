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
        // mavenLocal() first so locally-published SDK builds (via
        // `./gradlew publishToMavenLocal` from the SDK root) take
        // precedence over the remote JitPack artifact.
        mavenLocal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ethora-sample-android"

include(":app")

// Composite build — substitutes the JitPack coordinate with the local SDK source
// so this app always compiles against the current state of the repo without a
// publishToMavenLocal step. Remove this block to fall back to mavenLocal() or JitPack.
includeBuild("..") {
    dependencySubstitution {
        substitute(module("com.github.dappros:ethora-sdk-android"))
            .using(project(":ethora-component"))
    }
}
