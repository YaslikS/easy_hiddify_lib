plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android") apply false
    `maven-publish`
}

val isAgp9OrHigher = com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION.startsWith("9.")
if (!isAgp9OrHigher) {
    apply(plugin = "org.jetbrains.kotlin.android")
}

val extractedDir = file("${layout.buildDirectory.get().asFile}/extracted-hiddify-core")
val aarFile = file("libs/hiddify-core.aar")

if (!file("$extractedDir/classes.jar").exists() && aarFile.exists()) {
    copy {
        from(zipTree(aarFile))
        into(extractedDir)
    }
}

val extractAar = tasks.register<Copy>("extractHiddifyCore") {
    if (aarFile.exists()) {
        from(zipTree(aarFile))
        into(extractedDir)
    }
}

android {
    namespace = "com.yasliks.easy_hiddify_lib"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(file("$extractedDir/jni"))
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.named("preBuild") {
    dependsOn(extractAar)
}

dependencies {
    //noinspection UseTomlInstead
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    api(files("$extractedDir/classes.jar") {
        builtBy(extractAar)
    })
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
            }
        }
    }
}