// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Firebase (FCM): عرّف الإصدار فقط. لتفعيله: ضع google-services.json في app/
    // ثم أزل التعليق عن سطر التفعيل في app/build.gradle.kts.
    id("com.google.gms.google-services") version "4.4.2" apply false
}