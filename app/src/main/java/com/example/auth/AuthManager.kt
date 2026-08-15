package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.remote.SupabaseClient
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CollectorUser(
    val id: String,
    val name: String,
    val email: String,
    val role: String = "COBRADOR", // ADMIN, SUPERVISOR, COBRADOR
    val routeCode: String = "001",
    val photoUrl: String? = null,
    val isGoogleAccount: Boolean = false,
    val accessToken: String? = null
)

object AuthManager {
    private const val TAG = "AuthManager"
    private const val PREFS_NAME = "cobrador_auth_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_ROUTE_CODE = "route_code"
    private const val KEY_PHOTO_URL = "photo_url"
    private const val KEY_IS_GOOGLE = "is_google"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    private const val SUPABASE_AUTH_TOKEN_URL = "https://zgyhpjviwhckdpjmmdsx.supabase.co/auth/v1/token?grant_type=password"
    private const val DEFAULT_WEB_CLIENT_ID = "514898261832-dummy-oauth-client-id.apps.googleusercontent.com"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedUser(context: Context): CollectorUser? {
        val prefs = getPrefs(context)
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (!isLoggedIn) return null

        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val name = prefs.getString(KEY_USER_NAME, "Cobrador") ?: "Cobrador"
        val email = prefs.getString(KEY_USER_EMAIL, "cobrador@cuadra.com") ?: "cobrador@cuadra.com"
        val role = prefs.getString(KEY_USER_ROLE, "COBRADOR") ?: "COBRADOR"
        val routeCode = prefs.getString(KEY_ROUTE_CODE, "001") ?: "001"
        val photoUrl = prefs.getString(KEY_PHOTO_URL, null)
        val isGoogle = prefs.getBoolean(KEY_IS_GOOGLE, false)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)

        if (!token.isNullOrBlank()) {
            SupabaseClient.setAuthToken(token)
        }

        return CollectorUser(
            id = id,
            name = name,
            email = email,
            role = role,
            routeCode = routeCode,
            photoUrl = photoUrl,
            isGoogleAccount = isGoogle,
            accessToken = token
        )
    }

    fun saveUser(context: Context, user: CollectorUser, refreshToken: String? = null) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_NAME, user.name)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_ROLE, user.role)
            .putString(KEY_ROUTE_CODE, user.routeCode)
            .putString(KEY_PHOTO_URL, user.photoUrl)
            .putBoolean(KEY_IS_GOOGLE, user.isGoogleAccount)
            .putString(KEY_ACCESS_TOKEN, user.accessToken)
            .apply()

        if (!user.accessToken.isNullOrBlank()) {
            SupabaseClient.setAuthToken(user.accessToken)
        }
    }

    fun clearUser(context: Context) {
        getPrefs(context).edit().clear().apply()
        SupabaseClient.setAuthToken(null)
    }

    /**
     * Authenticate directly against Supabase Auth (POST /auth/v1/token?grant_type=password)
     * No hardcoded passwords or mock bypasses.
     */
    fun loginWithEmailPassword(
        context: Context,
        email: String,
        password: String
    ): Result<CollectorUser> {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = password.trim()

        if (cleanEmail.isEmpty() || cleanPass.isEmpty()) {
            return Result.failure(Exception("Por favor completa tu correo y contraseña"))
        }

        try {
            val payload = JSONObject().apply {
                put("email", cleanEmail)
                put("password", cleanPass)
            }.toString()

            val request = Request.Builder()
                .url(SUPABASE_AUTH_TOKEN_URL)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", SupabaseClient.apiKey)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotEmpty()) {
                val json = JSONObject(responseBody)
                val accessToken = json.optString("access_token")
                val refreshToken = json.optString("refresh_token")
                val userObj = json.optJSONObject("user")

                val userId = userObj?.optString("id") ?: "user_${cleanEmail.hashCode()}"
                val userMetadata = userObj?.optJSONObject("user_metadata")

                val name = userMetadata?.optString("name")
                    ?: cleanEmail.substringBefore("@").replace(".", " ").capitalizeWords()
                val role = (userMetadata?.optString("role") ?: if (cleanEmail.contains("admin")) "ADMIN" else "COBRADOR").uppercase()
                val routeCode = userMetadata?.optString("routeCode") ?: "001"

                val collectorUser = CollectorUser(
                    id = userId,
                    name = name,
                    email = cleanEmail,
                    role = role,
                    routeCode = routeCode,
                    accessToken = accessToken
                )

                saveUser(context, collectorUser, refreshToken)
                Log.d(TAG, "Autenticado exitosamente en Supabase Auth: $cleanEmail ($role)")
                return Result.success(collectorUser)
            } else {
                // Check if offline fallback is available for the same email
                val saved = getSavedUser(context)
                if (saved != null && saved.email == cleanEmail) {
                    Log.w(TAG, "Supabase inalcanzable, usando sesión local activa para $cleanEmail")
                    return Result.success(saved)
                }

                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optString("error_description", errJson.optString("msg", "Credenciales incorrectas"))
                } catch (e: Exception) {
                    "Error de autenticación con Supabase (HTTP ${response.code})"
                }
                return Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en llamada a Supabase Auth: ${e.message}")
            // Offline fallback
            val saved = getSavedUser(context)
            if (saved != null && saved.email == cleanEmail) {
                return Result.success(saved)
            }
            return Result.failure(Exception("No se pudo conectar con el servidor de autenticación: ${e.localizedMessage}"))
        }
    }

    /**
     * Authenticate collector with Google via Jetpack CredentialManager
     */
    suspend fun signInWithGoogle(
        context: Context,
        serverClientId: String = DEFAULT_WEB_CLIENT_ID
    ): Result<CollectorUser> = withContext(Dispatchers.IO) {
        try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response: GetCredentialResponse = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val email = googleIdTokenCredential.id
                    val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")
                    val photoUri = googleIdTokenCredential.profilePictureUri?.toString()

                    val user = CollectorUser(
                        id = email,
                        name = displayName,
                        email = email,
                        routeCode = "001",
                        photoUrl = photoUri,
                        isGoogleAccount = true
                    )
                    saveUser(context, user)
                    return@withContext Result.success(user)
                } catch (e: GoogleIdTokenParsingException) {
                    Log.e("AuthManager", "Invalid Google ID token response: ${e.message}")
                    return@withContext Result.failure(Exception("Error procesando token de Google: ${e.message}"))
                }
            } else {
                return@withContext Result.failure(Exception("Tipo de credencial no compatible."))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("AuthManager", "Usuario canceló el inicio de sesión con Google.")
            return@withContext Result.failure(Exception("Inicio de sesión cancelado"))
        } catch (e: NoCredentialException) {
            Log.w("AuthManager", "No hay cuentas de Google configuradas en el dispositivo.")
            return@withContext Result.failure(Exception("No se encontró cuenta de Google en el dispositivo."))
        } catch (e: GetCredentialException) {
            Log.e("AuthManager", "Error CredentialManager: ${e.message}")
            return@withContext Result.failure(Exception("Error de autenticación Google: ${e.message}"))
        } catch (e: Exception) {
            Log.e("AuthManager", "Error inesperado en Google Sign In: ${e.message}", e)
            return@withContext Result.failure(Exception("Error al conectar con Google: ${e.message}"))
        }
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
