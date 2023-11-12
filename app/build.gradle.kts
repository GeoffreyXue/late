import com.android.build.api.dsl.Packaging

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "family.late"
    compileSdk = 34

    defaultConfig {
        applicationId = "family.late"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        kotlinCompilerExtensionVersion = "1.4.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/**"
        }
    }
}
dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation(platform("androidx.compose:compose-bom:2023.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Auth
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Widget
    // For AppWidgets support
    implementation("androidx.glance:glance-appwidget:1.0.0")

    // For interop APIs with Material 2
    implementation("androidx.glance:glance-material:1.0.0")

    // For interop APIs with Material 3
    implementation("androidx.glance:glance-material3:1.0.0")

    // Images
    implementation("io.coil-kt:coil-compose:1.3.2")

    // API
    implementation("com.google.api-client:google-api-client-android:1.23.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev305-1.23.0")
    implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")

    // https://mvnrepository.com/artifact/com.google.apis/google-api-services-gmail
    implementation("com.google.apis:google-api-services-gmail:v1-rev110-1.25.0")


    // https://mvnrepository.com/artifact/com.sun.mail/javax.mail
    // https://mvnrepository.com/artifact/jakarta.mail/jakarta.mail-api
    implementation("com.sun.mail:jakarta.mail:2.0.1")


    // For widget experimental views
    implementation("com.google.android.glance.tools:appwidget-host:0.2.2")
}