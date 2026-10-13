package com.familyapp.core.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object NetworkClient {
    private const val PREFS_NAME = "family_app_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_FAMILY_ID = "family_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"

    const val TAILSCALE_BASE_URL = "http://100.97.53.61:8001/"
    const val LAN_BASE_URL = "http://192.168.1.3:8001/"
    const val DEFAULT_BASE_URL = TAILSCALE_BASE_URL

    private var prefs: SharedPreferences? = null

    var baseUrl: String = DEFAULT_BASE_URL
        private set

    var authToken: String? = null
        private set

    var currentFamilyId: String = "default-family"
        private set

    private val _familyIdFlow = MutableStateFlow("default-family")
    val familyIdFlow: StateFlow<String> = _familyIdFlow.asStateFlow()

    var currentUserName: String? = null
        private set

    var currentUserEmail: String? = null
        private set

    @Volatile
    private var _apiService: ApiService? = null

    val apiService: ApiService
        get() {
            return _apiService ?: synchronized(this) {
                _apiService ?: buildRetrofit().create(ApiService::class.java).also { _apiService = it }
            }
        }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.let {
            baseUrl = it.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
            authToken = it.getString(KEY_AUTH_TOKEN, null)
            currentFamilyId = it.getString(KEY_FAMILY_ID, "default-family") ?: "default-family"
            currentUserName = it.getString(KEY_USER_NAME, null)
            currentUserEmail = it.getString(KEY_USER_EMAIL, null)
        }
        _familyIdFlow.value = currentFamilyId
        Log.d("NetworkClient", "Initialized with baseUrl: $baseUrl, hasToken: ${authToken != null}")
        rebuildRetrofit()
    }

    fun updateBaseUrl(rawUrl: String): String {
        val sanitized = sanitizeUrl(rawUrl)
        baseUrl = sanitized
        prefs?.edit()?.putString(KEY_BASE_URL, sanitized)?.apply()
        Log.d("NetworkClient", "Updated baseUrl to: $sanitized")
        rebuildRetrofit()
        return sanitized
    }

    fun setAuth(token: String, familyId: String, name: String, email: String) {
        authToken = token
        currentFamilyId = familyId
        currentUserName = name
        currentUserEmail = email
        _familyIdFlow.value = familyId

        prefs?.edit()
            ?.putString(KEY_AUTH_TOKEN, token)
            ?.putString(KEY_FAMILY_ID, familyId)
            ?.putString(KEY_USER_NAME, name)
            ?.putString(KEY_USER_EMAIL, email)
            ?.apply()

        Log.d("NetworkClient", "Saved auth for user $email, family: $familyId")
        rebuildRetrofit()
    }

    fun clearAuth() {
        authToken = null
        currentFamilyId = "default-family"
        currentUserName = null
        currentUserEmail = null
        _familyIdFlow.value = "default-family"

        prefs?.edit()
            ?.remove(KEY_AUTH_TOKEN)
            ?.remove(KEY_FAMILY_ID)
            ?.remove(KEY_USER_NAME)
            ?.remove(KEY_USER_EMAIL)
            ?.apply()

        Log.d("NetworkClient", "Cleared auth session")
        rebuildRetrofit()
    }

    fun isLoggedIn(): Boolean {
        return !authToken.isNullOrBlank()
    }

    private fun sanitizeUrl(url: String): String {
        var trimmed = url.trim()
        if (trimmed.isEmpty()) return DEFAULT_BASE_URL
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        if (!trimmed.endsWith("/")) {
            trimmed = "$trimmed/"
        }
        return trimmed
    }

    private val authInterceptor = Interceptor { chain ->
        val req = chain.request()
        val builder = req.newBuilder()
        authToken?.let {
            if (it.isNotBlank()) {
                builder.addHeader("Authorization", "Bearer $it")
            }
        }
        chain.proceed(builder.build())
    }

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val sseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for continuous SSE stream
            .build()
    }

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun rebuildRetrofit() {
        synchronized(this) {
            try {
                _apiService = buildRetrofit().create(ApiService::class.java)
            } catch (e: Exception) {
                Log.e("NetworkClient", "Error building Retrofit with baseUrl: $baseUrl", e)
            }
        }
    }

    suspend fun pingHealth(rawUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val sanitized = sanitizeUrl(rawUrl)
        val startTime = System.currentTimeMillis()
        try {
            val request = okhttp3.Request.Builder()
                .url("${sanitized}api/health")
                .get()
                .build()

            val fastClient = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build()

            fastClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    Pair(true, "✓ Conectado exitosamente (${latency} ms)")
                } else {
                    Pair(false, "El servidor respondió con código HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val msg = when {
                e is java.net.ConnectException -> "No se pudo conectar a $sanitized (${latency} ms). Comprueba que el servidor o Tailscale estén activos."
                e is java.net.SocketTimeoutException -> "Tiempo de espera agotado al conectar a $sanitized."
                else -> "Error de conexión: ${e.localizedMessage ?: e.message}"
            }
            Pair(false, msg)
        }
    }
}
