@rem starting build

set APK="./app/build/outputs/apk/app-debug.apk"
nodemon -e java -i */build -x gradle assembleDebug
