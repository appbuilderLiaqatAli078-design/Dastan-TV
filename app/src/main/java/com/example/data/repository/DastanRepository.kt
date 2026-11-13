package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppConfig
import com.example.data.model.CatalogOverride
import com.example.data.model.FavoriteItem
import com.example.data.model.SavedEpisodeItem
import com.example.data.model.UserProfile
import com.example.data.model.WatchHistoryItem
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DastanRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth
        get() = Firebase.auth

    companion object {
        const val APP_ID = "dastan_tv"
        const val AUTHORIZED_ADMIN_EMAIL = "rdxkinglucky@gmail.com"
        const val RUNTIME_ADMIN_EMAIL = "appbuilder078@gmail.com"
        const val GLOBAL_CONFIG_DOC_ID = "global"

        fun isAuthorizedAdminEmail(email: String?, isEmailVerified: Boolean): Boolean {
            if (!isEmailVerified || email.isNullOrBlank()) return false
            val normalized = email.trim().lowercase()
            return normalized == AUTHORIZED_ADMIN_EMAIL || normalized == RUNTIME_ADMIN_EMAIL
        }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun sanitizeDocId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return cleaned.ifBlank { "doc_default" }
    }

    // 1. User Profile & Role Verification
    suspend fun ensureUserProfile(): Result<UserProfile> = runCatching {
        val user = auth.currentUser
            ?: throw IllegalStateException("User must be signed in with Google")
        val uid = user.uid
        val docRef = db.collection("users").document(uid)
        val path = docRef.path
        try {
            // Check custom claims or admin collection or verified admin email
            val tokenResult = try {
                user.getIdToken(false).await()
            } catch (e: Exception) {
                null
            }
            val claimRole = tokenResult?.claims?.get("role") as? String
            val claimAdmin = tokenResult?.claims?.get("admin") as? Boolean ?: false
            val adminDocExists = try {
                db.collection("admins").document(uid).get().await().exists()
            } catch (e: Exception) {
                false
            }
            val isVerifiedAdmin = claimRole == "admin" ||
                claimAdmin ||
                adminDocExists ||
                isAuthorizedAdminEmail(user.email, user.isEmailVerified)

            val email = (user.email ?: "viewer@dastantv.app").take(200)
            val googleDisplayName = (user.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").ifBlank { "Dastan Viewer" }).take(100)
            val googlePhotoUrl = (user.photoUrl?.toString() ?: "").take(500)

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val profile = existingSnap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = uid)

                val resolvedDisplayName = profile.displayName.ifBlank { googleDisplayName }.take(100)
                val resolvedPhotoUrl = googlePhotoUrl.ifBlank { profile.photoUrl }.take(500)
                val resolvedRole = if (isVerifiedAdmin) "admin" else profile.role.ifBlank { "user" }
                val resolvedPlan = if (isVerifiedAdmin) "YEARLY_VIP" else profile.membershipPlan.ifBlank { "FREE" }

                val updates = mutableMapOf<String, Any>(
                    "displayName" to resolvedDisplayName,
                    "photoUrl" to resolvedPhotoUrl,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (isVerifiedAdmin && (profile.role != "admin" || profile.membershipPlan != "YEARLY_VIP")) {
                    updates["role"] = "admin"
                    updates["membershipPlan"] = "YEARLY_VIP"
                }

                docRef.update(updates).await()
                return@runCatching profile.copy(
                    id = uid,
                    userId = uid,
                    email = profile.email.ifBlank { email },
                    displayName = resolvedDisplayName,
                    photoUrl = resolvedPhotoUrl,
                    role = resolvedRole,
                    membershipPlan = resolvedPlan
                )
            } else {
                val assignedRole = if (isVerifiedAdmin) "admin" else "user"
                val assignedPlan = if (isVerifiedAdmin) "YEARLY_VIP" else "FREE"

                val payload = mapOf(
                    "userId" to uid,
                    "email" to email,
                    "displayName" to googleDisplayName,
                    "photoUrl" to googlePhotoUrl,
                    "role" to assignedRole,
                    "membershipPlan" to assignedPlan,
                    "preferredQuality" to "AUTO",
                    "subtitleEnabled" to true,
                    "isBlocked" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                UserProfile(
                    id = uid,
                    userId = uid,
                    email = email,
                    displayName = googleDisplayName,
                    photoUrl = googlePhotoUrl,
                    role = assignedRole,
                    membershipPlan = assignedPlan,
                    preferredQuality = "AUTO",
                    subtitleEnabled = true,
                    isBlocked = false
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        val path = docRef.path
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateUserPreferences(
        displayName: String,
        preferredQuality: String,
        subtitleEnabled: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "displayName" to displayName.trim().ifBlank { "Dastan Viewer" }.take(100),
                    "preferredQuality" to preferredQuality,
                    "subtitleEnabled" to subtitleEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // 2. Favorites CRUD
    fun observeFavorites(userId: String): Flow<List<FavoriteItem>> = flow {
        val path = "users/$userId/favorites"
        emitAll(
            db.collection("users").document(userId).collection("favorites")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FavoriteItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addFavorite(
        dramaId: String,
        title: String,
        posterUrl: String,
        episodeCount: Int
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val safeDramaId = sanitizeDocId(dramaId)
        val docRef = db.collection("users").document(uid)
            .collection("favorites").document(safeDramaId)
        val payload = mapOf(
            "userId" to uid,
            "dramaId" to safeDramaId,
            "title" to title.trim().ifBlank { "Turkish Drama" }.take(200),
            "posterUrl" to posterUrl.take(500),
            "episodeCount" to episodeCount.toLong().coerceIn(0L, 10000L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            safeDramaId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun removeFavorite(dramaId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeDramaId = sanitizeDocId(dramaId)
        val docRef = db.collection("users").document(uid)
            .collection("favorites").document(safeDramaId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun getFavoriteById(targetUserId: String, dramaId: String): Result<FavoriteItem?> = runCatching {
        val safeDramaId = sanitizeDocId(dramaId)
        val docRef = db.collection("users").document(targetUserId)
            .collection("favorites").document(safeDramaId)
        try {
            val snap = docRef.get().await()
            snap.toObject(
                FavoriteItem::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    // 3. Watch History & Continue Watching CRUD
    fun observeWatchHistory(userId: String): Flow<List<WatchHistoryItem>> = flow {
        val path = "users/$userId/watch_history"
        emitAll(
            db.collection("users").document(userId).collection("watch_history")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WatchHistoryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveWatchProgress(
        episodeId: String,
        categoryId: Int,
        episodeTitle: String,
        dramaTitle: String,
        posterUrl: String,
        embedUrl: String,
        progressMs: Long,
        durationMs: Long,
        completed: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeEpisodeId = sanitizeDocId(episodeId)
        val docRef = db.collection("users").document(uid)
            .collection("watch_history").document(safeEpisodeId)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "episodeTitle" to episodeTitle.take(300),
                        "dramaTitle" to dramaTitle.take(200),
                        "posterUrl" to posterUrl.take(500),
                        "embedUrl" to embedUrl.take(500),
                        "progressMs" to progressMs.coerceAtLeast(0L),
                        "durationMs" to durationMs.coerceAtLeast(1000L),
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "episodeId" to safeEpisodeId,
                    "categoryId" to categoryId.toLong().coerceAtLeast(0L),
                    "episodeTitle" to episodeTitle.ifBlank { "Episode" }.take(300),
                    "dramaTitle" to dramaTitle.ifBlank { "Turkish Drama" }.take(200),
                    "posterUrl" to posterUrl.take(500),
                    "embedUrl" to embedUrl.take(500),
                    "progressMs" to progressMs.coerceAtLeast(0L),
                    "durationMs" to durationMs.coerceAtLeast(1000L),
                    "completed" to completed,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun clearWatchHistoryItem(episodeId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeEpisodeId = sanitizeDocId(episodeId)
        val docRef = db.collection("users").document(uid)
            .collection("watch_history").document(safeEpisodeId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // 4. Downloads / Offline Saved Episodes Vault
    fun observeSavedEpisodes(userId: String): Flow<List<SavedEpisodeItem>> = flow {
        val path = "users/$userId/downloads"
        emitAll(
            db.collection("users").document(userId).collection("downloads")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedEpisodeItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveEpisodeToVault(
        episodeId: String,
        categoryId: Int,
        title: String,
        dramaName: String,
        posterUrl: String,
        embedUrl: String,
        synopsis: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeEpisodeId = sanitizeDocId(episodeId)
        val docRef = db.collection("users").document(uid)
            .collection("downloads").document(safeEpisodeId)
        val payload = mapOf(
            "userId" to uid,
            "episodeId" to safeEpisodeId,
            "categoryId" to categoryId.toLong().coerceAtLeast(0L),
            "title" to title.ifBlank { "Episode" }.take(300),
            "dramaName" to dramaName.ifBlank { "Turkish Drama" }.take(200),
            "posterUrl" to posterUrl.take(500),
            "embedUrl" to embedUrl.take(500),
            "synopsis" to synopsis.take(2000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun removeSavedEpisode(episodeId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeEpisodeId = sanitizeDocId(episodeId)
        val docRef = db.collection("users").document(uid)
            .collection("downloads").document(safeEpisodeId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // 5. Catalog Overrides (Admin-controlled visibility, featuring, titles, descriptions, banners)
    fun observeCatalogOverrides(): Flow<List<CatalogOverride>> = flow {
        val path = "catalog_overrides"
        emitAll(
            db.collection(path)
                .whereEqualTo("appId", APP_ID)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CatalogOverride::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveCatalogOverride(
        targetType: String,
        targetId: String,
        customTitle: String,
        customDescription: String,
        customPosterUrl: String,
        isHidden: Boolean,
        isFeatured: Boolean,
        isVipOnly: Boolean,
        priorityOrder: Int
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docId = sanitizeDocId("${targetType.lowercase()}_$targetId")
        val docRef = db.collection("catalog_overrides").document(docId)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "customTitle" to customTitle.take(300),
                        "customDescription" to customDescription.take(3000),
                        "customPosterUrl" to customPosterUrl.take(500),
                        "isHidden" to isHidden,
                        "isFeatured" to isFeatured,
                        "isVipOnly" to isVipOnly,
                        "priorityOrder" to priorityOrder.toLong().coerceIn(0L, 10000L),
                        "updatedBy" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = mapOf(
                    "appId" to APP_ID,
                    "targetType" to targetType,
                    "targetId" to targetId.take(128),
                    "customTitle" to customTitle.take(300),
                    "customDescription" to customDescription.take(3000),
                    "customPosterUrl" to customPosterUrl.take(500),
                    "isHidden" to isHidden,
                    "isFeatured" to isFeatured,
                    "isVipOnly" to isVipOnly,
                    "priorityOrder" to priorityOrder.toLong().coerceIn(0L, 10000L),
                    "updatedBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteCatalogOverride(overrideDocId: String): Result<Unit> = runCatching {
        val safeDocId = sanitizeDocId(overrideDocId)
        val docRef = db.collection("catalog_overrides").document(safeDocId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // 6. Global App Config (Read by all authenticated users, updated by Admin)
    fun observeAppConfig(): Flow<AppConfig> = flow {
        val docRef = db.collection("app_config").document(GLOBAL_CONFIG_DOC_ID)
        val path = docRef.path
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            AppConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: AppConfig(id = GLOBAL_CONFIG_DOC_ID)
                    } else {
                        AppConfig(id = GLOBAL_CONFIG_DOC_ID)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveAppConfig(
        announcementBanner: String,
        maintenanceMode: Boolean,
        yearlyVipPricePkr: Long,
        paymentGatewayEnabled: Boolean,
        lastApiSyncEpochMs: Long,
        lastApiLatencyMs: Long,
        lastApiHealthy: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("app_config").document(GLOBAL_CONFIG_DOC_ID)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "announcementBanner" to announcementBanner.take(500),
                        "maintenanceMode" to maintenanceMode,
                        "yearlyVipPricePkr" to yearlyVipPricePkr.coerceIn(0L, 1000000L),
                        "paymentGatewayEnabled" to paymentGatewayEnabled,
                        "lastApiSyncEpochMs" to lastApiSyncEpochMs.coerceAtLeast(0L),
                        "lastApiLatencyMs" to lastApiLatencyMs.coerceAtLeast(0L),
                        "lastApiHealthy" to lastApiHealthy,
                        "updatedBy" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = mapOf(
                    "appId" to APP_ID,
                    "announcementBanner" to announcementBanner.take(500),
                    "maintenanceMode" to maintenanceMode,
                    "yearlyVipPricePkr" to yearlyVipPricePkr.coerceIn(0L, 1000000L),
                    "paymentGatewayEnabled" to paymentGatewayEnabled,
                    "lastApiSyncEpochMs" to lastApiSyncEpochMs.coerceAtLeast(0L),
                    "lastApiLatencyMs" to lastApiLatencyMs.coerceAtLeast(0L),
                    "lastApiHealthy" to lastApiHealthy,
                    "updatedBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // 7. Admin User Management (Only permitted for verified Admin)
    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun adminUpdateUserMembershipAndStatus(
        targetUserId: String,
        membershipPlan: String,
        isBlocked: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("users").document(targetUserId)
        try {
            docRef.update(
                mapOf(
                    "membershipPlan" to membershipPlan,
                    "isBlocked" to isBlocked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }
}
