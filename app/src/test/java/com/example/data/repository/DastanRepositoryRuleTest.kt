package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DastanRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_and_addFavorite_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = DastanRepository(firestore)

        val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.ensureUserProfile() }
        assertTrue(profileResult.isSuccess)
        assertEquals("user", profileResult.getOrThrow().role)
        assertEquals("FREE", profileResult.getOrThrow().membershipPlan)

        val dramaId = "drama_${UUID.randomUUID().toString().take(8)}"
        val addFavResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addFavorite(
                dramaId = dramaId,
                title = "Barbaroslar Season 1",
                posterUrl = "https://giveme5.co/poster.jpg",
                episodeCount = 32
            )
        }
        assertTrue(addFavResult.isSuccess)

        val favorites = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeFavorites(uid).first { list -> list.any { it.dramaId == dramaId } }
        }
        assertTrue(favorites.any { it.dramaId == dramaId })
    }

    @Test
    fun getFavoriteById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = DastanRepository(firestore)
        val dramaId = "drama_${UUID.randomUUID().toString().take(8)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addFavorite(
                dramaId = dramaId,
                title = "Sultan Muhammad Fateh",
                posterUrl = "https://giveme5.co/fateh.jpg",
                episodeCount = 91
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = DastanRepository(firestore)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getFavoriteById(aliceUid, dramaId).getOrThrow()
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    @Test
    fun observeFavorites_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = DastanRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeFavorites(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_dastan@test.com"
        const val BOB_EMAIL = "bob_dastan@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
