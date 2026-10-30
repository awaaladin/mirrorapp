# Add project specific ProGuard rules here.
# MediaPipe uses reflection/protobuf internally for its task graphs.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }

# Annotation-processor classes referenced by MediaPipe's dependencies; absent at runtime.
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**
-dontwarn com.squareup.javapoet.**
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**
-dontwarn javax.tools.**
-dontwarn com.google.mediapipe.proto.**
