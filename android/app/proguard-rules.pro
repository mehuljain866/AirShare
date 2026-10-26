# kotlinx.serialization: keep generated serializers for the job-store DTOs
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.mehuljain.airshare.** {
    kotlinx.serialization.KSerializer serializer(...);
}
