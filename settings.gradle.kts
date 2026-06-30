pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BritSpeak"

// The pure-Kotlin core is a standalone Gradle build; pull it in as a composite build so
// `:app` can depend on `com.englishteacher:core` while the core stays independently testable.
includeBuild("core")

include(":app")
