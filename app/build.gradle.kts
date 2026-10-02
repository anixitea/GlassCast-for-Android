plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.glasscast.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.glasscast.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "1.5"
    }

    buildTypes {
        release {
            // R8: strips unused code and optimizes what's left — inlining,
            // devirtualisation, removing Compose's debug paths. The biggest
            // single speed-up for a weak CPU like the Streamer's.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Debug's convenience without debug's brakes. A debuggable build keeps
        // runtime debugging hooks on and skips the optimizations Compose leans
        // on, so it can stutter where the real app wouldn't — judge smoothness
        // here, never in "debug". Signed with the debug key, so it installs from
        // Android Studio over the debug build with your data intact.
        // Build Variants panel → app → "fast".
        create("fast") {
            initWith(getByName("debug"))
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Most of media3-exoplayer is still marked @UnstableApi. Opting in once here
        // keeps the annotation noise out of every file that touches the player.
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    // The system output switcher behind the player's cast button — and the
    // same library a real Cast integration will need next.
    implementation(libs.androidx.mediarouter)
    // Google Cast: the Streamer, and any Chromecast or Cast speaker. media3-cast
    // supplies a Player that drives the receiver, so the session can swap to it
    // and every control — notification, lock screen, the app — keeps working.
    implementation(libs.androidx.media3.cast)
    implementation(libs.play.services.cast.framework)
    // Background checks for new episodes. WorkManager survives reboots and
    // respects Doze, which a hand-rolled alarm would not.
    implementation(libs.androidx.work.runtime)
    // Installs the Compose libraries' own baseline profiles, so their hot paths
    // are compiled ahead of time instead of warming up in the JIT — the first
    // minute of scrolling is where you'd otherwise feel it.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.media3.common)

    implementation(libs.androidx.palette.ktx)

    implementation(libs.haze)
    implementation(libs.haze.materials)
}
