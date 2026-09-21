import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val releaseKeystoreProperties = Properties()
val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
if (releaseKeystorePropertiesFile.exists()) {
    releaseKeystorePropertiesFile.inputStream().use(releaseKeystoreProperties::load)
}
val releaseSigningConfigured = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !releaseKeystoreProperties.getProperty(it).isNullOrBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.yu.syncon"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yu.syncon"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"https://nqpristylxavyexqgjtp.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_AE1V984zNrbJ3YlnJQjMVg_cW7n7sRW\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreProperties.getProperty("storeFile"))
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
        }
    }
}

tasks.register("verifyReleaseReadiness") {
    doLast {
        check(releaseSigningConfigured) {
            "Release signing is not configured. Copy release/keystore.properties.example to keystore.properties and add the private upload-key values."
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    constraints {
        implementation(libs.jdom2)
        implementation(libs.bouncycastle.bcprov)
        implementation(libs.bouncycastle.bcpkix)
        implementation(libs.jose4j)
        implementation(libs.commons.lang3)
        implementation(libs.netty.codec)
        implementation(libs.netty.codec.http)
        implementation(libs.netty.codec.http2)
        implementation(libs.netty.handler.proxy)
        implementation(libs.netty.handler)
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.google.code.scanner)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    debugImplementation(libs.androidx.compose.ui.tooling)
}
