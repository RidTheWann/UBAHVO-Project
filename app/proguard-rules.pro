# The downloader and FFmpeg wrappers use native/JNI integration and runtime-loaded classes.
# Keep their API names and members stable while allowing R8 to shrink unrelated code.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }

# Preserve method names/signatures referenced from native code.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
