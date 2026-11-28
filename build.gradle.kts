import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import javax.inject.Inject

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android") apply false
    `maven-publish`
}

val isAgp9OrHigher = com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION.startsWith("9.")
if (!isAgp9OrHigher) {
    apply(plugin = "org.jetbrains.kotlin.android")
}

@CacheableTask
abstract class ExtractHiddifyCore : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aarFile: RegularFileProperty

    @get:OutputFile
    abstract val classesJar: RegularFileProperty

    @get:OutputDirectory
    abstract val jniDirectory: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun extract() {
        val archive = archives.zipTree(aarFile)
        val jar = classesJar.get().asFile

        fileSystem.sync {
            from(archive)
            include("classes.jar")
            into(jar.parentFile)
        }
        fileSystem.sync {
            from(archive)
            include("jni/**/*.so")
            includeEmptyDirs = false
            eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
            into(jniDirectory)
        }
        check(jar.isFile && jniDirectory.get().asFile.walkTopDown().any { it.isFile }) {
            "hiddify-core.aar must contain classes.jar and native libraries under jni/."
        }
    }
}

val extractAar = tasks.register<ExtractHiddifyCore>("extractHiddifyCore") {
    aarFile.set(layout.projectDirectory.file("libs/hiddify-core.aar"))
    classesJar.set(layout.buildDirectory.file("generated/hiddify-core/jar/classes.jar"))
    jniDirectory.set(layout.buildDirectory.dir("generated/hiddify-core/jni"))
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

androidComponents {
    onVariants { variant ->
        requireNotNull(variant.sources.jniLibs).addGeneratedSourceDirectory(
            extractAar,
            ExtractHiddifyCore::jniDirectory,
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    //noinspection UseTomlInstead
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    api(files(extractAar.flatMap(ExtractHiddifyCore::classesJar)))
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
