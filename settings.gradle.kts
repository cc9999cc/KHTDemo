pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(uri("https://jitpack.io"))
        maven { url = uri("https://chaquo.com/maven-test") }//为了使用chaquo15.0.0版本，是一个预发布版，更好的支持build.gradle.kts
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(uri("https://jitpack.io"))
        maven { url = uri("https://chaquo.com/maven-test") }
    }
}

rootProject.name = "KHTDemo"
include(":app")
include(":opencv")
//include(":opencv_sdk")
