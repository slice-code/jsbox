import java.net.URI
import java.net.URL
import java.net.HttpURLConnection
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

// Robust manual .env parser
val env = mutableMapOf<String, String>()
val envFile = file("${rootDir}/.env")
if (envFile.exists()) {
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
            val parts = trimmed.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                var value = parts[1].trim()
                if (value.contains(" #")) value = value.substring(0, value.indexOf(" #")).trim()
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length - 1).trim()
                }
                env[key] = value
            }
        }
    }
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.jssandbox.xdwetb"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val kPath = env["KEYSTORE_PATH"] ?: "my-upload-key.jks"
      val kFile = if (File(kPath).isAbsolute) file(kPath) else file("${rootDir}/$kPath")
      storeFile = kFile
      storePassword = env["STORE_PASSWORD"] ?: ""
      keyAlias = env["KEY_ALIAS"] ?: "upload"
      keyPassword = env["KEY_PASSWORD"] ?: ""
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

// Task untuk membuat Keystore otomatis jika belum ada
tasks.register("generateKeystore") {
    group = "publishing"
    val sPass = env["STORE_PASSWORD"] ?: ""
    val kPass = env["KEY_PASSWORD"] ?: ""
    val kAlias = env["KEY_ALIAS"] ?: "upload"
    val kPathEnv = env["KEYSTORE_PATH"] ?: "my-upload-key.jks"
    val kFile = if (File(kPathEnv).isAbsolute) file(kPathEnv) else file("${rootDir}/$kPathEnv")

    doLast {
        val execOps = project.serviceOf<ExecOperations>()
        if (sPass.isEmpty()) {
            println("WARNING: STORE_PASSWORD is empty in .env. Skipping keystore generation.")
            return@doLast
        }

        if (kFile.exists()) {
            println("Checking existing keystore password...")
            val result = execOps.exec {
                executable("keytool")
                args("-list", "-keystore", kFile.absolutePath, "-storepass", sPass)
                isIgnoreExitValue = true
                standardOutput = OutputStream.nullOutputStream()
                errorOutput = OutputStream.nullOutputStream()
            }
            
            if (result.exitValue != 0) {
                throw GradleException("Password di .env tidak cocok dengan keystore yang sudah ada (${kFile.name}). Silakan periksa password Anda atau hapus file keystore tersebut secara manual jika ingin membuat baru.")
            }
        }

        if (!kFile.exists()) {
            println("Generating new keystore at ${kFile.absolutePath}...")
            execOps.exec {
                executable("keytool")
                args(
                    "-genkey", "-v", "-keystore", kFile.absolutePath, "-alias", kAlias,
                    "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
                    "-storepass", sPass, "-keypass", kPass,
                    "-dname", "CN=JSBox, OU=Dev, O=JSBox, L=Jakarta, S=Jakarta, C=ID", "-noprompt"
                )
            }
            println("Keystore generated successfully!")
        } else {
            println("Keystore is valid and ready.")
        }
    }
}

tasks.matching { it.name == "validateSigningRelease" }.all { dependsOn("generateKeystore") }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation(libs.sora.editor)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}

tasks.register("downloadOfflineEditor") {
    outputs.dir("src/main/assets/web-ide/libs")
    doLast {
        val outputDir = File("app/src/main/assets/web-ide/libs").let {
            if (it.parentFile?.exists() == true) it else File("src/main/assets/web-ide/libs")
        }
        if (!outputDir.exists()) outputDir.mkdirs()
        val filesToDownload = mapOf(
            "codemirror.min.js" to "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.15/codemirror.min.js",
            "codemirror.min.css" to "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.15/codemirror.min.css",
            "javascript.min.js" to "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.15/mode/javascript/javascript.min.js",
            "dracula.min.css" to "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.15/theme/dracula.min.css"
        )
        filesToDownload.forEach { (name, urlString) ->
            val destinationFile = File(outputDir, name)
            if (!destinationFile.exists()) {
                try {
                    val url = URI(urlString).toURL()
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connect()
                    connection.inputStream.use { input ->
                        destinationFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) { println("Failed to download $name: ${e.message}") }
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.all { dependsOn("downloadOfflineEditor") }
