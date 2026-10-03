# JNI looks the exception class and native method names up by name.
-keep class com.frankzhu.xiangqi.engine.NativeEngineException { *; }
-keep class com.frankzhu.xiangqi.engine.NativePikafish { *; }
-keepclasseswithmembernames class * { native <methods>; }
