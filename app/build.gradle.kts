import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.todo"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.todo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val debugUnitTestClassesRoot = File(System.getProperty("java.io.tmpdir"), "tongji-todo-unit-test-classes/debug")
val syncDebugKotlinClassesForUnitTest by tasks.registering(Sync::class) {
    dependsOn("compileDebugKotlin", "compileDebugUnitTestKotlin")
    from(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
        into("main")
    }
    from(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes")) {
        into("test")
    }
    into(debugUnitTestClassesRoot)
}

afterEvaluate {
    tasks.named<Test>("testDebugUnitTest") {
        dependsOn(syncDebugKotlinClassesForUnitTest)
        val asciiMainClasses = File(debugUnitTestClassesRoot, "main")
        val asciiTestClasses = File(debugUnitTestClassesRoot, "test")
        testClassesDirs = files(asciiTestClasses)
        classpath = files(asciiMainClasses, asciiTestClasses).plus(classpath)
    }
}