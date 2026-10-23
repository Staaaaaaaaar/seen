pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val googleMirror = providers.gradleProperty("seen.googleMavenMirror").orNull
    repositories {
        // Optional per-machine transport fallback. CI uses the official repositories.
        if (!googleMirror.isNullOrBlank()) {
            require(googleMirror.startsWith("https://")) { "seen.googleMavenMirror must use HTTPS" }
            maven {
                name = "LocalGoogleMavenMirror"
                url = uri(googleMirror)
                content {
                    includeGroupByRegex("androidx\\..*")
                    includeGroupByRegex("com\\.android\\..*")
                    includeGroupByRegex("com\\.google\\.android\\..*")
                }
            }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "Seen"
include(":app")
