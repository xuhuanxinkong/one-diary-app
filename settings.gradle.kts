pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // 华为 Maven 仓库
        maven { url = uri("https://developer.huawei.com/repo/") }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // 华为 Maven 仓库
        maven { url = uri("https://developer.huawei.com/repo/") }
        mavenCentral()
    }
}

rootProject.name = "Diaryd"
include(":app")
 