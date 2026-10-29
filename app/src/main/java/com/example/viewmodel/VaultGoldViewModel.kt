package com.example.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ads.UnityAdManager
import com.example.ads.UnityAdsConfig
import com.example.data.VaultGoldRepository
import com.example.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class VaultGoldViewModel(
    private val repository: VaultGoldRepository,
    private val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String?,
    private val userPhotoUrl: String?,
    val isGuestUser: Boolean = currentUserId.startsWith("guest_")
) : ViewModel() {

    val unityAdManager = UnityAdManager()

    private val _appSettings = MutableStateFlow(FoundwayAppSettings())
    val appSettings: StateFlow<FoundwayAppSettings> = _appSettings.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    // Section 7: Active Claim-With-Ad reward (stays claimable for 10 minutes)
    private val _activePendingClaim = MutableStateFlow<PendingClaimReward?>(null)
    val activePendingClaim: StateFlow<PendingClaimReward?> = _activePendingClaim.asStateFlow()

    // Saved pending claim if user taps "Skip" (can still be claimed within 10 minutes)
    private val _savedPendingClaim = MutableStateFlow<PendingClaimReward?>(null)
    val savedPendingClaim: StateFlow<PendingClaimReward?> = _savedPendingClaim.asStateFlow()

    // Section 2: Guest trying to Redeem -> prompt Google Sign-In & Link
    private val _showGuestLinkModal = MutableStateFlow(false)
    val showGuestLinkModal: StateFlow<Boolean> = _showGuestLinkModal.asStateFlow()

    private val _dailyAdsWatched = MutableStateFlow(0)
    val dailyAdsWatched: StateFlow<Int> = _dailyAdsWatched.asStateFlow()

    val dailyAdLimit: Int
        get() = if (isGuestUser) _appSettings.value.dailyAdLimitGuest else _appSettings.value.dailyAdLimitUser

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { e ->
            Log.w(TAG, "Profile stream error", e)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val wallet: StateFlow<Wallet> = repository.observeWallet(currentUserId)
        .catch { e ->
            Log.w(TAG, "Wallet stream error", e)
            emit(Wallet(id = "main", userId = currentUserId))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), Wallet(id = "main", userId = currentUserId))

    val ledger: StateFlow<UiState<List<LedgerEntry>>> = repository.observeLedger(currentUserId)
        .map<List<LedgerEntry>, UiState<List<LedgerEntry>>> { UiState.Success(it) }
        .catch { e -> emit(UiState.Error(e.message ?: "Failed to load ledger")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val appCampaigns: StateFlow<UiState<List<AppCampaign>>> = repository.observeAppCampaigns()
        .map<List<AppCampaign>, UiState<List<AppCampaign>>> { UiState.Success(it) }
        .catch { e -> emit(UiState.Error(e.message ?: "Failed to load missions")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val taskClaims: StateFlow<List<TaskClaim>> = repository.observeTaskClaims(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val puzzleAttempts: StateFlow<List<PuzzleAttempt>> = repository.observePuzzleAttempts(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adSessions: StateFlow<List<AdSession>> = repository.observeAdSessions(currentUserId)
        .onEach { list ->
            _dailyAdsWatched.value = list.count { it.status == "approved" }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val videoCampaigns: StateFlow<List<VideoCampaign>> = repository.observeVideoCampaigns()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val rewardsCatalog: StateFlow<List<RewardCatalogItem>> = repository.observeRewardsCatalog()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val redemptions: StateFlow<List<RedemptionRequest>> = repository.observeRedemptions(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allRedemptionsForAdmin: StateFlow<List<RedemptionRequest>> = repository.observeAllRedemptionsForAdmin()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val redeemCodes: StateFlow<List<RedeemCodeInfo>> = repository.observeRedeemCodes()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val supportTickets: StateFlow<List<SupportTicket>> = repository.observeSupportTickets(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val adminAuditLogs: StateFlow<List<AdminAuditLog>> = userProfile
        .map { it?.role != null && it.role != "user" }
        .distinctUntilChanged()
        .flatMapLatest { isAdmin ->
            if (isAdmin) {
                repository.observeAdminAuditLogs().catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userRedeemedCodes: StateFlow<Set<String>> = repository.observeUserRedeemedCodeKeys(currentUserId)
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val promoCodes: StateFlow<List<Pair<RedeemPromoCode, RedeemCodeStatus>>> = combine(
        repository.observePromoCodes(),
        userRedeemedCodes
    ) { codes, redeemed ->
        codes.map { item ->
            val claimedUsers = if (redeemed.contains(item.code)) setOf(currentUserId) else emptySet()
            Pair(item, item.evaluateStatus(claimedUsers, currentUserId))
        }
    }.catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _activeAppMode = MutableStateFlow(AppMode.TASK_AND_EARN)
    val activeAppMode: StateFlow<AppMode> = _activeAppMode.asStateFlow()

    fun setAppMode(mode: AppMode) {
        _activeAppMode.value = mode
    }

    private val _videoCategoryFilter = MutableStateFlow("All")
    val videoCategoryFilter: StateFlow<String> = _videoCategoryFilter.asStateFlow()

    private val _videoSearchQuery = MutableStateFlow("")
    val videoSearchQuery: StateFlow<String> = _videoSearchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val videoPosts: StateFlow<List<VideoPost>> = combine(
        _videoCategoryFilter,
        _videoSearchQuery
    ) { cat, query ->
        Pair(cat, query)
    }.flatMapLatest { (cat, query) ->
        repository.observeVideoPosts(cat, query)
    }.catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _selectedWatchVideo = MutableStateFlow<VideoPost?>(null)
    val selectedWatchVideo: StateFlow<VideoPost?> = _selectedWatchVideo.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedVideoComments: StateFlow<List<VideoComment>> = _selectedWatchVideo
        .flatMapLatest { vid ->
            if (vid != null) repository.observeVideoComments(vid.id) else flowOf(emptyList())
        }.catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val recommendedVideos: StateFlow<List<VideoPost>> = _selectedWatchVideo
        .flatMapLatest { vid ->
            repository.observeRecommendedVideos(vid?.id ?: "")
        }.catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val subscribedChannelIds: StateFlow<Set<String>> = repository.observeSubscriptions()
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val creatorChannel: StateFlow<CreatorChannel> = repository.observeCreatorChannel(currentUserId)
        .catch { emit(CreatorChannel(channelId = "chan_$currentUserId", ownerUid = currentUserId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CreatorChannel(channelId = "chan_$currentUserId", ownerUid = currentUserId))

    val monetizationApplications: StateFlow<List<CreatorMonetizationApplication>> = repository.observeMonetizationApplications()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val monetizationApplication: StateFlow<CreatorMonetizationApplication?> = monetizationApplications
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _showUploadVideoDialog = MutableStateFlow(false)
    val showUploadVideoDialog: StateFlow<Boolean> = _showUploadVideoDialog.asStateFlow()

    fun openUploadVideoDialog() { _showUploadVideoDialog.value = true }
    fun closeUploadVideoDialog() { _showUploadVideoDialog.value = false }

    init {
        viewModelScope.launch {
            val profileRes = repository.ensureUserInitialized(
                uid = currentUserId,
                displayName = userDisplayName,
                email = userEmail,
                photoUrl = userPhotoUrl
            )
            val profile = profileRes.getOrNull()
            if (profile?.role != null && profile.role != "user") {
                repository.seedInitialCatalogsIfEmpty()
            }
        }
    }

    fun initUnityAds(context: Context, forceReload: Boolean = false) {
        unityAdManager.initializeAndLoad(context, viewModelScope, forceReload = forceReload)
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun postMessage(msg: String) {
        _bannerMessage.value = msg
    }

    fun openGuestLinkModal() {
        _showGuestLinkModal.value = true
    }

    fun dismissGuestLinkModal() {
        _showGuestLinkModal.value = false
    }

    fun updateProfileDisplayName(newName: String) {
        viewModelScope.launch {
            repository.updateDisplayName(newName).fold(
                onSuccess = { _bannerMessage.value = "Name updated to '${newName.trim()}'." },
                onFailure = { e -> _bannerMessage.value = e.localizedMessage ?: "Could not update name." }
            )
        }
    }

    // --- CLAIM-WITH-AD FLOW (Daily Bonus, Panda 3x3, Brain Puzzles, Quiz) ---

    fun prepareDailyCheckIn() {
        viewModelScope.launch {
            val coins = _appSettings.value.dailyCheckInCoins
            repository.prepareDailyCheckInClaim(coins).fold(
                onSuccess = { pending ->
                    _activePendingClaim.value = pending
                    _savedPendingClaim.value = pending
                },
                onFailure = { e ->
                    _bannerMessage.value = e.localizedMessage ?: "Daily +$coins already claimed today."
                }
            )
        }
    }

    fun claimDailyCheckIn() {
        prepareDailyCheckIn()
    }

    fun prepareQuizClaim(questionId: String, questionTitle: String, isCorrect: Boolean) {
        if (!isCorrect) {
            _bannerMessage.value = "Not quite right. Try another answer!"
            return
        }
        val coins = _appSettings.value.quizRewardCoins
        val pending = PendingClaimReward(
            id = "claim_quiz_$questionId",
            sourceType = "knowledge_quiz",
            sourceId = questionId,
            eventId = "${questionId}_${repository.todayDateString()}",
            headline = "Quiz complete",
            coins = coins
        )
        _activePendingClaim.value = pending
        _savedPendingClaim.value = pending
    }

    fun submitQuizAnswer(questionId: String, questionTitle: String, isCorrect: Boolean) {
        prepareQuizClaim(questionId, questionTitle, isCorrect)
    }

    fun completePuzzleChallenge(
        gameId: String,
        category: String,
        difficulty: String,
        score: Long,
        movesCount: Long,
        durationSeconds: Long,
        seedToken: String,
        baseReward: Long
    ) {
        viewModelScope.launch {
            val pandaWinsToday = puzzleAttempts.value.count { it.gameId == gameId }
            if (gameId == "find_the_panda_3x3" && pandaWinsToday >= _appSettings.value.pandaDailyLimit) {
                _bannerMessage.value = "Daily Panda game limit reached (${_appSettings.value.pandaDailyLimit}/day)."
                return@launch
            }

            val rewardAmount = if (gameId == "find_the_panda_3x3") {
                _appSettings.value.pandaRewardCoins
            } else {
                baseReward.coerceIn(2L, 6L)
            }

            repository.recordPuzzleWinForAdClaim(
                gameId = gameId,
                category = category,
                difficulty = difficulty,
                score = score,
                movesCount = movesCount,
                durationSeconds = durationSeconds,
                seedToken = seedToken,
                baseRewardCoins = rewardAmount
            ).fold(
                onSuccess = { pending ->
                    _activePendingClaim.value = pending
                    _savedPendingClaim.value = pending
                },
                onFailure = { e ->
                    _bannerMessage.value = e.localizedMessage ?: "Could not verify round."
                }
            )
        }
    }

    fun skipPendingClaimForNow() {
        val current = _activePendingClaim.value
        if (current != null && !current.isExpired()) {
            _savedPendingClaim.value = current
            _bannerMessage.value = "${current.coins} coins saved — claim within 10 minutes."
        }
        _activePendingClaim.value = null
    }

    fun reopenSavedPendingClaim() {
        val saved = _savedPendingClaim.value ?: return
        if (saved.isExpired()) {
            _savedPendingClaim.value = null
            _bannerMessage.value = "Pending claim expired (10-minute limit)."
        } else {
            _activePendingClaim.value = saved
        }
    }

    /**
     * Tapping "Watch ad to claim" starts ONE real Unity rewarded ad (BP_Rewarded_Android).
     * - Completed -> credits `+N` coins once.
     * - Not loaded / failed -> shows "Ad not available, try again" and keeps reward claimable for 10 minutes.
     */
    fun watchAdToClaimPendingReward(activity: Activity?) {
        val pending = _activePendingClaim.value ?: return
        if (pending.isExpired()) {
            _activePendingClaim.value = null
            _savedPendingClaim.value = null
            _bannerMessage.value = "Claim expired after 10 minutes."
            return
        }
        if (_dailyAdsWatched.value >= dailyAdLimit) {
            _bannerMessage.value = "Daily ad limit reached ($dailyAdLimit/$dailyAdLimit)."
            return
        }

        val activePlacementId = UnityAdsConfig.rewardedPlacementId
        viewModelScope.launch {
            val sessionRes = repository.requestAdSession(
                placementId = activePlacementId,
                testMode = false,
                rewardCoins = pending.coins
            )
            sessionRes.fold(
                onSuccess = { session ->
                    unityAdManager.showRewardedAd(
                        activity = activity,
                        sessionId = session.id,
                        scope = viewModelScope,
                        onAdCompleted = { completedSessionId, elapsedSec ->
                            viewModelScope.launch {
                                val claimRes = repository.completeAdAndClaimPendingReward(
                                    pending = pending,
                                    sessionId = completedSessionId,
                                    placementId = session.placementId,
                                    elapsedSeconds = elapsedSec,
                                    isTestMode = false
                                )
                                claimRes.fold(
                                    onSuccess = { entry ->
                                        unityAdManager.markVerificationResult(true, "+${entry.amount} coins credited")
                                        _activePendingClaim.value = null
                                        _savedPendingClaim.value = null
                                        _bannerMessage.value = "+${entry.amount} coins added!"
                                        unityAdManager.loadRewardedPlacement(viewModelScope)
                                    },
                                    onFailure = { err ->
                                        val msg = err.localizedMessage ?: "Already claimed."
                                        unityAdManager.markVerificationResult(false, msg)
                                        _activePendingClaim.value = null
                                        _savedPendingClaim.value = null
                                        _bannerMessage.value = msg
                                    }
                                )
                            }
                        },
                        onAdSkippedOrFailed = { reason ->
                            val updated = pending.copy(errorMessage = reason)
                            _activePendingClaim.value = updated
                            _savedPendingClaim.value = updated
                            _bannerMessage.value = reason
                            unityAdManager.loadRewardedPlacement(viewModelScope)
                        }
                    )
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: UnityAdManager.AD_UNAVAILABLE_MESSAGE
                }
            )
        }
    }

    /**
     * Direct "Watch Ad" action on Home screen.
     */
    fun watchUnityRewardedAd(activity: Activity?, bonusContextTitle: String? = null) {
        if (_dailyAdsWatched.value >= dailyAdLimit) {
            _bannerMessage.value = "Daily ad limit reached ($dailyAdLimit/$dailyAdLimit)."
            return
        }
        val activePlacementId = UnityAdsConfig.rewardedPlacementId
        viewModelScope.launch {
            val sessionRes = repository.requestAdSession(
                placementId = activePlacementId,
                testMode = false
            )
            sessionRes.fold(
                onSuccess = { session ->
                    unityAdManager.showRewardedAd(
                        activity = activity,
                        sessionId = session.id,
                        scope = viewModelScope,
                        onAdCompleted = { completedSessionId, elapsedSec ->
                            viewModelScope.launch {
                                val verifyRes = repository.completeAndVerifyAdSession(
                                    sessionId = completedSessionId,
                                    placementId = session.placementId,
                                    elapsedSeconds = elapsedSec,
                                    isTestMode = false,
                                    bonusContextTitle = bonusContextTitle
                                )
                                verifyRes.fold(
                                    onSuccess = { entry ->
                                        unityAdManager.markVerificationResult(
                                            true,
                                            "+${entry.amount} coin added"
                                        )
                                        _bannerMessage.value = "+1 coin added!"
                                        unityAdManager.loadRewardedPlacement(viewModelScope)
                                    },
                                    onFailure = { err ->
                                        unityAdManager.markVerificationResult(
                                            false,
                                            err.localizedMessage ?: "Verification failed"
                                        )
                                        _bannerMessage.value = err.localizedMessage ?: "Verification failed"
                                    }
                                )
                            }
                        },
                        onAdSkippedOrFailed = { reason ->
                            _bannerMessage.value = reason
                            unityAdManager.loadRewardedPlacement(viewModelScope)
                        }
                    )
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: UnityAdManager.AD_UNAVAILABLE_MESSAGE
                }
            )
        }
    }

    // --- SECTION 4: PLAYSKILL PROOF SUBMISSION (NO UNITY AD TRIGGERED) ---

    fun submitAppMilestone(
        campaign: AppCampaign,
        milestoneTitle: String,
        milestoneId: String,
        rewardCoins: Long,
        evidenceText: String
    ) {
        viewModelScope.launch {
            val res = repository.submitMissionClaim(
                campaign = campaign,
                milestoneTitle = milestoneTitle,
                milestoneId = milestoneId,
                rewardCoins = rewardCoins,
                evidenceHash = evidenceText
            )
            res.fold(
                onSuccess = {
                    _bannerMessage.value = "Sent for review. Reviewed within 24 hours."
                },
                onFailure = { e ->
                    _bannerMessage.value = e.localizedMessage ?: "Could not submit proof."
                }
            )
        }
    }

    // --- SECTION 2: YOUTUBE WATCH-AND-EARN VERIFICATION STATE ---

    private val _videoWatchRecords = MutableStateFlow<Map<String, VideoWatchRecord>>(emptyMap())
    val videoWatchRecords: StateFlow<Map<String, VideoWatchRecord>> = _videoWatchRecords.asStateFlow()

    fun markVideoOpened(video: VideoCampaign) {
        val currentMap = _videoWatchRecords.value.toMutableMap()
        val existing = currentMap[video.id]
        if (existing?.status == VideoWatchStatus.REWARDED) {
            _bannerMessage.value = "Video reward already claimed today (${existing.transactionId})."
            return
        }
        val now = System.currentTimeMillis()
        currentMap[video.id] = (existing ?: VideoWatchRecord(
            videoId = video.id,
            youtubeVideoId = video.youtubeVideoId,
            title = video.title,
            rewardCoins = video.rewardCoins,
            minWatchSeconds = video.minWatchSeconds
        )).copy(
            openedAtMs = existing?.openedAtMs ?: now,
            status = if (existing?.status == VideoWatchStatus.VERIFIED) VideoWatchStatus.VERIFIED else VideoWatchStatus.PENDING,
            failureReason = null
        )
        _videoWatchRecords.value = currentMap
    }

    fun updateVideoWatchProgress(video: VideoCampaign, actualElapsedSec: Long, checkpointPassed: Boolean) {
        val currentMap = _videoWatchRecords.value.toMutableMap()
        val existing = currentMap[video.id] ?: VideoWatchRecord(
            videoId = video.id,
            youtubeVideoId = video.youtubeVideoId,
            title = video.title,
            rewardCoins = video.rewardCoins,
            minWatchSeconds = video.minWatchSeconds,
            openedAtMs = System.currentTimeMillis()
        )
        if (existing.status == VideoWatchStatus.REWARDED) return

        val bestElapsed = maxOf(existing.watchedSeconds, actualElapsedSec.coerceAtLeast(0L))
        val passed = existing.checkpointPassed || checkpointPassed
        val nextStatus = when {
            bestElapsed >= video.minWatchSeconds && passed -> VideoWatchStatus.VERIFIED
            else -> VideoWatchStatus.PENDING
        }
        currentMap[video.id] = existing.copy(
            watchedSeconds = bestElapsed,
            checkpointPassed = passed,
            status = nextStatus,
            failureReason = null
        )
        _videoWatchRecords.value = currentMap
    }

    fun markVideoVerificationFailed(video: VideoCampaign, reason: String) {
        val currentMap = _videoWatchRecords.value.toMutableMap()
        val existing = currentMap[video.id] ?: VideoWatchRecord(
            videoId = video.id,
            youtubeVideoId = video.youtubeVideoId,
            title = video.title,
            rewardCoins = video.rewardCoins,
            minWatchSeconds = video.minWatchSeconds
        )
        if (existing.status == VideoWatchStatus.REWARDED) return
        currentMap[video.id] = existing.copy(
            status = VideoWatchStatus.FAILED,
            failureReason = reason
        )
        _videoWatchRecords.value = currentMap
        _bannerMessage.value = reason
    }

    fun completeVerifiableVideoWatch(
        video: VideoCampaign,
        watchedSeconds: Long,
        checkpointVerified: Boolean = true
    ) {
        val existing = _videoWatchRecords.value[video.id]
        if (existing?.status == VideoWatchStatus.REWARDED) {
            _bannerMessage.value = "Duplicate claim blocked: Video already rewarded today."
            return
        }
        if (watchedSeconds < video.minWatchSeconds) {
            val needed = video.minWatchSeconds - watchedSeconds
            markVideoVerificationFailed(
                video,
                "Watch time incomplete (${watchedSeconds}s/${video.minWatchSeconds}s). Watch ${needed}s more."
            )
            return
        }
        if (!checkpointVerified) {
            markVideoVerificationFailed(
                video,
                "Complete the video verification checkpoint before claiming."
            )
            return
        }

        viewModelScope.launch {
            repository.claimVerifiableVideoWatch(
                video = video,
                watchedSeconds = watchedSeconds,
                checkpointVerified = checkpointVerified
            ).fold(
                onSuccess = { entry ->
                    val currentMap = _videoWatchRecords.value.toMutableMap()
                    val base = currentMap[video.id] ?: VideoWatchRecord(
                        videoId = video.id,
                        youtubeVideoId = video.youtubeVideoId,
                        title = video.title,
                        rewardCoins = video.rewardCoins,
                        minWatchSeconds = video.minWatchSeconds
                    )
                    currentMap[video.id] = base.copy(
                        watchedSeconds = watchedSeconds,
                        checkpointPassed = true,
                        status = VideoWatchStatus.REWARDED,
                        verifiedAtMs = System.currentTimeMillis(),
                        transactionId = entry.id,
                        failureReason = null
                    )
                    _videoWatchRecords.value = currentMap
                    _bannerMessage.value = "+${entry.amount} coins verified & credited! (Ref: ${entry.id.take(10)})"
                },
                onFailure = { e ->
                    val msg = e.localizedMessage ?: "Already claimed today."
                    markVideoVerificationFailed(video, msg)
                }
            )
        }
    }

    // --- SECTION 3 & 4: PLAY TOURNAMENT (STRICTLY 1V1 LUDO & CARROM) & MINI-GAMES STATE ---

    private val _tournamentArenas = MutableStateFlow(repository.defaultTournamentArenas())
    val tournamentArenas: StateFlow<List<TournamentArenaSpec>> = _tournamentArenas.asStateFlow()

    private val _tournamentMatches = MutableStateFlow<List<TournamentMatchRecord>>(emptyList())
    val tournamentMatches: StateFlow<List<TournamentMatchRecord>> = _tournamentMatches.asStateFlow()

    val active1v1Rooms: StateFlow<List<OneVsOneMatchRoom>> = repository.observeActive1v1Rooms()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _current1v1Match = MutableStateFlow<OneVsOneMatchRoom?>(null)
    val current1v1Match: StateFlow<OneVsOneMatchRoom?> = _current1v1Match.asStateFlow()

    // --- PART 6: TWO-CONSECUTIVE-REWARDED-AD HINT SYSTEM (0/2 -> 1/2 -> 2/2) ---
    private val _hintStates = MutableStateFlow<Map<String, HintProgressState>>(emptyMap())
    val hintStates: StateFlow<Map<String, HintProgressState>> = _hintStates.asStateFlow()

    fun getHintState(gameId: String): HintProgressState {
        return _hintStates.value[gameId] ?: HintProgressState(gameId = gameId)
    }

    fun resetHintForNewRound(gameId: String) {
        val updated = _hintStates.value.toMutableMap()
        updated[gameId] = HintProgressState(gameId = gameId)
        _hintStates.value = updated
    }

    /**
     * Pure, deterministic state transition for a completed Unity Rewarded Ad step toward a game hint.
     * - Step 1 (`0/2 -> 1/2`): Records `firstAdSessionId`, keeps `isUnlocked = false`, displays `"Hint progress: 1/2 ads completed"`.
     * - Step 2 (`1/2 -> 2/2`): Requires a distinct `adSessionId`, sets `adsCompleted = 2`, `isUnlocked = true`, and reveals `hintExplanation`.
     */
    fun recordHintAdStepCompleted(
        gameId: String,
        adSessionId: String,
        hintExplanation: String
    ): HintProgressState {
        val current = getHintState(gameId)
        if (current.isUnlocked && current.adsCompleted >= 2) {
            return current
        }
        if (adSessionId.isBlank() || adSessionId == current.firstAdSessionId || adSessionId == current.secondAdSessionId) {
            val dupState = current.copy(lastError = "Duplicate ad session ignored.")
            val map = _hintStates.value.toMutableMap()
            map[gameId] = dupState
            _hintStates.value = map
            return dupState
        }

        val nextState = if (current.adsCompleted == 0) {
            current.copy(
                adsCompleted = 1,
                adsRequired = 2,
                isUnlocked = false,
                firstAdSessionId = adSessionId,
                lastError = null
            )
        } else {
            current.copy(
                adsCompleted = 2,
                adsRequired = 2,
                isUnlocked = true,
                secondAdSessionId = adSessionId,
                hintText = hintExplanation,
                lastError = null
            )
        }
        val map = _hintStates.value.toMutableMap()
        map[gameId] = nextState
        _hintStates.value = map
        return nextState
    }

    /**
     * Records a skipped or failed rewarded ad during the 2-ad hint flow.
     * Never unlocks the hint early; preserves `1/2` progress if the first ad already completed.
     */
    fun recordHintAdStepFailedOrSkipped(gameId: String, reason: String): HintProgressState {
        val current = getHintState(gameId)
        val failedState = current.copy(
            isUnlocked = current.adsCompleted >= 2 && current.isUnlocked,
            lastError = reason
        )
        val map = _hintStates.value.toMutableMap()
        map[gameId] = failedState
        _hintStates.value = map
        return failedState
    }

    /**
     * Triggers the Two-Consecutive-Rewarded-Ad Hint sequence (`BP_Rewarded_Android`).
     * - If at `0/2`, plays Ad 1 -> on completion updates to `1/2` and immediately requests Ad 2 -> on completion updates to `2/2` and unlocks hint.
     * - If already at `1/2` (because Ad 2 previously failed to load), plays only Ad 2 -> on completion updates to `2/2` and unlocks hint.
     */
    fun requestTwoAdHint(
        activity: Activity?,
        gameId: String,
        hintExplanation: String,
        onHintUnlocked: (String) -> Unit = {}
    ) = requestHintWithTwoRewardedAds(
        activity = activity,
        gameId = gameId,
        hintExplanation = hintExplanation,
        onHintUnlocked = onHintUnlocked
    )

    fun requestHintWithTwoRewardedAds(
        activity: Activity?,
        gameId: String,
        hintExplanation: String,
        onHintUnlocked: (String) -> Unit = {}
    ) {
        val current = getHintState(gameId)
        if (current.isUnlocked && current.adsCompleted >= 2) {
            onHintUnlocked((current.hintText ?: "").ifBlank { hintExplanation })
            return
        }

        val activePlacementId = UnityAdsConfig.rewardedPlacementId

        fun launchSecondRewardedAd() {
            viewModelScope.launch {
                val session2Res = repository.requestAdSession(
                    placementId = activePlacementId,
                    testMode = false,
                    rewardCoins = 0L
                )
                session2Res.fold(
                    onSuccess = { session2 ->
                        unityAdManager.showRewardedAd(
                            activity = activity,
                            sessionId = session2.id,
                            scope = viewModelScope,
                            onAdCompleted = { completedSessionId2, _ ->
                                val unlocked = recordHintAdStepCompleted(
                                    gameId = gameId,
                                    adSessionId = completedSessionId2,
                                    hintExplanation = hintExplanation
                                )
                                _bannerMessage.value = unlocked.progressLabel
                                unityAdManager.loadRewardedPlacement(viewModelScope)
                                if (unlocked.isUnlocked) {
                                    onHintUnlocked(unlocked.hintText ?: hintExplanation)
                                }
                            },
                            onAdSkippedOrFailed = { reason ->
                                val preserveMsg = "$reason • Progress saved (1/2 ads completed). Tap Retry 2nd Ad."
                                recordHintAdStepFailedOrSkipped(gameId, preserveMsg)
                                _bannerMessage.value = preserveMsg
                                unityAdManager.loadRewardedPlacement(viewModelScope)
                            }
                        )
                    },
                    onFailure = { err ->
                        val msg = err.localizedMessage ?: UnityAdManager.AD_UNAVAILABLE_MESSAGE
                        recordHintAdStepFailedOrSkipped(gameId, "$msg • Progress saved (1/2 ads completed).")
                        _bannerMessage.value = "$msg • Progress saved (1/2 ads completed)."
                    }
                )
            }
        }

        if (current.adsCompleted == 1) {
            launchSecondRewardedAd()
            return
        }

        viewModelScope.launch {
            val session1Res = repository.requestAdSession(
                placementId = activePlacementId,
                testMode = false,
                rewardCoins = 0L
            )
            session1Res.fold(
                onSuccess = { session1 ->
                    unityAdManager.showRewardedAd(
                        activity = activity,
                        sessionId = session1.id,
                        scope = viewModelScope,
                        onAdCompleted = { completedSessionId1, _ ->
                            val step1 = recordHintAdStepCompleted(
                                gameId = gameId,
                                adSessionId = completedSessionId1,
                                hintExplanation = hintExplanation
                            )
                            _bannerMessage.value = "${step1.progressLabel} — Loading 2nd ad..."
                            unityAdManager.loadRewardedPlacement(viewModelScope)
                            launchSecondRewardedAd()
                        },
                        onAdSkippedOrFailed = { reason ->
                            recordHintAdStepFailedOrSkipped(gameId, reason)
                            _bannerMessage.value = reason
                            unityAdManager.loadRewardedPlacement(viewModelScope)
                        }
                    )
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: UnityAdManager.AD_UNAVAILABLE_MESSAGE
                    recordHintAdStepFailedOrSkipped(gameId, msg)
                    _bannerMessage.value = msg
                }
            )
        }
    }

    /**
     * Triggers Pre-roll, Mid-roll, or Post-roll Unity Video Ad opportunity on Creator Videos.
     */
    fun triggerCreatorVideoAdOpportunity(
        activity: Activity?,
        slotType: String,
        onResumeVideoPlayback: (adShown: Boolean) -> Unit
    ) {
        unityAdManager.showCreatorVideoAdOpportunity(
            activity = activity,
            slotType = slotType,
            scope = viewModelScope,
            onResumeVideoPlayback = onResumeVideoPlayback
        )
    }

    fun isGameEnabled(gameId: String): Boolean {
        return _appSettings.value.enabledGames[gameId] != false
    }

    fun showTransitionInterstitialIfEligible(
        activity: Activity?,
        onContinue: () -> Unit = {}
    ) {
        unityAdManager.showInterstitialAtTransition(
            activity = activity,
            scope = viewModelScope,
            onTransitionContinue = onContinue
        )
    }

    /**
     * Joins or creates a strictly 1v1 (2-player max) Ludo or Carrom match at one of the configured tiers:
     * - 5 coins entry -> 8 coins winner prize
     * - 15 coins entry -> 25 coins winner prize
     * - 20 coins entry -> 35 coins winner prize
     */
    fun start1v1TournamentMatch(
        gameId: String,
        tier: TournamentTierSpec,
        autoMatchChallenger: Boolean = true,
        onMatchReady: (OneVsOneMatchRoom) -> Unit = {}
    ) {
        if (!isGameEnabled(gameId)) {
            _bannerMessage.value = "This tournament arena is currently disabled."
            return
        }
        viewModelScope.launch {
            val joinRes = repository.joinOrCreate1v1Match(
                gameId = gameId,
                tier = tier,
                playerName = userDisplayName,
                opponentUid = if (autoMatchChallenger) "challenger_${UUID.randomUUID().toString().take(6)}" else null,
                opponentName = if (autoMatchChallenger) "Arena Challenger #${(101..999).random()}" else null
            )
            joinRes.fold(
                onSuccess = { activeRoom ->
                    _current1v1Match.value = activeRoom
                    _bannerMessage.value = if (activeRoom.playerCount == 2) {
                        "1v1 Match Started (2/2 Players) • Entry: ${tier.entryCoins} Coins • Winner Prize: ${tier.winnerPrizeCoins} Coins"
                    } else {
                        "Room Created (1/2 Waiting for Opponent). Entry ${tier.entryCoins} coins reserved."
                    }
                    onMatchReady(activeRoom)
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Could not join 1v1 match."
                }
            )
        }
    }

    fun cancelWaiting1v1TournamentMatch(roomId: String) {
        viewModelScope.launch {
            repository.cancelWaiting1v1Match(roomId).fold(
                onSuccess = { refundEntry ->
                    if (_current1v1Match.value?.id == roomId) {
                        _current1v1Match.value = null
                    }
                    _bannerMessage.value = "Match cancelled. +${refundEntry.amount} entry coins refunded to your wallet."
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Cannot cancel match."
                }
            )
        }
    }

    fun complete1v1TournamentMatch(
        roomId: String,
        player1Score: Int,
        player2Score: Int,
        roundsPlayed: Int = 1,
        activity: Activity? = null
    ) {
        viewModelScope.launch {
            val room = active1v1Rooms.value.firstOrNull { it.id == roomId } ?: _current1v1Match.value
            if (room == null) {
                return@launch
            }
            val effectiveP2Score = if (player1Score == player2Score) (player2Score - 1).coerceAtLeast(0) else player2Score

            repository.settle1v1MatchResult(
                roomId = room.id,
                player1Score = player1Score,
                player2Score = effectiveP2Score
            ).fold(
                onSuccess = { settled ->
                    _current1v1Match.value = settled
                    val userWon = settled.winnerUid == currentUserId
                    val resultStatus = if (userWon) "VICTORY" else "DEFEAT"
                    val matchRecord = TournamentMatchRecord(
                        id = settled.id,
                        tournamentId = if (settled.gameId.contains("ludo")) "ludo_arena" else "carrom_arena",
                        gameTitle = settled.gameTitle,
                        opponentLabel = (settled.player2Name ?: "").ifBlank { "1v1 Challenger" },
                        botDifficulty = BotDifficulty.ADVANCED,
                        playerScore = player1Score,
                        botScore = effectiveP2Score,
                        resultStatus = resultStatus,
                        entryCoinsPaid = settled.entryCoins,
                        winnerPrizeCoins = if (userWon) settled.winnerPrizeCoins else 0L,
                        practiceCoinsEarned = 0L,
                        playerCountLabel = "2/2",
                        roundsPlayed = roundsPlayed
                    )
                    _tournamentMatches.value = listOf(matchRecord) + _tournamentMatches.value
                    _bannerMessage.value = if (userWon) {
                        "1v1 Victory! +${settled.winnerPrizeCoins} coins prize credited to your wallet!"
                    } else {
                        "1v1 Match Complete ($player1Score - $effectiveP2Score). Winner received ${settled.winnerPrizeCoins} coins."
                    }
                    if (activity != null) {
                        showTransitionInterstitialIfEligible(activity)
                    }
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Could not settle 1v1 match."
                }
            )
        }
    }

    fun recordTournamentMatch(
        tournament: TournamentArenaSpec,
        botDifficulty: BotDifficulty,
        playerScore: Int,
        botScore: Int,
        roundsPlayed: Int = 1,
        activity: Activity? = null
    ) {
        if (!isGameEnabled(tournament.gameId)) {
            _bannerMessage.value = "${tournament.title} is currently disabled by Administrator."
            return
        }

        val activeRoom = _current1v1Match.value
        if (activeRoom != null && activeRoom.gameId == tournament.gameId && activeRoom.status == "IN_PROGRESS") {
            complete1v1TournamentMatch(
                roomId = activeRoom.id,
                player1Score = playerScore,
                player2Score = botScore,
                roundsPlayed = roundsPlayed,
                activity = activity
            )
            return
        }

        val isVictory = playerScore > botScore
        val isDraw = playerScore == botScore
        val resultLabel = when {
            isVictory -> "VICTORY"
            isDraw -> "DRAW"
            else -> "DEFEAT"
        }

        val match = TournamentMatchRecord(
            id = "match_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            tournamentId = tournament.id,
            gameTitle = tournament.title,
            opponentLabel = "${botDifficulty.label} (1v1 Practice)",
            botDifficulty = botDifficulty,
            playerScore = playerScore,
            botScore = botScore,
            resultStatus = resultLabel,
            entryCoinsPaid = 0L,
            winnerPrizeCoins = 0L,
            practiceCoinsEarned = if (isVictory) tournament.practiceRewardCoins else 0L,
            playerCountLabel = "2/2",
            roundsPlayed = roundsPlayed
        )
        _tournamentMatches.value = listOf(match) + _tournamentMatches.value

        if (isVictory) {
            completePuzzleChallenge(
                gameId = tournament.gameId,
                category = "Tournament",
                difficulty = botDifficulty.label,
                score = playerScore.toLong(),
                movesCount = roundsPlayed.toLong().coerceAtLeast(1L),
                durationSeconds = 30L,
                seedToken = match.id,
                baseReward = tournament.practiceRewardCoins
            )
        } else {
            _bannerMessage.value = "${tournament.title}: $resultLabel ($playerScore - $botScore vs ${botDifficulty.label})"
            if (activity != null) {
                showTransitionInterstitialIfEligible(activity)
            }
        }
    }

    // --- SECTION 2 & 9: REDEEM (Requires Google Sign-In; blocks Guests with Link prompt) ---

    fun requestGiftCodeRedemption(reward: RewardCatalogItem, emailInput: String? = null) {
        if (isGuestUser || userProfile.value?.accountStatus == "guest") {
            _showGuestLinkModal.value = true
            _bannerMessage.value = "Sign in with Google to redeem your coins."
            return
        }
        val effectiveEmail = (emailInput ?: userEmail ?: userProfile.value?.email ?: "").trim()
        if (effectiveEmail.isBlank()) {
            _bannerMessage.value = "Please enter your email address to submit a redemption request."
            return
        }
        submitRedemptionRequestWithEmail(reward = reward, email = effectiveEmail)
    }

    fun submitRedemptionRequestWithEmail(
        reward: RewardCatalogItem,
        email: String,
        onSuccess: (RedemptionRequest) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (isGuestUser || userProfile.value?.accountStatus == "guest") {
            _showGuestLinkModal.value = true
            val msg = "Sign in with Google to redeem your coins."
            _bannerMessage.value = msg
            onError(msg)
            return
        }
        viewModelScope.launch {
            val res = repository.submitRedemptionWithEmail(
                reward = reward,
                email = email,
                isGuestAccount = isGuestUser
            )
            res.fold(
                onSuccess = { req ->
                    _bannerMessage.value = "Redeem Pending! Request #${req.id.take(8)} submitted for ${req.userEmail}."
                    onSuccess(req)
                },
                onFailure = { err ->
                    if (err.message == "GUEST_REQUIRES_GOOGLE_SIGN_IN") {
                        _showGuestLinkModal.value = true
                    }
                    val msg = err.localizedMessage ?: "Redemption request failed."
                    _bannerMessage.value = msg
                    onError(msg)
                }
            )
        }
    }

    fun revealRedemptionCodeForUser(
        requestId: String,
        onRevealed: (String) -> Unit
    ) {
        viewModelScope.launch {
            repository.revealRedemptionCode(requestId = requestId, callerUid = currentUserId).fold(
                onSuccess = { code ->
                    onRevealed(code)
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Code is hidden until approved by staff."
                }
            )
        }
    }

    fun adminApproveRedemption(
        requestId: String,
        assignedCode: String,
        auditReason: String
    ) {
        viewModelScope.launch {
            repository.staffApproveRedemption(
                staffUid = currentUserId,
                requestId = requestId,
                assignedRedeemCode = assignedCode,
                auditReason = auditReason
            ).fold(
                onSuccess = { req ->
                    _bannerMessage.value = "Staff Approved: Request #${req.id.take(8)} marked Redeem Successful."
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Failed to approve redemption."
                }
            )
        }
    }

    fun adminRejectRedemption(
        requestId: String,
        rejectionReason: String,
        auditReason: String
    ) {
        viewModelScope.launch {
            repository.staffRejectRedemption(
                staffUid = currentUserId,
                requestId = requestId,
                rejectionReason = rejectionReason,
                auditReason = auditReason
            ).fold(
                onSuccess = { req ->
                    _bannerMessage.value = "Staff Rejected: Request #${req.id.take(8)} rejected & ${req.pointsReserved} coins refunded."
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Failed to reject redemption."
                }
            )
        }
    }

    fun adminAssignUserRole(
        targetUserId: String,
        newRole: String,
        reason: String
    ) {
        viewModelScope.launch {
            repository.assignUserRole(
                actorUid = currentUserId,
                targetUserId = targetUserId,
                newRole = newRole,
                reason = reason
            ).fold(
                onSuccess = {
                    _bannerMessage.value = "RBAC: Assigned role '$newRole' to $targetUserId."
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Role assignment rejected."
                }
            )
        }
    }

    fun adminAdjustUserBalance(
        targetUserId: String,
        deltaCoins: Long,
        reason: String
    ) {
        viewModelScope.launch {
            repository.staffAdjustUserBalance(
                staffUid = currentUserId,
                targetUserId = targetUserId,
                deltaCoins = deltaCoins,
                reason = reason
            ).fold(
                onSuccess = { entry ->
                    _bannerMessage.value = "Balance adjusted (${if (deltaCoins >= 0) "+$deltaCoins" else "$deltaCoins"} coins). New balance: ${entry.balanceAfter}."
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Balance adjustment rejected."
                }
            )
        }
    }

    fun adminRunSqlMigration() {
        viewModelScope.launch {
            repository.backupAndMigrateToSql().fold(
                onSuccess = { report ->
                    _bannerMessage.value = "SQL Migration Verified: ${report.migratedUsersCount} users, ${report.migratedWalletsCount} wallets, ${report.migratedLedgerCount} ledger entries, ${report.migratedRedemptionsCount} redemptions (Integrity: ${report.integrityVerified})."
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "SQL migration failed."
                }
            )
        }
    }

    fun reportCommunityCode(code: RedeemCodeInfo) {
        viewModelScope.launch {
            repository.reportRedeemCode(code.id, code.reportsCount)
            _bannerMessage.value = "Code reported."
        }
    }

    fun createSupportTicket(category: String, subject: String, message: String, linkedRedemptionId: String? = null) {
        viewModelScope.launch {
            if (subject.isBlank() || message.isBlank()) {
                _bannerMessage.value = "Please enter a subject and message."
                return@launch
            }
            val res = repository.submitSupportTicket(category, subject, message, linkedRedemptionId)
            res.fold(
                onSuccess = { _bannerMessage.value = "Support ticket #${it.id.take(6)} sent." },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to send ticket." }
            )
        }
    }

    // --- ADMIN ACTIONS ---

    fun adminUpdateSettings(updated: FoundwayAppSettings, auditReason: String) {
        _appSettings.value = updated.copy(unityTestMode = false)
        viewModelScope.launch {
            repository.logAdminAction("UPDATE_APP_SETTINGS", "settings", "global", auditReason)
            _bannerMessage.value = "Settings updated."
        }
    }

    fun adminToggleGameEnabled(gameId: String, enabled: Boolean, auditReason: String) {
        val updatedMap = _appSettings.value.enabledGames.toMutableMap()
        updatedMap[gameId] = enabled
        _appSettings.value = _appSettings.value.copy(enabledGames = updatedMap)
        _tournamentArenas.value = _tournamentArenas.value.map { arena ->
            if (arena.gameId == gameId) arena.copy(isEnabled = enabled) else arena
        }
        viewModelScope.launch {
            repository.logAdminAction(
                actionType = if (enabled) "ENABLE_GAME" else "DISABLE_GAME",
                targetCollection = "settings_games",
                targetId = gameId,
                reason = auditReason.ifBlank { "Toggled $gameId to $enabled" }
            )
            _bannerMessage.value = "Admin: Game '$gameId' ${if (enabled) "enabled" else "disabled"}."
        }
    }

    fun adminUpdateTournamentConfig(
        tournamentId: String,
        entryCoins: Long,
        advertisedPrizeCoins: Long,
        activateRealPrizeArena: Boolean,
        auditReason: String
    ) {
        _tournamentArenas.value = _tournamentArenas.value.map { arena ->
            if (arena.id == tournamentId) {
                arena.copy(
                    entryCoins = entryCoins.coerceAtLeast(0L),
                    advertisedPrizeCoins = advertisedPrizeCoins.coerceAtLeast(0L),
                    isRealPrizeActive = activateRealPrizeArena
                )
            } else arena
        }
        viewModelScope.launch {
            repository.logAdminAction(
                actionType = "UPDATE_TOURNAMENT_CONFIG",
                targetCollection = "tournaments",
                targetId = tournamentId,
                reason = auditReason.ifBlank { "Updated entry=$entryCoins, prize=$advertisedPrizeCoins, active=$activateRealPrizeArena" }
            )
            _bannerMessage.value = "Admin: Updated tournament '$tournamentId' configuration."
        }
    }

    fun adminApprovePlaySkillProof(claim: TaskClaim, auditReason: String) {
        viewModelScope.launch {
            repository.adminApprovePlaySkillClaim(claim, auditReason).fold(
                onSuccess = { entry ->
                    _bannerMessage.value = "Admin: Approved ${claim.title} (+${entry.amount} coins credited)."
                },
                onFailure = { e ->
                    _bannerMessage.value = e.localizedMessage ?: "Approval failed."
                }
            )
        }
    }

    fun adminAddCampaign(
        appName: String,
        packageName: String,
        rewardCoins: Long,
        categoryLabel: String,
        auditReason: String
    ) {
        viewModelScope.launch {
            val camp = AppCampaign(
                id = "camp_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                visibility = "public",
                appName = appName.trim(),
                packageName = packageName.trim(),
                playStoreUrl = "https://play.google.com/store/apps/details?id=${packageName.trim()}",
                shortDescription = "Reach level 10",
                fullInstructions = "Install $appName from Google Play and submit level proof.",
                totalReward = rewardCoins,
                estimatedMinutes = 12L,
                status = "live",
                categoryLabel = categoryLabel,
                verificationMethod = "manual",
                sponsorDisclosure = "Reviewed by an admin within 24 hours.",
                slotsRemaining = 100L,
                dailyLimit = 25L
            )
            repository.createAppCampaign(camp, auditReason).fold(
                onSuccess = { _bannerMessage.value = "Admin: PlaySkill mission '$appName' added." },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to create mission." }
            )
        }
    }

    fun adminUpdateRewardStock(reward: RewardCatalogItem, newStockCount: Long, auditReason: String) {
        viewModelScope.launch {
            val updated = reward.copy(
                stockCount = newStockCount.coerceAtLeast(0L),
                availability = if (newStockCount > 0L) "available" else "out_of_stock"
            )
            repository.createOrUpdateRewardItem(updated, auditReason).fold(
                onSuccess = { _bannerMessage.value = "Admin: Updated ${reward.rewardName} stock to $newStockCount." },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to update stock." }
            )
        }
    }

    fun adminPublishRedeemCode(
        code: String,
        title: String,
        rewardDesc: String,
        region: String,
        sourceRef: String,
        auditReason: String
    ) {
        viewModelScope.launch {
            val info = RedeemCodeInfo(
                id = "ff_${code.trim().lowercase()}",
                visibility = "public",
                code = code.trim().uppercase(),
                title = title.trim(),
                rewardDescription = rewardDesc.trim(),
                region = region.trim().ifBlank { "IND" },
                game = "FF_MAX",
                expiryLabel = "Active",
                sourceReference = sourceRef.trim(),
                verificationStatus = "verified",
                reportsCount = 0L
            )
            repository.publishRedeemCodeInfo(info, auditReason).fold(
                onSuccess = { _bannerMessage.value = "Admin: Published code ${info.code}." },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to publish code." }
            )
        }
    }

    fun redeemPromoCode(rawCode: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.redeemCodeAtomic(rawCode).fold(
                onSuccess = { (status, message) ->
                    _bannerMessage.value = message
                    if (status == RedeemCodeStatus.AVAILABLE) {
                        onSuccess()
                    }
                },
                onFailure = {
                    _bannerMessage.value = it.localizedMessage ?: "Failed to redeem code."
                }
            )
        }
    }

    fun setVideoCategory(category: String) {
        _videoCategoryFilter.value = category
    }

    fun setVideoSearchQuery(query: String) {
        _videoSearchQuery.value = query
    }

    fun selectWatchVideo(video: VideoPost?) {
        _selectedWatchVideo.value = video
        if (video != null) {
            viewModelScope.launch {
                repository.recordVideoView(video.id)
            }
        }
    }

    fun toggleVideoLike(videoId: String) {
        viewModelScope.launch {
            repository.toggleVideoLike(videoId).fold(
                onSuccess = { liked ->
                    _bannerMessage.value = if (liked) "Added to your liked videos!" else "Removed like."
                },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Action failed." }
            )
        }
    }

    fun addVideoComment(videoId: String, commentText: String) {
        viewModelScope.launch {
            repository.addVideoComment(videoId, commentText).fold(
                onSuccess = { _bannerMessage.value = "Comment posted successfully!" },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to post comment." }
            )
        }
    }

    fun deleteVideoComment(videoId: String, commentId: String) {
        viewModelScope.launch {
            repository.deleteVideoComment(videoId, commentId).fold(
                onSuccess = { _bannerMessage.value = "Comment deleted." },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to delete comment." }
            )
        }
    }

    fun toggleChannelSubscription(channelId: String) {
        viewModelScope.launch {
            repository.toggleSubscription(channelId).fold(
                onSuccess = { subbed ->
                    _bannerMessage.value = if (subbed) "Subscribed to channel!" else "Unsubscribed."
                },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Subscription failed." }
            )
        }
    }

    fun publishCreatorVideo(
        title: String,
        description: String,
        category: String,
        durationSeconds: Long = 60L,
        localVideoUri: String = "",
        fileSizeBytes: Long = 0L
    ) {
        viewModelScope.launch {
            val post = VideoPost(
                title = title.trim(),
                description = description.trim(),
                channelId = creatorChannel.value.channelId,
                channelName = creatorChannel.value.channelName,
                authorUid = currentUserId,
                videoCategory = category,
                durationSeconds = durationSeconds,
                viewsCount = 0L,
                likesCount = 0L,
                commentsCount = 0L,
                status = "published",
                visibility = "public",
                localVideoUri = localVideoUri,
                fileSizeBytes = fileSizeBytes,
                isVerifiedCreator = true,
                watchRewardCoins = 2L
            )
            repository.publishVideoPost(post).fold(
                onSuccess = {
                    _bannerMessage.value = "Video published! +10 creator bonus coins credited to your wallet!"
                    closeUploadVideoDialog()
                },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to publish video." }
            )
        }
    }

    fun deleteCreatorVideo(videoId: String) {
        viewModelScope.launch {
            repository.deleteVideoPost(videoId).fold(
                onSuccess = { _bannerMessage.value = "Video removed from channel." },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to delete video." }
            )
        }
    }

    fun applyForMonetization() {
        viewModelScope.launch {
            val channel = creatorChannel.value
            repository.submitMonetizationApplication(channel).fold(
                onSuccess = {
                    _bannerMessage.value = "Monetization application submitted! Admin will review within 24-48 hours."
                },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to submit application." }
            )
        }
    }

    fun adminReviewMonetization(
        creatorId: String,
        approve: Boolean,
        notes: String,
        auditReason: String = "Admin review"
    ) {
        viewModelScope.launch {
            val targetId = monetizationApplication.value?.id ?: creatorId
            repository.adminReviewMonetization(targetId, approve, "$notes ($auditReason)").fold(
                onSuccess = {
                    _bannerMessage.value = "Monetization application marked as ${if (approve) "APPROVED" else "REJECTED"}."
                },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to review application." }
            )
        }
    }

    fun adminCreatePromoCode(
        code: String,
        title: String,
        rewardCoins: Long,
        maxStock: Long,
        category: String = "All Features",
        auditReason: String
    ) {
        viewModelScope.launch {
            val promo = RedeemPromoCode(
                code = code.trim().uppercase(),
                title = title.trim(),
                coinReward = rewardCoins,
                totalStock = maxStock,
                description = "$title ($category)",
                targetGame = category,
                isEnabled = true
            )
            repository.adminCreatePromoCode(promo, auditReason).fold(
                onSuccess = { _bannerMessage.value = "Admin: Created promo code ${promo.code}." },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to create promo code." }
            )
        }
    }

    fun adminTogglePromoCode(codeId: String, enable: Boolean, auditReason: String) {
        viewModelScope.launch {
            repository.adminTogglePromoCode(codeId, enable, auditReason).fold(
                onSuccess = { _bannerMessage.value = "Admin: Promo code toggled." },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to toggle promo code." }
            )
        }
    }

    // =========================================================================
    // V2: AUTHORITATIVE LUDO & CARROM MATCHES, VIDEO PROOF & MINI-GAMES
    // =========================================================================

    val activeAuthoritativeMatches: StateFlow<Map<String, AuthoritativeGameMatch>> =
        repository.observeActiveAuthoritativeMatches()
            .catch { emit(emptyMap()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    val videoProofSubmissions: StateFlow<List<VideoProofSubmission>> =
        repository.observeVideoProofSubmissions(currentUserId)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val miniGamePersonalBests: StateFlow<List<MiniGamePersonalBest>> =
        repository.observeMiniGamePersonalBests(currentUserId)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _staffVideoProofQueue = MutableStateFlow<List<VideoProofSubmission>>(emptyList())
    val staffVideoProofQueue: StateFlow<List<VideoProofSubmission>> = _staffVideoProofQueue.asStateFlow()

    private val _insufficientMatchCoinsAlert = MutableStateFlow<String?>(null)
    val insufficientMatchCoinsAlert: StateFlow<String?> = _insufficientMatchCoinsAlert.asStateFlow()

    fun clearInsufficientMatchCoinsAlert() {
        _insufficientMatchCoinsAlert.value = null
    }

    fun startAuthoritativeBoardMatch(
        gameType: String, // "LUDO" or "CARROM"
        matchTitle: String,
        entryFeeCoins: Long,
        prizeCoins: Long,
        isPlayAgain: Boolean = false,
        onMatchStarted: (AuthoritativeGameMatch) -> Unit = {},
        onInsufficientCoins: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val idempKey = "entry_${gameType.lowercase()}_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            repository.startAuthoritativeBoardMatch(
                gameType = gameType,
                matchTitle = matchTitle,
                entryFeeCoins = entryFeeCoins,
                prizeCoins = prizeCoins,
                idempotencyKey = idempKey,
                isPlayAgain = isPlayAgain
            ).fold(
                onSuccess = { match ->
                    _insufficientMatchCoinsAlert.value = null
                    onMatchStarted(match)
                },
                onFailure = { err ->
                    val exactMsg = when (err) {
                        is InsufficientMatchCoinsException -> err.message
                        else -> err.localizedMessage ?: "Not enough coins to play this match. Please go back and earn more coins."
                    }
                    _insufficientMatchCoinsAlert.value = exactMsg
                    _bannerMessage.value = exactMsg
                    onInsufficientCoins(exactMsg)
                }
            )
        }
    }

    fun rollAuthoritativeLudoDice(
        matchId: String,
        actor: String,
        requestedDiceValue: Int? = null,
        onUpdated: (AuthoritativeGameMatch) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.rollAuthoritativeLudoDice(matchId, actor, requestedDiceValue).fold(
                onSuccess = { onUpdated(it) },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Dice roll rejected." }
            )
        }
    }

    fun submitAuthoritativeLudoMove(
        matchId: String,
        actor: String,
        tokenIndex: Int,
        onUpdated: (AuthoritativeGameMatch) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.submitAuthoritativeLudoMove(matchId, actor, tokenIndex).fold(
                onSuccess = { match ->
                    if (match.status == "COMPLETED_WON") {
                        _bannerMessage.value = "Booyah Victory! +${match.prizeCoins} coins credited to your wallet!"
                    }
                    onUpdated(match)
                },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Illegal move rejected." }
            )
        }
    }

    fun submitAuthoritativeCarromShot(
        matchId: String,
        actor: String,
        strikerX: Float,
        aimAngleDeg: Float,
        powerFraction: Float,
        updatedDiscs: List<CarromDiscState>,
        strikerFoul: Boolean,
        onUpdated: (AuthoritativeGameMatch) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.submitAuthoritativeCarromShot(
                matchId = matchId,
                actor = actor,
                strikerX = strikerX,
                aimAngleDeg = aimAngleDeg,
                powerFraction = powerFraction,
                updatedDiscs = updatedDiscs,
                strikerFoul = strikerFoul
            ).fold(
                onSuccess = { match ->
                    if (match.status == "COMPLETED_WON") {
                        _bannerMessage.value = "Booyah Carrom Victory! +${match.prizeCoins} coins credited to your wallet!"
                    }
                    onUpdated(match)
                },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Carrom shot rejected." }
            )
        }
    }

    fun settleAuthoritativeBoardMatch(
        matchId: String,
        playerScore: Int,
        opponentScore: Int,
        surrendered: Boolean = false,
        onSettled: (AuthoritativeGameMatch) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.settleAuthoritativeBoardMatch(
                matchId = matchId,
                playerScore = playerScore,
                opponentScore = opponentScore,
                surrendered = surrendered
            ).fold(
                onSuccess = { match ->
                    if (surrendered) {
                        _bannerMessage.value = "Match surrendered. Entry fee is non-refundable after match starts."
                    } else if (match.status == "COMPLETED_WON") {
                        _bannerMessage.value = "Victory! +${match.prizeCoins} coins credited to your wallet!"
                    }
                    onSettled(match)
                },
                onFailure = { _bannerMessage.value = it.localizedMessage ?: "Failed to settle match." }
            )
        }
    }

    fun submitVideoProofForTask(
        taskId: String,
        taskTitle: String,
        rewardCoins: Long,
        videoUri: String,
        mimeType: String = "video/mp4",
        fileSizeBytes: Long = 2_450_000L,
        durationSeconds: Int = 24,
        metadataHash: String = "vp_hash_${taskId}_${videoUri.hashCode()}_${System.currentTimeMillis() / 60000}"
    ) {
        viewModelScope.launch {
            repository.submitVideoProofForTask(
                taskId = taskId,
                taskTitle = taskTitle,
                rewardCoins = rewardCoins,
                videoUri = videoUri,
                mimeType = mimeType,
                fileSizeBytes = fileSizeBytes,
                durationSeconds = durationSeconds,
                metadataHash = metadataHash
            ).fold(
                onSuccess = { sub ->
                    _bannerMessage.value = "Video proof submitted (Status: Pending Review). Coins will be credited after staff approval!"
                    refreshStaffVideoProofQueue()
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Video proof validation failed."
                }
            )
        }
    }

    fun refreshStaffVideoProofQueue() {
        viewModelScope.launch {
            repository.getAllVideoProofsForStaffReview().onSuccess { list ->
                _staffVideoProofQueue.value = list
            }
        }
    }

    fun staffApproveVideoProof(submissionId: String, auditReason: String = "Verified gameplay video proof") {
        viewModelScope.launch {
            repository.staffApproveVideoProof(submissionId, auditReason).fold(
                onSuccess = { approved ->
                    _bannerMessage.value = "Approved video proof ${approved.submissionId}! +${approved.rewardCoins} coins credited to user."
                    refreshStaffVideoProofQueue()
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Failed to approve video proof."
                }
            )
        }
    }

    fun staffRejectVideoProof(submissionId: String, rejectionReason: String) {
        viewModelScope.launch {
            repository.staffRejectVideoProof(submissionId, rejectionReason).fold(
                onSuccess = { rejected ->
                    _bannerMessage.value = "Rejected video proof ${rejected.submissionId}."
                    refreshStaffVideoProofQueue()
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Failed to reject video proof."
                }
            )
        }
    }

    fun completeMiniGameSession(
        gameType: String, // "MEMORY_MATCH" or "BUBBLE_POP"
        difficulty: String,
        score: Int,
        movesCount: Int,
        durationSeconds: Int,
        onCompleted: (MiniGameSessionResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.completeMiniGameSession(
                gameType = gameType,
                difficulty = difficulty,
                score = score,
                movesCount = movesCount,
                durationSeconds = durationSeconds
            ).fold(
                onSuccess = { res ->
                    if (res.coinsAwarded > 0L) {
                        _bannerMessage.value = "+${res.coinsAwarded} Coins earned from ${res.gameType.replace("_", " ")}!"
                    } else {
                        _bannerMessage.value = "Session recorded! (Daily rewarded session limit reached for this mini-game)."
                    }
                    onCompleted(res)
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Session validation failed."
                }
            )
        }
    }

    companion object {
        private const val TAG = "AthxoBooyahVM"
    }
}

