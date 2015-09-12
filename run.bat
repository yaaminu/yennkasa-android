@rem starting build

set APK="./app/build/outputs/apk/app-debug.apk"
gradle assembleDebug && adb install -r %APK%

