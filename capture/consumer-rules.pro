# ── Attributes ──────────────────────────────────────────────
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, Exceptions

# ── Gson: request/response models (reflection on field names) ─
-keepclassmembers class app.gov.uidai.registration.model.** { <fields>; }
-keep class app.gov.uidai.registration.model.** { <init>(...); }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# ── Retrofit (suspend funs + generic return types) ──────────
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface app.gov.uidai.registration.data.remote.api.** { *; }
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*

# ── Jackson XML + Woodstox (PidOptions / SDKResponse XML) ───
#-keep class in.gov.uidai.network.** { *; }
#-keep class kotlin.Metadata { *; }
#-keep class com.fasterxml.jackson.dataformat.xml.** { *; }
#-keep class com.ctc.wstx.** { *; }
#-keep class org.codehaus.stax2.** { *; }
#-keep class javax.xml.stream.** { *; }
#-keepclassmembers class * {
#    @com.fasterxml.jackson.annotation.* <fields>;
#    @com.fasterxml.jackson.annotation.* <methods>;
#    @com.fasterxml.jackson.annotation.* <init>(...);
#}
#-dontwarn com.ctc.wstx.**
#-dontwarn org.codehaus.stax2.**
#-dontwarn java.beans.**
#-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry
#-dontwarn aQute.bnd.annotation.spi.ServiceProvider
#-dontwarn org.slf4j.impl.StaticLoggerBinder

# ── JNI ─────────────────────────────────────────────────────
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class app.gov.uidai.capture.utils.nativelib.** { *; }
-keep class dev.keiji.jp2.** { *; }

# ── MediaPipe / TFLite (JNI + protobuf reflection) ──────────
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn org.tensorflow.lite.**

# ── No logging in release ───────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}