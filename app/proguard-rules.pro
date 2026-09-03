# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 1. 完整保留 GeckoView 所有的类、接口与方法（class 关键字已默认涵盖 interface）
-keep class org.mozilla.geckoview.** { *; }

# 2. 保留 JNI 与 Native C++ 引擎交互的核心注解
-keep @interface org.mozilla.gecko.annotation.** { *; }
-keep @org.mozilla.gecko.annotation.** class * { *; }
-keepclassmembers class * {
    @org.mozilla.gecko.annotation.** *;
}

# 3. 忽略 GeckoView 内部引用的第三方可选依赖警告，防止 R8 构建报错中断
-dontwarn org.mozilla.geckoview.**
-dontwarn org.mozilla.gecko.**

# 4. 保护 JavaScript 交互接口不被混淆
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}