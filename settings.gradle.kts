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
        // Stable repository for sherpa-onnx Android artifacts
        maven { url = uri("https://k2-fsa.github.io/sherpa/onnx/maven-repo/") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ProstoVoice"
include(":app")
