pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // SmartTube's legacy modules declare their own repositories.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "IPTV Player"
include(":app")
include(":smarttube")

// SmartTube Droid's legacy modules use these shared paths and constants.
// The original core_settings.gradle files are not present in the checked-out
// submodule tree in CI, so the required modules are included explicitly below.
val smartTubeRoot = file("third_party/SmartTube-droid")
gradle.extensions.extraProperties.set("sharedModulesRoot", file("$smartTubeRoot/SharedModules"))
gradle.extensions.extraProperties.set("sharedModulesConstants", file("$smartTubeRoot/SharedModules/constants.gradle"))
gradle.extensions.extraProperties.set("mediaServiceCoreRoot", file("$smartTubeRoot/MediaServiceCore"))
gradle.extensions.extraProperties.set("exoplayerRoot", file("$smartTubeRoot/exoplayer-amzn-2.10.6"))
gradle.extensions.extraProperties.set("exoplayerModulePrefix", "exoplayer-")

// SmartTube Droid phone UI and shared modules.
include(":common", ":leanbackassistant", ":leanback-1.0.0", ":fragment-1.1.0", ":filepicker-lib", ":doubletapplayerview", ":slidableactivity")
project(":common").projectDir = file("third_party/SmartTube-droid/common")
project(":leanbackassistant").projectDir = file("third_party/SmartTube-droid/leanbackassistant")
project(":leanback-1.0.0").projectDir = file("third_party/SmartTube-droid/leanback-1.0.0")
project(":fragment-1.1.0").projectDir = file("third_party/SmartTube-droid/fragment-1.1.0")
project(":filepicker-lib").projectDir = file("third_party/SmartTube-droid/filepicker-lib")
project(":doubletapplayerview").projectDir = file("third_party/SmartTube-droid/doubletapplayerview")
project(":slidableactivity").projectDir = file("third_party/SmartTube-droid/slidableactivity")

include(":sharedutils", ":sharedtests", ":appupdatechecker2", ":commons-io-2.8.0", ":j2v8")
project(":sharedutils").projectDir = file("third_party/SmartTube-droid/SharedModules/sharedutils")
project(":sharedtests").projectDir = file("third_party/SmartTube-droid/SharedModules/sharedtests")
project(":appupdatechecker2").projectDir = file("third_party/SmartTube-droid/SharedModules/appupdatechecker2")
project(":commons-io-2.8.0").projectDir = file("third_party/SmartTube-droid/SharedModules/commons-io-2.8.0")
project(":j2v8").projectDir = file("third_party/SmartTube-droid/SharedModules/j2v8")

include(":mediaserviceinterfaces", ":youtubeapi")
project(":mediaserviceinterfaces").projectDir = file("third_party/SmartTube-droid/MediaServiceCore/mediaserviceinterfaces")
project(":youtubeapi").projectDir = file("third_party/SmartTube-droid/MediaServiceCore/youtubeapi")

// Patched ExoPlayer 2.10.6 used by SmartTube's common/player stack.
val exo = "third_party/SmartTube-droid/exoplayer-amzn-2.10.6"
listOf(
    "library" to "library/all",
    "library-core" to "library/core",
    "library-dash" to "library/dash",
    "library-sabr" to "library/sabr",
    "library-hls" to "library/hls",
    "library-smoothstreaming" to "library/smoothstreaming",
    "library-ui" to "library/ui",
    "testutils" to "testutils",
    "testutils-robolectric" to "testutils_robolectric",
    "extension-ffmpeg" to "extensions/ffmpeg",
    "extension-flac" to "extensions/flac",
    "extension-cast" to "extensions/cast",
    "extension-cronet" to "extensions/cronet",
    "extension-mediasession" to "extensions/mediasession",
    "extension-okhttp" to "extensions/okhttp",
    "extension-opus" to "extensions/opus",
    "extension-vp9" to "extensions/vp9",
    "extension-leanback" to "extensions/leanback",
    "extension-workmanager" to "extensions/workmanager"
).forEach { (name, path) ->
    include(":exoplayer-$name")
    project(":exoplayer-$name").projectDir = file("$exo/$path")
}
