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
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LicenseManager(private val context: Context) {

    private val SUPABASE_URL = "https://clizjpihxmtoaywvosom.supabase.co"
    private val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNsaXpqcGloeG10b2F5d3Zvc29tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTEwMzU4MTEsImV4cCI6MjEwNjYxMTgxMX0.pi9qZLJkZsa53FMhJr32583_eo8aFf8xP_GssVR6BMA"

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
                val cleanKey = licenseKey.trim()
                    .uppercase()
                    .replace("–", "-")
                    .replace("—", "-")
                    .replace("‐", "-")

                val deviceId = getDeviceId()
                val encodedKey = URLEncoder.encode(cleanKey, "UTF-8")

                val queryUrl = URL("$SUPABASE_URL/rest/v1/license_keys?code=eq.$encodedKey")
                val getConn = queryUrl.openConnection() as HttpURLConnection
                getConn.requestMethod = "GET"
                getConn.setRequestProperty("apikey", SUPABASE_ANON_KEY)
                getConn.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                getConn.connectTimeout = 5000

                if (getConn.responseCode != 200) {
                    val errorStream = getConn.errorStream?.bufferedReader()?.readText() ?: "Brak szczegółów"
                    return@withContext Result.failure(Exception("HTTP ${getConn.responseCode}: $errorStream"))
                }

                val responseText = getConn.inputStream.bufferedReader().readText()
                val jsonArray = JSONArray(responseText)

                if (jsonArray.length() == 0) {
                    return@withContext Result.failure(Exception("Nieprawidłowy kod licencji."))
                }

                val keyObject = jsonArray.getJSONObject(0)
                val isUsed = keyObject.getBoolean("is_used")
                val keyDeviceId = keyObject.optString("device_id", "")

                if (isUsed) {
                    if (keyDeviceId == deviceId) {
                        setPremiumState(true, cleanKey)
                        return@withContext Result.success("Przywrócono licencję PRO!")
                    } else {
                        return@withContext Result.failure(Exception("Ten klucz został już użyty na innym urządzeniu."))
                    }
                }

                val patchUrl = URL("$SUPABASE_URL/rest/v1/license_keys?code=eq.$encodedKey")
                val patchConn = patchUrl.openConnection() as HttpURLConnection
                patchConn.requestMethod = "PATCH"
                patchConn.doOutput = true
                patchConn.setRequestProperty("apikey", SUPABASE_ANON_KEY)
                patchConn.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                patchConn.setRequestProperty("Content-Type", "application/json")

                val body = JSONObject().apply {
                    put("is_used", true)
                    put("device_id", deviceId)
                }

                val writer = OutputStreamWriter(patchConn.outputStream)
                writer.write(body.toString())
                writer.flush()

                if (patchConn.responseCode in 200..299) {
                    setPremiumState(true, cleanKey)
                    Result.success("Licencja PRO aktywowana pomyślnie! 🚀")
                } else {
                    val patchErrorText = patchConn.errorStream?.bufferedReader()?.readText() ?: "Błąd aktywacji"
                    Result.failure(Exception("HTTP ${patchConn.responseCode}: $patchErrorText"))
                }

            } catch (e: Exception) {
                Result.failure(Exception("Błąd połączenia: ${e.message}"))
            }
        }
    }
}