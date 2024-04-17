-keep class co.nayan.canvas.edgedetection.utils.ScannedQuadrilateral
-keep class co.nayan.canvas.sandbox.models.* { *; }
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
#-keep class com.arthenica.mobileffmpeg.Config {
#    native <methods>;
#    void log(long, int, byte[]);
#    void statistics(long, int, float, float, long , int, double, double);
#}
#
#-keep class com.arthenica.mobileffmpeg.AbiDetect {
#    native <methods>;
#}

-keep class com.arthenica.mobileffmpeg.*{ *; }