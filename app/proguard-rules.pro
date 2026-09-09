# Add project specific ProGuard rules here.
# MediaPipe uses reflection/protobuf internally for its task graphs.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
