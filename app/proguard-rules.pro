# Generated missing rules from R8
-dontwarn co.paystack.android.design.widget.PinPadView$OnSubmitListener
-dontwarn co.paystack.android.design.widget.PinPadView
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Thêm các rules bổ sung cho các thư viện bạn đang dùng

# RabbitMQ (sử dụng SLF4J)
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# Retrofit & OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data models
-keep class com.onefin.posapp.data.model.** { *; }
-keep class com.onefin.posapp.domain.model.** { *; }

# OneFin SDKs
-keep class vn.onefin.** { *; }
-keep class com.onefin.** { *; }
-dontwarn vn.onefin.**

# PayLib (Sunmi)
-keep class com.sunmi.pay.** { *; }
-dontwarn com.sunmi.pay.**

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# EMV NFC Card
-keep class com.github.devnied.emvnfccard.** { *; }
-dontwarn com.github.devnied.emvnfccard.**

# RabbitMQ
-keep class com.rabbitmq.** { *; }
-dontwarn com.rabbitmq.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**