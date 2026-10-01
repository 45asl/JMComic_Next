# ---------------------------------------------------------------------------
# R8 规则
#
# 这些规则不是「保险起见先加上」，每一条都对应一个只在运行时才暴露的失败模式：
# 混淆把反射/泛型依赖的类名或泛型签名改掉后，编译期一切正常，装到机器上才发现接口全挂。
#
# 说明：kotlinx-serialization、OkHttp、Okio、Coil 都自带 consumer 规则（随 AAR 分发），
# 因此这里只补它们覆盖不到的部分。
# ---------------------------------------------------------------------------

# 保留泛型签名与注解：Retrofit 靠它们解析返回类型与 @SerialName/@Field 等注解
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Retrofit 的服务接口：方法体是注解 + 泛型，R8 看不到调用点，容易整段裁掉
-keep interface com.jmcomic_next.lyqs.data.remote.JmApi { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# kotlinx.serialization 为每个 @Serializable 类生成的伴生 serializer()。
# 反序列化入口是按类型取它的，被裁掉就会在解析时抛 SerializationException
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$$serializer {
    *** descriptor;
}

# 数据模型本身可以混淆（字段名由 @SerialName 决定，不依赖反射），
# 但类名会出现在序列化器查找里，保留名称最稳妥
-keep class com.jmcomic_next.lyqs.data.remote.dto.** { *; }

# OkHttp 在部分平台实现里会引用可选依赖
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
