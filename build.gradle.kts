// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://developer.huawei.com/repo/") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.0")
        classpath("io.objectbox:objectbox-gradle-plugin:4.0.3")
        classpath("com.huawei.agconnect:agcp:1.9.1.301")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}