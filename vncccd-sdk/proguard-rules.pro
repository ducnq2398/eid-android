# Add project specific ProGuard rules here.
-keep class com.vncccd.sdk.** { *; }
-keep class org.jmrtd.** { *; }
-keep class net.sf.scuba.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn net.sf.scuba.**
-dontwarn org.jmrtd.**
