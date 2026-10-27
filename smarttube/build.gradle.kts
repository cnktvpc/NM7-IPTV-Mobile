plugins {
    id("com.android.library") version "8.13.2"
}

android {
    namespace = "com.liskovsoft.smartyoutubetv2.droid"
    compileSdk = 36

    // Mirror the app's device dimension so the Mobile app consumes a
    // matching SmartTube variant instead of leaving its transitive
    // SmartTube flavors ambiguous.
    flavorDimensions += "device"
    productFlavors {
        create("mobile") {
            dimension = "device"
        }
    }

    defaultConfig {
        minSdk = 23
        targetSdk = 36
        multiDexEnabled = true
        missingDimensionStrategy("default", "ststable")
        buildConfigField("long", "TIMESTAMP", "${System.currentTimeMillis()}L")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    sourceSets["main"].apply {
        java.srcDirs("../third_party/SmartTube-droid/smarttubedroid/src/main/java")
        res.srcDirs("../third_party/SmartTube-droid/smarttubedroid/src/main/res", "src/main/res")
        assets.srcDirs("../third_party/SmartTube-droid/smarttubedroid/src/main/assets")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "../third_party/SmartTube-droid/smarttubedroid/libs", "include" to listOf("*.jar"))))
    implementation(project(":common"))
    implementation(project(":sharedutils"))
    implementation(project(":mediaserviceinterfaces"))
    implementation(project(":youtubeapi"))
    implementation(project(":fragment-1.1.0"))
    implementation(project(":exoplayer-library-core"))
    implementation(project(":exoplayer-library-ui"))
    implementation(project(":exoplayer-extension-mediasession"))
    implementation(project(":doubletapplayerview"))

    // SmartTube phone UI dependencies. Keep the upstream versions explicit
    // because SharedModules constants are not exposed to this Kotlin DSL module.
    implementation("androidx.annotation:annotation:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:1.1.2")
    implementation("androidx.media:media:1.2.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("com.google.android.material:material:1.5.0")
    implementation("com.github.bumptech.glide:glide:4.11.0")
    implementation("com.github.bumptech.glide:annotations:4.11.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.11.0")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}

