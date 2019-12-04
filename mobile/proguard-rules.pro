# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\sdk/tools/proguard/proguard-android.txt
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
-keepclassmembers class * implements cn.garymb.ygomobile.core.IrrlichtBridge.* { public *;}
-keepclassmembers class * implements android.os.Parcelable { public *;}
-keep class * implements com.bumptech.glide.module.GlideModule{
    *;
}
-keep class net.kk.xml.**{
    public *;
    protected *;
}
-keep class ocgcore.**{
    public *;
    protected *;
}
-keep class cn.garymb.**{
    public *;
    protected *;
}
-keepattributes *Annotation*,InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-dontwarn android.support.**
-dontwarn java.lang.invoke.**
-dontwarn org.slf4j.**
-dontwarn org.chromium.**
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}
-keep class android.support.**{*;}