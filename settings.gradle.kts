pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Mirrors first: Maven Central 429-rate-limits this build server.
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Mirrors first: Maven Central 429-rate-limits this build server.
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}

rootProject.name = "momo-subtitle"

include(":app")
include(":core:common")
include(":core:database")
include(":core:media")
include(":core:models")
include(":core:processing")
include(":core:speech")
include(":core:storage")
include(":core:subtitles")
include(":core:translation")
include(":core:ui")
include(":feature:editor")
include(":feature:export")
include(":feature:home")
include(":feature:import")
include(":feature:models")
include(":feature:processing")
include(":feature:settings")
