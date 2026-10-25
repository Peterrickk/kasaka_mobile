# kotlinx.serialization and Room ship their own consumer rules.
# Keep the BLE payload model's serializer so device JSON parsing survives R8.
-keep,includedescriptorclasses class com.sakasense.kasaka.device.**$$serializer { *; }
-keepclassmembers class com.sakasense.kasaka.device.** {
    *** Companion;
}
