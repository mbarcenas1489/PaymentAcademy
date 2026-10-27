import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
  val propertiesFile = rootProject.file("local.properties")
  if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
}

fun localProperty(name: String): String = localProperties.getProperty(name, "")
fun String.asBuildConfigValue(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
  namespace = "com.example.paymentacademy"
  compileSdk {
    version = release(37)
  }

  defaultConfig {
    applicationId = "com.example.paymentacademy"
    minSdk = 27
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("String", "SUPABASE_URL", localProperty("SUPABASE_URL").asBuildConfigValue())
    buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", localProperty("SUPABASE_PUBLISHABLE_KEY").asBuildConfigValue())
  }

  buildFeatures {
    buildConfig = true
    viewBinding = true
  }

  buildTypes {
    release {
      optimization {
        enable = true
        packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.recyclerview)
  implementation(libs.material)
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.auth)
  implementation(libs.supabase.postgrest)
  implementation(libs.ktor.client.android)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
}
