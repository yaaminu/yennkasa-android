@rem starting build

set APK="./app/build/outputs/apk/app-debug.apk"
nodemon -e java -w ./app/src/main -x gradle assembleDebug
