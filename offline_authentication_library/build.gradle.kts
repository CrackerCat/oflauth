plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

group="com.dev2.wpvsyou"
version="1.0.8"
android {
    namespace = "com.dev2.offlineauthentication"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    afterEvaluate {
        publishing {
            publications {
                create("release", MavenPublication::class) {
                    groupId = "com.dev2.wpvsyou"
                    artifactId = "offlineauthentication"
                    version = "1.0.8"
                    from(components["release"])

                    //artifact("$buildDir/outputs/aar/${project.name}-release.aar")
                }
            }
        }
    }
}

dependencies {
    compileOnly("com.google.code.gson:gson:2.10.1")
    implementation("androidx.compose.runtime:runtime:1.5.0")
}