# Mooncake Android

[![AppVeyor Build Status](https://ci.appveyor.com/api/projects/status/232a8tadrrn8jv0k/branch/master?svg=true)](https://ci.appveyor.com/project/cgutman/moonlight-android/branch/master)
[![Translation Status](https://hosted.weblate.org/widgets/moonlight/-/moonlight-android/svg-badge.svg)](https://hosted.weblate.org/projects/moonlight/moonlight-android/)

[Mooncake for Android](https://github.com/sebastianazarraga-gif/mooncake-android) is an open source client for NVIDIA GameStream and [Sunshine](https://github.com/LizardByte/Sunshine).

Mooncake for Android will allow you to stream your full collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Mooncake dosent have a [PC client](https://github.com/moonlight-stream/moonlight-qt) and [iOS/tvOS client](https://github.com/moonlight-stream/moonlight-ios). So kindly use the other hosts and clients with different devices


## Downloads
* [APK](https://github.com/sebastianazarraga-gif/mooncake-android/releases)

## Building
* Install Android Studio and the Android NDK
* Run ‘git submodule update --init --recursive’ from within moonlight-android/
* In moonlight -android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio or gradle

## Authors

* [Sebastian azarraga](https://github.com/sebastianazarraga-gif)  


Mooncake is the work of a solo student and was
started as a fork of [Moonlight](ghttps://github.com/moonlight-android/releases)
