import java.net.URI

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
        maven(url = URI("https://jitpack.io"))
    }
}

rootProject.name = "offline_authentication"
include(":admin_sample")
include(":consumer_sample")
include(":offline_authentication_library")
