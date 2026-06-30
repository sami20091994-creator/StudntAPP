plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // === تفعيل FCM ===
    // 1) ضع ملف google-services.json داخل مجلّد app/
    // 2) أزل التعليق عن السطر التالي ثم Sync/Rebuild:
    // id("com.google.gms.google-services")
}

android {
    namespace = "com.example.studntapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.studntapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // ViewPager2 + Fragment للتنقّل بالسحب بين الصفحات الرئيسية (مثل واتساب)
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Firebase Cloud Messaging — إشعارات فورية بلا اتصال دائم ولا إشعار ثابت
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Networking
    // Pusher مستخدم فقط للتحديث الحيّ داخل شاشة المحادثة (ضمن دورة حياة الشاشة، بلا خدمة دائمة)
    implementation("com.pusher:pusher-java-client:2.4.2")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
