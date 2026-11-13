package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.repository.DastanRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object GoogleAuthHelper {

    private const val TAG = "Auth"
    private const val AUTH_TIMEOUT_MS = 60_000L
    private val isSignInInProgress = AtomicBoolean(false)

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private fun extractGoogleIdToken(credential: Credential): String? {
        return try {
            when (credential) {
                is GoogleIdTokenCredential -> credential.idToken
                is CustomCredential -> {
                    if (
                        credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                        credential.type == TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL ||
                        credential.type.contains("GOOGLE_ID_TOKEN", ignoreCase = true)
                    ) {
                        GoogleIdTokenCredential.createFrom(credential.data).idToken
                    } else {
                        runCatching { GoogleIdTokenCredential.createFrom(credential.data).idToken }.getOrNull()
                    }
                }
                else -> null
            }
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Failed to parse GoogleIdTokenCredential from returned bundle", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error extracting Google ID token", e)
            null
        }
    }

    private suspend fun authenticateFirebaseAndSyncProfile(
        context: Context,
        googleIdToken: String
    ) {
        require(googleIdToken.isNotBlank()) { "Google ID token returned from account chooser was empty." }
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        val authResult = Firebase.auth.signInWithCredential(authCredential).await()
        val signedInUser = authResult.user ?: Firebase.auth.currentUser
            ?: throw IllegalStateException("Firebase signInWithCredential completed but currentUser is null.")

        Log.i(TAG, "Firebase signInWithCredential succeeded for uid=${signedInUser.uid}")

        // Create or update the user's Firestore profile immediately after authentication
        runCatching {
            withTimeout(12_000L) {
                DastanRepository(context.applicationContext).ensureUserProfile().getOrThrow()
            }
        }.onFailure { profileError ->
            Log.w(TAG, "Initial Firestore profile sync deferred to ViewModel: ${profileError.message}")
        }
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope,
        onAuthError: (String) -> Unit = {}
    ) {
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val activityContext = context.findActivity()
        if (activityContext == null) {
            onUnauthenticated()
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id).trim()
        } catch (e: Exception) {
            onUnauthenticated()
            return
        }
        if (clientId.isBlank()) {
            onUnauthenticated()
            return
        }
        if (!isSignInInProgress.compareAndSet(false, true)) {
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        // Single credential option per request for API 36 responsiveness
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                withTimeout(AUTH_TIMEOUT_MS) {
                    val result = credentialManager.getCredential(activityContext, request)
                    val googleIdToken = extractGoogleIdToken(result.credential)
                    if (!googleIdToken.isNullOrBlank()) {
                        authenticateFirebaseAndSyncProfile(context, googleIdToken)
                        withContext(Dispatchers.Main) {
                            onAuthSuccess()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onUnauthenticated()
                        }
                    }
                }
            } catch (e: NoCredentialException) {
                // Normal on first launch when no previously authorized account exists
                withContext(Dispatchers.Main) {
                    onUnauthenticated()
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Auto Google Sign-In cancelled or dismissed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onUnauthenticated()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Auto Google Sign-In did not complete: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onUnauthenticated()
                }
            } finally {
                isSignInInProgress.set(false)
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return
        }

        val activityContext = context.findActivity()
        if (activityContext == null) {
            onAuthError("Unable to launch Google Account Chooser: Activity context is unavailable.")
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id).trim()
        } catch (e: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found.")
            return
        }

        if (clientId.isBlank()) {
            onAuthError("Google Sign-In configuration invalid: Web Client ID is empty.")
            return
        }

        // Prevent duplicate simultaneous sign-in requests
        if (!isSignInInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Ignoring duplicate Google Sign-In click while request is already in flight.")
            return
        }

        // Single credential option per request for interactive sign-in
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                withTimeout(AUTH_TIMEOUT_MS) {
                    val result = try {
                        credentialManager.getCredential(activityContext, primaryRequest)
                    } catch (primaryErr: NoCredentialException) {
                        Log.w(TAG, "GetSignInWithGoogleOption returned NoCredentialException; retrying with GetGoogleIdOption(filterByAuthorizedAccounts=false)", primaryErr)
                        val fallbackOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(clientId)
                            .setAutoSelectEnabled(false)
                            .build()
                        val fallbackRequest = GetCredentialRequest.Builder()
                            .addCredentialOption(fallbackOption)
                            .build()
                        credentialManager.getCredential(activityContext, fallbackRequest)
                    }

                    val googleIdToken = extractGoogleIdToken(result.credential)
                    if (!googleIdToken.isNullOrBlank()) {
                        authenticateFirebaseAndSyncProfile(context, googleIdToken)
                        withContext(Dispatchers.Main) {
                            onAuthSuccess()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onAuthError(
                                "Google Sign-In returned an unsupported credential type (${result.credential.type}). Please try again."
                            )
                        }
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
                val detail = e.message.orEmpty()
                withContext(Dispatchers.Main) {
                    if (
                        detail.contains("28444") ||
                        detail.contains("10:") ||
                        detail.contains("Developer console", ignoreCase = true) ||
                        detail.contains("OAuth", ignoreCase = true)
                    ) {
                        onAuthError(
                            "Google OAuth verification rejected this APK signature ($detail). Please install the latest signed release APK."
                        )
                    } else {
                        onAuthCancelled()
                        onAuthError(
                            "Google Sign-In did not complete. If you selected an account and it closed, ensure Google Play Services is updated and try again."
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Google Sign-In timed out after ${AUTH_TIMEOUT_MS}ms", e)
                withContext(Dispatchers.Main) {
                    onAuthError("Google Sign-In timed out. Please check your internet connection and try again.")
                }
            } catch (e: NoCredentialException) {
                Log.e(TAG, "No Google accounts available on device", e)
                withContext(Dispatchers.Main) {
                    onAuthError("No Google account found on this device. Please add a Google account in Android Settings and try again.")
                }
            } catch (e: GetCredentialException) {
                Log.e(TAG, "CredentialManager error (${e.type}): ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onAuthError(
                        "Google Sign-In failed (${e.errorMessage ?: e.message ?: e.type}). Please try again."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase Google authentication failed", e)
                withContext(Dispatchers.Main) {
                    onAuthError(
                        e.localizedMessage ?: "Firebase Google authentication failed. Please try again."
                    )
                }
            } finally {
                isSignInInProgress.set(false)
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    isSignInInProgress.set(false)
                    Firebase.auth.signOut()
                    withContext(Dispatchers.Main) {
                        onSignOutComplete()
                    }
                }
            }
        }
    }
}
