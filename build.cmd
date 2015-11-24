@rem starting build

set APK="./app/build/outputs/apk/app-debug.apk"
gradle -q --offline assembleDebug && adb install -r %APK% && adb shell am start  -n "com.idea.pairapp/com.idea.ui.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER