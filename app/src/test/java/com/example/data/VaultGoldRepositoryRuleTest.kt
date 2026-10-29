package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class VaultGoldRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserInitialized_andAwardReward_creditsExactlyOnceAndRejectsReplay() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@test.com")
        val repo = VaultGoldRepository(firestore)

        val profile = withTimeout(5000L) {
            repo.ensureUserInitialized(uid, "Alice Tester", "alice@test.com", null).getOrThrow()
        }
        assertThat(profile.userId).isEqualTo(uid)

        val eventId = "evt_${UUID.randomUUID().toString().take(8)}"
        val firstAward = withTimeout(5000L) {
            repo.awardReward(
                sourceType = "unity_rewarded_ad",
                sourceId = "BP_Rewarded_Android",
                eventId = eventId,
                title = "Watch Ad & Earn (+1 Coin)",
                amount = 1L,
                verificationLevel = "client_attested",
                isSandboxTestAd = false
            ).getOrThrow()
        }
        assertThat(firstAward.balanceAfter).isEqualTo(1L)

        // Replaying the exact same eventId must be rejected by the idempotency index
        val duplicateAward = withTimeout(5000L) {
            repo.awardReward(
                sourceType = "unity_rewarded_ad",
                sourceId = "BP_Rewarded_Android",
                eventId = eventId,
                title = "Watch Ad & Earn (+1 Coin)",
                amount = 1L,
                verificationLevel = "client_attested",
                isSandboxTestAd = false
            )
        }
        assertThat(duplicateAward.isFailure).isTrue()
    }

    @Test
    fun crossUserWalletRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@test.com")
        val repo = VaultGoldRepository(firestore)
        withTimeout(5000L) {
            repo.ensureUserInitialized(aliceUid, "Alice", "alice@test.com", null).getOrThrow()
        }

        // Sign in as Bob and attempt to read Alice's wallet directly
        signInTestUser("bob_${UUID.randomUUID().toString().take(6)}@test.com")
        val thrown = assertThrows(Exception::class.java) {
            runBlocking {
                withTimeout(5000L) {
                    firestore.collection("users").document(aliceUid)
                        .collection("wallets").document("main")
                        .get().await()
                }
            }
        }
        val fsEx = checkNotNull(thrown.unwrapFirestoreException())
        assertThat(fsEx.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    @Test
    fun observeAppCampaigns_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = VaultGoldRepository(firestore)

        val thrown = assertThrows(Exception::class.java) {
            runBlocking {
                withTimeout(4000L) {
                    repo.observeAppCampaigns().first()
                }
            }
        }
        val fsEx = checkNotNull(thrown.unwrapFirestoreException())
        assertThat(fsEx.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
}
