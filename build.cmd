@rem starting build

set APK="./app/build/outputs/apk/app-debug.apk"
gradle -q  assembleDebug && adb install -r %APK% && adb shell am start  -n "com.pairapp/com.pairapp.ui.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER