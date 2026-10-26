# The downloader and FFmpeg wrappers use native/JNI integration and runtime-loaded classes.
# Keep their API names and members stable while allowing R8 to shrink unrelated code.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }

# Apache Commons Compress 1.12 uses Class.newInstance() to register ZIP extra-field
# implementations in ExtraFieldUtils static initialization. R8 must retain their concrete
# classes, public no-argument constructors, and class shape for this reflection path.
-keep class org.apache.commons.compress.** { *; }

# Preserve method names/signatures referenced from native code.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
