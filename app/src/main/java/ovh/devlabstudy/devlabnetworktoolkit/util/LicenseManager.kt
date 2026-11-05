package ovh.devlabstudy.devlabnetworktoolkit.util

import android.content.Context
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ovh.devlabstudy.devlabnetworktoolkit.BuildConfig
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class LicenseManager(private val context: Context) {

    private val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "secure_license_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Standardowe SharedPreferences dla kompatybilności z modułami
    private val defaultPrefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    private val _isPremiumFlow = MutableStateFlow(isPremium())
    val isPremiumFlow: StateFlow<Boolean> = _isPremiumFlow.asStateFlow()

    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    fun isPremium(): Boolean {
        // Sprawdzamy czy w którymkolwiek miejscu jest true
        return securePrefs.getBoolean("KEY_IS_PREMIUM", false) ||
                defaultPrefs.getBoolean("is_premium_unlocked", false) ||
                defaultPrefs.getBoolean("is_pro", false)
    }

    private fun setPremiumState(isPremium: Boolean, key: String?) {
        // 1. Zapis w szyfrowanych prefs
        securePrefs.edit()
            .putBoolean("KEY_IS_PREMIUM", isPremium)
            .putString("KEY_ACTIVE_CODE", key)
            .apply()

        // 2. Zapis w standardowych prefs pod powszechnymi kluczami (dla modułów skanerów)
        defaultPrefs.edit()
            .putBoolean("is_premium_unlocked", isPremium)
            .putBoolean("is_pro", isPremium)
            .putBoolean("is_lan_pro", isPremium)
            .putBoolean("is_port_pro", isPremium)
            .apply()

        // 3. Odświeżenie StateFlow dla UI
        _isPremiumFlow.value = isPremium
    }

    suspend fun activateKey(licenseKey: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (supabaseAnonKey.isBlank()) {
                    return@withContext Result.failure(
                        Exception("Brak klucza Supabase. Ustaw supabaseAnonKey w local.properties.")
                    )
                }

                val cleanKey = licenseKey.trim()
                    .uppercase()
                    .replace("–", "-")
                    .replace("—", "-")
                    .replace("‐", "-")

                val connection = URL("$supabaseUrl/functions/v1/activate-license")
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("apikey", supabaseAnonKey)
                connection.setRequestProperty("Authorization", "Bearer $supabaseAnonKey")
                connection.setRequestProperty("Content-Type", "application/json")

                val body = JSONObject().apply {
                    put("code", cleanKey)
                    put("device_id", getDeviceId())
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body.toString())
                }

                val responseText = (if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                })?.bufferedReader()?.readText().orEmpty()
                val response = runCatching { JSONObject(responseText) }.getOrNull()
                val message = response?.optString("message").orEmpty()

                if (connection.responseCode in 200..299) {
                    setPremiumState(true, cleanKey)
                    Result.success(message.ifBlank { "Licencja PRO aktywowana pomyślnie." })
                } else {
                    Result.failure(
                        Exception(message.ifBlank {
                            "Aktywacja licencji nie powiodła się (HTTP ${connection.responseCode})."
                        })
                    )
                }
            } catch (e: Exception) {
                Result.failure(Exception("Błąd połączenia: ${e.message}"))
            }
        }
    }
}
