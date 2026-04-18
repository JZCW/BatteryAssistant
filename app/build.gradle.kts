import org.gradle.kotlin.dsl.compileOnly
import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream

plugins {
    alias(libs.plugins.android.application)
}

// 版本号管理
val versionPropsFile = rootProject.file("version.properties")
// 在配置阶段读取当前版本（用于 android.defaultConfig）
val versionCodeFromFile: Int = Properties().apply {
  FileInputStream(versionPropsFile).use { load(it) }
}.getProperty("VERSION_CODE").toInt()

tasks.register("incrementVersionCode") {
  doLast {
    val props = Properties().apply {
      FileInputStream(versionPropsFile).use { load(it) }
    }
    val newCode = props.getProperty("VERSION_CODE").toInt() + 1
    props.setProperty("VERSION_CODE", newCode.toString())
    FileOutputStream(versionPropsFile).use { props.store(it, null) }
    println("Bumped VERSION_CODE -> $newCode")
  }
}

// 依赖 preBuild
tasks.named("preBuild") {
    dependsOn("incrementVersionCode")
}
//------------------------------------------------------------------------------

// 配置签名
val keystorePropertiesFile = rootProject.file("key/keystore.properties")
val keystoreProperties = Properties()
keystoreProperties.load(FileInputStream(keystorePropertiesFile))

android {
    namespace = "com.upo.batteryassistant"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.upo.batteryassistant"
        minSdk = 35
        targetSdk = 36
        versionCode = versionCodeFromFile
        versionName = "0.5.${versionCodeFromFile}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("config") {
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("config")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("config")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.swiperefreshlayout)
    implementation(libs.mpandroidchart)
    implementation(libs.viewpager2)
    implementation(libs.cardview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}