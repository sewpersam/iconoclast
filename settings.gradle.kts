pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "iconoclast"

include(":app", ":render", ":pack", ":iconsource", ":template-pack")
