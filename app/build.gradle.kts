import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.metro)
}

// Load version from version.properties (single source of truth)
val versionProps = Properties().also {
    it.load(rootProject.file("version.properties").inputStream())
}
// Prefixed so that defaultConfig, which declares its own versionCode, can't shadow them.
val appVersionName = versionProps.getProperty("versionName") ?: "0.0.1"
val appVersionCode = (versionProps.getProperty("versionCode") ?: "1").toInt()

// Provider for use in androidComponents (lazy evaluation)
val versionNameProvider = providers.provider { appVersionName }

android {
    compileSdk = 37
    namespace = "app.galib.painticons"

    defaultConfig {
        applicationId = "app.galib.painticons"
        minSdk = 26
        targetSdk = compileSdk
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = true
        val buildCommit = try {
            providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }.standardOutput.asText.get().trim()
        } catch (e: Exception) {
            "unknown"
        }
        buildConfigField("String", "COMMIT_HASH", "\"$buildCommit\"")
    }

    androidResources {
        generateLocaleConfig = true
    }

    val releaseSigning = try {
        val keystoreProperties = Properties()
        keystoreProperties.load(rootProject.file("keystore.properties").inputStream())
        signingConfigs.create("release") {
            keyAlias = keystoreProperties["keyAlias"].toString()
            keyPassword = keystoreProperties["keyPassword"].toString()
            storeFile = rootProject.file(keystoreProperties["storeFile"].toString())
            storePassword = keystoreProperties["storePassword"].toString()
        }
    } catch (ignored: Exception) {
        signingConfigs["debug"]
    }

    buildTypes {
        all {
            signingConfig = releaseSigning
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
        create("play") {
            applicationIdSuffix = ".play"
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
    }

    flavorDimensions += "product"
    productFlavors {
        create("app") {
            dimension = "product"
            resValue("string", "apps_name", "Painticons")
        }
    }
    sourceSets.getByName("app") {
        res.directories.add("src/runtime/res")
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// ---------------------------------------------------------------------------
// Icon pack template
//
// A minimal icon pack APK (a manifest with the icon pack intent filters, an
// appfilter and one placeholder drawable per icon slot) is compiled with aapt2
// at build time and shipped in the assets of the app. At export time the app
// fills in the painted icons and the appfilter, signs the APK and saves it as
// a plain file.
// ---------------------------------------------------------------------------
val iconPackSlots = 512
val packTemplateDir = layout.buildDirectory.dir("pack-template")
val packTemplateAssetsDir = layout.buildDirectory.dir("pack-template-assets")
val packTemplateApk = packTemplateAssetsDir.map { it.file("iconpack-template.apk") }
val sdkDirectoryPath = provider {
    val localProperties = rootProject.file("local.properties")
    localProperties.takeIf { it.exists() }
        ?.readLines()
        ?.firstOrNull { it.startsWith("sdk.dir=") }
        ?.substringAfter("=")
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("The Android SDK could not be found")
}

abstract class BuildIconPackTemplate : DefaultTask() {
    @get:InputFile
    abstract val manifestFile: RegularFileProperty

    @get:InputFile
    abstract val appfilterFile: RegularFileProperty

    /**
     * The additional resources of the pack, e.g. the icon of the pack itself. They are
     * copied into the resources of the template as they are.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val additionalResFiles: ConfigurableFileCollection

    @get:Input
    abstract val slots: Property<Int>

    @get:Input
    abstract val sdkDirectory: Property<String>

    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun build() {
        val resDir = File(workingDirectory.get().asFile, "res").apply {
            deleteRecursively()
            mkdirs()
        }
        File(resDir, "xml").mkdirs()
        File(resDir, "drawable-xxxhdpi").mkdirs()
        appfilterFile.get().asFile.copyTo(File(resDir, "xml/appfilter.xml"), overwrite = true)

        // The additional resources of the pack, e.g. the icon of the pack itself.
        additionalResFiles.files.filter { it.isDirectory }.forEach { source ->
            source.walkTopDown().filter { it.isFile }.forEach { file ->
                val target = File(resDir, file.relativeTo(source).path)
                target.parentFile.mkdirs()
                file.copyTo(target, overwrite = true)
            }
        }

        // One transparent placeholder PNG per icon slot, so that the compiled
        // resources contain a drawable entry for every icon the pack can hold.
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val png = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        repeat(slots.get()) { index ->
            File(resDir, "drawable-xxxhdpi/icon_%04d.png".format(index + 1)).writeBytes(png)
        }

        val sdkDirectoryFile = File(sdkDirectory.get())
        val aapt2 = File(sdkDirectoryFile, "build-tools").listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name }
            ?.let { File(it, "aapt2") }
            ?.takeIf { it.canExecute() }
            ?: throw GradleException("aapt2 could not be found in $sdkDirectoryFile")
        val androidJar = File(sdkDirectoryFile, "platforms").listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name }
            ?.let { File(it, "android.jar") }
            ?.takeIf { it.exists() }
            ?: throw GradleException("android.jar could not be found in $sdkDirectoryFile")

        val flats = File(workingDirectory.get().asFile, "resources-flat.zip")
        val apk = outputFile.get().asFile
        apk.parentFile.mkdirs()
        aapt2Exec(aapt2, "compile", "--dir", resDir.absolutePath, "-o", flats.absolutePath)
        aapt2Exec(
            aapt2, "link",
            "-o", apk.absolutePath,
            "-I", androidJar.absolutePath,
            "--manifest", manifestFile.get().asFile.absolutePath,
            "-R", flats.absolutePath,
            "--auto-add-overlay",
        )
    }

    private fun aapt2Exec(aapt2: File, vararg command: String) {
        val process = ProcessBuilder(listOf(aapt2.absolutePath) + command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        if (process.waitFor() != 0) {
            throw GradleException("aapt2 ${command.joinToString(" ")} failed:\n$output")
        }
    }
}

val buildIconPackTemplate = tasks.register<BuildIconPackTemplate>("buildIconPackTemplate") {
    manifestFile.set(rootProject.layout.projectDirectory.file("pack-template/AndroidManifest.xml"))
    appfilterFile.set(rootProject.layout.projectDirectory.file("pack-template/appfilter.xml"))
    additionalResFiles.from(rootProject.layout.projectDirectory.dir("pack-template/res"))
    slots.set(iconPackSlots)
    sdkDirectory.set(sdkDirectoryPath)
    workingDirectory.set(packTemplateDir)
    outputFile.set(packTemplateApk)
}

android {
    sourceSets.getByName("main") {
        assets.srcDir(packTemplateAssetsDir.get().asFile)
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(buildIconPackTemplate)
}

// The lint tasks read the merged assets, so they need the icon pack template as well.
tasks.matching { it.name.contains("lint", ignoreCase = true) }.configureEach {
    dependsOn(buildIconPackTemplate)
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            with(output) {
                val newApkName = "Painticons ${versionNameProvider.get()}.apk"
                outputFileName = newApkName
            }
        }
    }
}

composeCompiler {
    stabilityConfigurationFiles.addAll(
        layout.projectDirectory.file("compose_compiler_config.conf"),
    )
    reportsDestination = layout.buildDirectory.dir("compose_build_reports")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.util)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.metrox.viewmodel.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)

    // Signs the exported icon pack.
    implementation(libs.apksig)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.lazycolumn.scrollbar)
    implementation(libs.material.motion.compose.core)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockito)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.preBuild {
    dependsOn(project(projects.svgProcessor.path).tasks.named("run"))
}
