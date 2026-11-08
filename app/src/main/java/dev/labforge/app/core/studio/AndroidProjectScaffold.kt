package dev.labforge.app.core.studio

import java.io.File

/** Generates a portable Android project skeleton; compilation requires a real Android SDK/JDK toolchain. */
object AndroidProjectScaffold {
    fun create(root: File, packageName: String = "dev.example.generated"): List<File> {
        require(packageName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) { "Package Android inválido" }
        val app=root.resolve("app")
        val src=app.resolve("src/main/java/"+packageName.replace('.','/'))
        src.mkdirs()
        val files=linkedMapOf(
            "settings.gradle.kts" to """
                pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
                dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
                rootProject.name = "GeneratedAndroidApp"
                include(":app")
            """.trimIndent(),
            "build.gradle.kts" to """
                plugins {
                    id("com.android.application") version "8.10.1" apply false
                    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
                }
            """.trimIndent(),
            "app/build.gradle.kts" to """
                plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
                android {
                    namespace = "$packageName"
                    compileSdk = 35
                    defaultConfig { applicationId = "$packageName"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
                    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
                    kotlinOptions { jvmTarget = "17" }
                }
                dependencies { implementation("androidx.core:core-ktx:1.16.0"); implementation("androidx.appcompat:appcompat:1.7.1") }
            """.trimIndent(),
            "app/src/main/AndroidManifest.xml" to """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:theme="@android:style/Theme.Material.Light.NoActionBar" android:label="Generated App">
                        <activity android:name=".$" + "MainActivity" android:exported="true">
                            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent(),
            "app/src/main/java/"+packageName.replace('.','/')+"/MainActivity.kt" to """
                package $packageName
                import android.app.Activity
                import android.os.Bundle
                import android.widget.TextView
                class MainActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(TextView(this).apply { text = "Generated with Girassol Native Studio"; textSize = 20f })
                    }
                }
            """.trimIndent(),
            "README.md" to "# Generated Android project\n\nOpen this folder in Android Studio or build with a configured JDK, Gradle and Android SDK.\n"
        )
        return files.map { (path,content) -> root.resolve(path).apply { parentFile?.mkdirs(); writeText(content) } }
    }
}
