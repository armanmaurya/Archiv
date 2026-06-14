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

# PDFBox-Android
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn org.apache.pdfbox.pdmodel.font.FileSystemFontProvider
-dontwarn org.apache.pdfbox.pdmodel.font.FontMapperImpl
-dontwarn org.apache.fontbox.cff.CFFFont
-dontwarn org.apache.fontbox.util.autodetect.FontFileFinder
-dontwarn org.bouncycastle.**
-dontwarn org.apache.fontbox.util.autodetect.NativeFontDirFinder
-dontwarn javax.xml.stream.**
-dontwarn com.sun.msv.**
-dontwarn org.relaxng.datatype.**
-dontwarn com.tom_roush.pdfbox.pdmodel.font.FileSystemFontProvider
-dontwarn com.tom_roush.pdfbox.pdmodel.font.FontMapperImpl
-dontwarn com.tom_roush.fontbox.util.autodetect.FontFileFinder
-dontwarn com.tom_roush.fontbox.util.autodetect.NativeFontDirFinder