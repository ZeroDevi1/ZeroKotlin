pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "ZeroKotlin"
include(
    "zero-core", "zero-app", "zero-system", "zero-cache",
    "zero-data-mybatis", "zero-data-jpa", "zero-search-elasticsearch",
    "zero-messaging-kafka", "modules:example"
)
