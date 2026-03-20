# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.moodfox.data.local.db.** { *; }

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
-keep class io.ktor.** { *; }

# Kotlinx Serialization
-keepattributes InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.moodfox.**$$serializer { *; }
