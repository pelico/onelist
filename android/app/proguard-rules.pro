# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keep class com.onelist.tv.data.** { <fields>; <init>(...); }
-keep class com.google.gson.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader$** { *; }
# Glide GeneratedAppGlideModule (kapt 生成)
-keep class * extends com.bumptech.glide.GeneratedAppGlideModule { <init>(...); }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
# OkHttp Platform class lookup
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ExoPlayer 2
-keep class com.google.android.exoplayer2.** { *; }
-keep class com.google.android.exoplayer2.ui.PlayerView { *; }
-keep class com.google.android.exoplayer2.ext.okhttp.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <init>(...);
}

# AndroidX / Core
-keep class androidx.** { *; }
-dontwarn androidx.**
-keep class android.support.v4.** { *; }

# MultiDex (minSdk 19 < 21 需要保留)
-keep class androidx.multidex.** { *; }

# 保持所有自定义 View 不被移除（因为大量通过代码 new 而非 XML 引用，R8 可能误以为未使用）
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# JSR305 / Nullability 注解
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.ParametersAreNonnullByDefault

# Gson 反序列化可能用到的无参构造器（data 类）
-keepclassmembers class * {
    <init>(...);
}
