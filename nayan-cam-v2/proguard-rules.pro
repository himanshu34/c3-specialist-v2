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

-keep class co.nayan.c3v2.core.models.** { *; }
-keepclassmembers class co.nayan.c3v2.core.models.** { *; }
-keep class co.nayan.c3v2.core.api.* { *; }
-keep class co.nayan.c3specialist_v2.notification.* { *; }
-keep class co.nayan.c3specialist_v2.screen_sharing.models.* { *; }

-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose
-dump class_files.txt
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

-allowaccessmodification
-keepattributes *Annotation*
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-repackageclasses ''

-dontwarn java.awt.Component
-dontwarn java.awt.Dimension
-dontwarn java.awt.Graphics
-dontwarn java.awt.Image
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ImageObserver
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.swing.JFrame
-dontwarn javax.swing.JPanel

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# Explicitly preserve all serialization members. The Serializable interface
# is only a marker interface, so it wouldn't save them.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Preserve all native method names and the names of their classes.
-keepclasseswithmembernames class * { native <methods>; }

# Preserve static fields of inner classes of R classes that might be accessed
# through introspection.
-keepclassmembers class **.R$* { public static <fields>; }

# Preserve the special static methods that are required in all enumeration classes.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep public class * {
    public protected *;
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-dontwarn okio.**
-dontwarn java.lang.invoke.*
-dontwarn retrofit2.Platform$Java8
-keep class retrofit.** { *; }
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit.http.* <methods>;
}
-keepclasseswithmembers interface * {
    @retrofit.http.* <methods>;
}
-keepclasseswithmembers interface * {
    @retrofit2.* <methods>;
}

-keep class com.google.** {*;}
-keep class com.google.gson.** { *; }
-keep class com.google.inject.** { *; }
-keep class javax.inject.** { *; }

# Gson specific classes
-keep class sun.misc.**

# Application classes that will be serialized/deserialized over Gson
-keep class com.google.gson.examples.android.model.** { *; }

# Retrofit does reflection on generic parameters. InnerClasses is required to use Signature and
# EnclosingMethod is required to use InnerClasses.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit does reflection on method and parameter annotations.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Webrtc
-keep class org.webrtc.**  { *; }
-keep class org.appspot.apprtc.**  { *; }
-keep class de.tavendo.autobahn.**  { *; }
-dontwarn org.webrtc.voiceengine.WebRtcAudioTrack

# Action Cable
-keep class com.hosopy.actioncable.** { *; }
-keep interface com.hosopy.actioncable._* { *; }

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

##---------------End: proguard configuration for Gson  ----------

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit

# With R8 full mode, it sees no subtypes of Retrofit interfaces since they are created with a Proxy
# and replaces all potential values with null. Explicitly keeping the interfaces prevents this.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

-keep class com.facebook.** {
   *;
}

### DJI progaurd rules ###
-keepattributes Exceptions,InnerClasses,*Annotation*,Signature,EnclosingMethod

-dontoptimize
-dontpreverify
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn dji.**
-dontwarn com.dji.**
-dontwarn sun.**
-dontwarn java.**
-dontwarn com.amap.api.**
-dontwarn com.here.**
-dontwarn com.mapbox.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**

-keepclassmembers enum * {
    public static <methods>;
}

-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keep class * extends android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keep,allowshrinking class * extends dji.publics.DJIUI.** {
    public <methods>;
}

-keep class net.sqlcipher.** { *; }

-keep class net.sqlcipher.database.* { *; }

-keep class dji.** { *; }

-keep class com.dji.** { *; }

-keep class com.google.** { *; }

-keep class org.bouncycastle.** { *; }

-keep,allowshrinking class org.** { *; }

-keep class com.squareup.wire.** { *; }

-keep class sun.misc.Unsafe { *; }

-keep class com.secneo.** { *; }

-keep class org.greenrobot.eventbus.**{*;}

-keep class it.sauronsoftware.ftp4j.**{*;}

-keepclasseswithmembers,allowshrinking class * {
    native <methods>;
}

-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keep class androidx.appcompat.widget.SearchView { *; }

-keepclassmembers class * extends android.app.Service
-keepclassmembers public class * extends android.view.View {
    void set*(***);
    *** get*();
}
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
-keep class androidx.** { *; }
-keep class android.media.** { *; }
-keep class okio.** { *; }
-keep class com.lmax.disruptor.** { *; }
-keep class com.qx.wz.dj.rtcm.* { *; }

-dontwarn com.mapbox.services.android.location.LostLocationEngine
-dontwarn com.mapbox.services.android.location.MockLocationEngine
-keepclassmembers class * implements android.arch.lifecycle.LifecycleObserver {
    <init>(...);
}
# ViewModel's empty constructor is considered to be unused by proguard
-keepclassmembers class * extends android.arch.lifecycle.ViewModel {
    <init>(...);
}
# keep Lifecycle State and Event enums values
-keepclassmembers class android.arch.lifecycle.Lifecycle$State { *; }
-keepclassmembers class android.arch.lifecycle.Lifecycle$Event { *; }
# keep methods annotated with @OnLifecycleEvent even if they seem to be unused
# (Mostly for LiveData.LifecycleBoundObserver.onStateChange(), but who knows)
-keepclassmembers class * {
    @android.arch.lifecycle.OnLifecycleEvent *;
}
# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-dontwarn java.awt.Component
-dontwarn java.awt.Dimension
-dontwarn java.awt.Graphics
-dontwarn java.awt.Image
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ImageObserver
-dontwarn javax.swing.JFrame
-dontwarn javax.swing.JPanel
-dontwarn java.lang.invoke.StringConcatFactory

-keepclassmembers class * implements android.arch.lifecycle.LifecycleObserver {
    <init>(...);
}

-keep class * implements android.arch.lifecycle.LifecycleObserver {
    <init>(...);
}
-keepclassmembers class android.arch.** { *; }
-keep class android.arch.** { *; }
-dontwarn android.arch.**

-keep class org.apache.commons.** {*;}


#<------------ utmiss config start------------>
-keep class dji.sdk.utmiss.** { *; }
-keep class utmisslib.** { *; }
#<------------ utmiss config end------------>