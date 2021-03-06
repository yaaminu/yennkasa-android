# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\Null-Pointer\AppData\Local\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}


# Required for Parse
-keepattributes *Annotation*
-keepattributes Signature
-dontwarn com.squareup.**
-dontwarn okio.**
-dontwarn com.parse.**
-keep class com.parse.** { *; }
#---------------------------------butterknife-------------------------------------------------#
# Retain generated class which implement Unbinder.
-keep class butterknife.** { *; }
-dontwarn butterknife.internal.**
-keep class **$$ViewBinder { *; }

-keepclasseswithmembernames class * {
    @butterknife.* <fields>;
}

-keepclasseswithmembernames class * {
    @butterknife.* <methods>;
}

-keep class com.squareup.okhttp.** { *; }
-keep class retrofit.** { *; }
-keep interface com.squareup.okhttp.** { *; }

-dontwarn com.squareup.okhttp.**
-dontwarn okio.**
-dontwarn retrofit.**
-dontwarn rx.**

-keepclasseswithmembers class * {
    @retrofit.http.* <methods>;
}

# If in your rest service interface you use methods with Callback argument.
-keepattributes Exceptions

# If your rest service methods throw custom exceptions, because you've defined an ErrorHandler.
-keepattributes Signature

#-------------------------sinch------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}

-dontwarn org.apache.http.annotation.**

-keep class com.sinch.** { *; }
-keep interface com.sinch.** { *; }
-keep class org.webrtc.** { *; }
#-------------------------------end sinch-----------------

######## calligraphy######################################

-keep class uk.co.chrisjenx.calligraphy.* { *; }
-keep class uk.co.chrisjenx.calligraphy.*$* { *; }
##################### calligraphy##############################

# org.spongycastle.pro: Spongy Castle.
-keep class org.spongycastle.**
-dontwarn org.spongycastle.jce.provider.X509LDAPCertStoreSpi
-dontwarn org.spongycastle.x509.util.LDAPStoreHelper
##############################################

# sinch verification library
-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class com.sinch.** extends android.os.AsyncTask {
    *;
}

-keep,includedescriptorclasses class com.sinch.** {
    *;
}
