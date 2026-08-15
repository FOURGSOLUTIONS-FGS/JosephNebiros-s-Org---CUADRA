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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class CollectorUser(
    val id: String,
    val name: String,
    val email: String,
    val role: String = "COBRADOR", // ADMIN, SUPERVISOR, COBRADOR
    val routeCode: String = "RUTA_BARRANQUILLA_01",
    val photoUrl: String? = null,
    val isGoogleAccount: Boolean = false
)

object AuthManager {
    private const val PREFS_NAME = "cobrador_auth_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_ROUTE_CODE = "route_code"
    private const val KEY_PHOTO_URL = "photo_url"
    private const val KEY_IS_GOOGLE = "is_google"

    // Default Web Client ID placeholder for Google Sign-In with Credential Manager
    private const val DEFAULT_WEB_CLIENT_ID = "514898261832-dummy-oauth-client-id.apps.googleusercontent.com"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedUser(context: Context): CollectorUser? {
        val prefs = getPrefs(context)
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (!isLoggedIn) return null

        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val name = prefs.getString(KEY_USER_NAME, "Cobrador") ?: "Cobrador"
        val email = prefs.getString(KEY_USER_EMAIL, "cobrador@creditos.com") ?: "cobrador@creditos.com"
        val role = prefs.getString(KEY_USER_ROLE, "COBRADOR") ?: "COBRADOR"
        val routeCode = prefs.getString(KEY_ROUTE_CODE, "RUTA_BARRANQUILLA_01") ?: "RUTA_BARRANQUILLA_01"
        val photoUrl = prefs.getString(KEY_PHOTO_URL, null)
        val isGoogle = prefs.getBoolean(KEY_IS_GOOGLE, false)

        return CollectorUser(
            id = id,
            name = name,
            email = email,
            role = role,
            routeCode = routeCode,
            photoUrl = photoUrl,
            isGoogleAccount = isGoogle
        )
    }

    fun saveUser(context: Context, user: CollectorUser) {
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
            .apply()
    }

    fun clearUser(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    /**
     * Authenticate via email and password credentials.
     * Includes pre-configured Admin and Collector credentials.
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

        // Admin Account validation
        if (cleanEmail == "admin@creditos.com" && cleanPass == "JHGK2J!") {
            val adminUser = CollectorUser(
                id = "admin_master_01",
                name = "Administrador General",
                email = "admin@creditos.com",
                role = "ADMIN",
                routeCode = "TODAS LAS RUTAS (ADMIN)",
                isGoogleAccount = false
            )
            saveUser(context, adminUser)
            return Result.success(adminUser)
        }

        // Standard Collector validation
        if (cleanPass.length >= 4) {
            val collectorName = cleanEmail.substringBefore("@")
                .replace(".", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

            val user = CollectorUser(
                id = "user_${cleanEmail.hashCode()}",
                name = collectorName.ifBlank { "Cobrador de Ruta" },
                email = cleanEmail,
                role = if (cleanEmail.contains("admin")) "ADMIN" else "COBRADOR",
                routeCode = "RUTA_BARRANQUILLA_01",
                isGoogleAccount = false
            )
            saveUser(context, user)
            return Result.success(user)
        } else {
            return Result.failure(Exception("La contraseña debe tener al menos 4 caracteres"))
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
                        routeCode = "RUTA_BARRANQUILLA_01",
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
            return@withContext Result.failure(Exception("No se encontró cuenta de Google en el dispositivo. Puedes usar Acceso Rápido."))
        } catch (e: GetCredentialException) {
            Log.e("AuthManager", "Error CredentialManager: ${e.message}")
            return@withContext Result.failure(Exception("Error de autenticación Google: ${e.message}"))
        } catch (e: Exception) {
            Log.e("AuthManager", "Error inesperado en Google Sign In: ${e.message}", e)
            return@withContext Result.failure(Exception("Error al conectar con Google: ${e.message}"))
        }
    }
}
