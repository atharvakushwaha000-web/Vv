@file:Suppress("DEPRECATION")

package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.example.ui.theme.PandaCardBorder
import com.example.ui.theme.PandaGreen
import com.example.ui.theme.PandaInk
import com.example.ui.theme.PandaMuted
import com.example.ui.theme.PandaWhiteCard
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AdState(val label: String) {
    IDLE("Ready to Initialize"),
    INITIALIZING("Initializing Unity Ads..."),
    LOADING("Loading Ad..."),
    READY("Ad Ready"),
    DISPLAYED("Ad Playing"),
    COMPLETED("Ad Completed"),
    SKIPPED("Closed Early — 0 Coins"),
    VERIFICATION_PENDING("Verifying Reward..."),
    APPROVED("Reward Credited"),
    REJECTED("Verification Rejected"),
    FAILED("Ad Not Available")
}

/**
 * Verified Unity Cloud Monetization Configuration (`cloud.unity.com` screenshot):
 * - App: MindFlow: Brain Puzzle
 * - Unity Project ID: `0b5a3932-6f76-434b-9237-c7a2735c7372`
 * - Android Game ID: `800370914`
 * - Rewarded Placement ID: `BP_Rewarded_Android`
 * - Interstitial Placement Name & ID: `All ads` (with `All_ads` normalized alias support)
 * - Banner Placement ID: `Banner_Android`
 * - Test Mode: `false` (strictly production mode everywhere; zero test/mock ads)
 */
object UnityAdsConfig {
    const val DEFAULT_UNITY_PROJECT_ID = "0b5a3932-6f76-434b-9237-c7a2735c7372"
    const val DEFAULT_ANDROID_GAME_ID = "800370914"
    const val DEFAULT_REWARDED_PLACEMENT_ID = "BP_Rewarded_Android"
    const val DEFAULT_INTERSTITIAL_PLACEMENT_ID = "All ads"
    const val INTERSTITIAL_PLACEMENT_ALIAS = "All_ads"
    const val DEFAULT_BANNER_PLACEMENT_ID = "Banner_Android"

    // Strictly false across the entire app
    const val TEST_MODE: Boolean = false

    // Minimum 2 minutes between interstitial ads so gameplay and navigation are never spammed
    const val INTERSTITIAL_COOLDOWN_MS: Long = 120_000L

    /**
     * Rejects YouTube URLs, channel IDs, empty strings, or the Game ID if accidentally passed as a placement ID.
     */
    fun isValidUnityPlacementId(candidate: String): Boolean {
        val trimmed = candidate.trim()
        if (trimmed.isBlank()) return false
        if (trimmed == DEFAULT_ANDROID_GAME_ID || trimmed == DEFAULT_UNITY_PROJECT_ID) return false
        val lower = trimmed.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) return false
        return true
    }

    private fun readBuildConfigField(fieldName: String): String {
        return runCatching {
            val field = BuildConfig::class.java.getDeclaredField(fieldName)
            (field.get(null) as? String)?.trim().orEmpty()
        }.getOrDefault("")
    }

    val unityProjectId: String
        get() = DEFAULT_UNITY_PROJECT_ID

    val androidGameId: String
        get() = DEFAULT_ANDROID_GAME_ID

    val rewardedPlacementId: String
        get() = DEFAULT_REWARDED_PLACEMENT_ID

    val interstitialPlacementId: String
        get() = DEFAULT_INTERSTITIAL_PLACEMENT_ID

    val interstitialPlacementAlias: String
        get() = INTERSTITIAL_PLACEMENT_ALIAS

    val bannerPlacementId: String
        get() = DEFAULT_BANNER_PLACEMENT_ID
}

class UnityAdManager {
    val providerName: String = "Unity Ads"
    val unityProjectId: String
        get() = UnityAdsConfig.unityProjectId
    val gameId: String
        get() = UnityAdsConfig.androidGameId
    val placementId: String
        get() = UnityAdsConfig.rewardedPlacementId
    val interstitialPlacementId: String
        get() = UnityAdsConfig.interstitialPlacementId
    val interstitialPlacementAlias: String
        get() = UnityAdsConfig.interstitialPlacementAlias
    val bannerPlacementId: String
        get() = UnityAdsConfig.bannerPlacementId
    val isTestMode: Boolean = UnityAdsConfig.TEST_MODE

    private val _adState = MutableStateFlow(AdState.IDLE)
    val adState: StateFlow<AdState> = _adState.asStateFlow()

    private val _interstitialState = MutableStateFlow(AdState.IDLE)
    val interstitialState: StateFlow<AdState> = _interstitialState.asStateFlow()

    private val _bannerState = MutableStateFlow(AdState.IDLE)
    val bannerState: StateFlow<AdState> = _bannerState.asStateFlow()

    private val _statusDetail = MutableStateFlow("Ready")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    @Volatile
    private var isSdkInitialized = false

    @Volatile
    private var isRewardedPlacementLoaded = false

    @Volatile
    private var isInterstitialPlacementLoaded = false

    @Volatile
    private var activeInterstitialPlacementId: String = UnityAdsConfig.interstitialPlacementId

    @Volatile
    private var lastInterstitialShownAtMs: Long = 0L

    private var showStartTimeMs: Long = 0L

    fun initializeAndLoad(
        context: Context,
        scope: CoroutineScope,
        forceReload: Boolean = false
    ) {
        val activeGameId = gameId
        val activeRewardedId = placementId

        if (activeGameId.isBlank() || !UnityAdsConfig.isValidUnityPlacementId(activeRewardedId)) {
            _adState.value = AdState.FAILED
            _statusDetail.value = AD_UNAVAILABLE_MESSAGE
            return
        }

        try {
            if (UnityAds.isInitialized) {
                isSdkInitialized = true
                if (forceReload || !isRewardedPlacementLoaded) {
                    loadRewardedPlacement(scope)
                } else {
                    _adState.value = AdState.READY
                    _statusDetail.value = "Ad ready"
                }
                if (forceReload || !isInterstitialPlacementLoaded) {
                    loadInterstitialPlacement(scope)
                }
                return
            }

            _adState.value = AdState.INITIALIZING
            _interstitialState.value = AdState.INITIALIZING
            _statusDetail.value = "Loading ad..."

            // Strictly `testMode = false` (production ads only)
            UnityAds.initialize(
                context.applicationContext,
                activeGameId,
                false,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        isSdkInitialized = true
                        Log.i(
                            TAG,
                            "UnityAds initialized (Project=$unityProjectId, GameID=$activeGameId, " +
                                "Rewarded=$activeRewardedId, Interstitial=$interstitialPlacementId / $interstitialPlacementAlias, testMode=false)"
                        )
                        loadRewardedPlacement(scope)
                        loadInterstitialPlacement(scope)
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError?,
                        message: String?
                    ) {
                        isSdkInitialized = false
                        isRewardedPlacementLoaded = false
                        isInterstitialPlacementLoaded = false
                        Log.w(TAG, "UnityAds init failed: $error - $message")
                        scope.launch(Dispatchers.Main) {
                            _adState.value = AdState.FAILED
                            _interstitialState.value = AdState.FAILED
                            _statusDetail.value = AD_UNAVAILABLE_MESSAGE
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "UnityAds initialize exception: ${t.message}")
            _adState.value = AdState.FAILED
            _interstitialState.value = AdState.FAILED
            _statusDetail.value = AD_UNAVAILABLE_MESSAGE
        }
    }

    fun loadRewardedPlacement(scope: CoroutineScope) {
        val activePlacementId = placementId
        if (!isSdkInitialized && !UnityAds.isInitialized) {
            _adState.value = AdState.FAILED
            _statusDetail.value = AD_UNAVAILABLE_MESSAGE
            return
        }

        _adState.value = AdState.LOADING
        _statusDetail.value = "Loading ad..."

        try {
            UnityAds.load(
                activePlacementId,
                object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(loadedPlacementId: String?) {
                        isRewardedPlacementLoaded = true
                        Log.i(TAG, "UnityAds rewarded placement loaded: $loadedPlacementId")
                        scope.launch(Dispatchers.Main) {
                            _adState.value = AdState.READY
                            _statusDetail.value = "Ad ready"
                        }
                    }

                    override fun onUnityAdsFailedToLoad(
                        failedPlacementId: String?,
                        error: UnityAds.UnityAdsLoadError?,
                        message: String?
                    ) {
                        isRewardedPlacementLoaded = false
                        Log.w(TAG, "UnityAds load failed ($failedPlacementId): $error - $message")
                        scope.launch(Dispatchers.Main) {
                            _adState.value = AdState.FAILED
                            _statusDetail.value = AD_UNAVAILABLE_MESSAGE
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            isRewardedPlacementLoaded = false
            _adState.value = AdState.FAILED
            _statusDetail.value = AD_UNAVAILABLE_MESSAGE
        }
    }

    /**
     * Loads the Unity Cloud Interstitial placement (`"All ads"` with automatic fallback to `"All_ads"`).
     */
    fun loadInterstitialPlacement(scope: CoroutineScope, tryAliasOnFail: Boolean = true) {
        if (!isSdkInitialized && !UnityAds.isInitialized) {
            _interstitialState.value = AdState.FAILED
            return
        }

        val targetPlacement = if (tryAliasOnFail) interstitialPlacementId else interstitialPlacementAlias
        _interstitialState.value = AdState.LOADING

        try {
            UnityAds.load(
                targetPlacement,
                object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(loadedPlacementId: String?) {
                        isInterstitialPlacementLoaded = true
                        activeInterstitialPlacementId = loadedPlacementId ?: targetPlacement
                        Log.i(TAG, "UnityAds interstitial placement loaded: $activeInterstitialPlacementId")
                        scope.launch(Dispatchers.Main) {
                            _interstitialState.value = AdState.READY
                        }
                    }

                    override fun onUnityAdsFailedToLoad(
                        failedPlacementId: String?,
                        error: UnityAds.UnityAdsLoadError?,
                        message: String?
                    ) {
                        Log.w(TAG, "UnityAds interstitial load failed ($failedPlacementId): $error - $message")
                        if (tryAliasOnFail && interstitialPlacementAlias != interstitialPlacementId) {
                            loadInterstitialPlacement(scope, tryAliasOnFail = false)
                        } else {
                            isInterstitialPlacementLoaded = false
                            scope.launch(Dispatchers.Main) {
                                _interstitialState.value = AdState.FAILED
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            isInterstitialPlacementLoaded = false
            _interstitialState.value = AdState.FAILED
        }
    }

    /**
     * Shows a real Unity Rewarded Ad (`BP_Rewarded_Android`, `testMode = false`).
     * Never shows a fake/simulated ad screen.
     * Coins are granted ONLY when `onUnityAdsShowComplete` fires with `UnityAdsShowCompletionState.COMPLETED`.
     */
    fun showRewardedAd(
        activity: Activity?,
        sessionId: String,
        scope: CoroutineScope,
        onAdCompleted: (sessionId: String, elapsedSeconds: Int) -> Unit,
        onAdSkippedOrFailed: (reason: String) -> Unit
    ) {
        val activePlacementId = placementId

        if (activity == null) {
            _adState.value = AdState.FAILED
            _statusDetail.value = AD_UNAVAILABLE_MESSAGE
            onAdSkippedOrFailed(AD_UNAVAILABLE_MESSAGE)
            return
        }

        if ((!isSdkInitialized && !UnityAds.isInitialized) || !isRewardedPlacementLoaded) {
            _adState.value = AdState.FAILED
            _statusDetail.value = AD_UNAVAILABLE_MESSAGE
            initializeAndLoad(activity.applicationContext, scope, forceReload = true)
            onAdSkippedOrFailed(AD_UNAVAILABLE_MESSAGE)
            return
        }

        try {
            showStartTimeMs = System.currentTimeMillis()
            val showOptions = UnityAdsShowOptions()
            UnityAds.show(
                activity,
                activePlacementId,
                showOptions,
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowStart(placementId: String?) {
                        showStartTimeMs = System.currentTimeMillis()
                        isRewardedPlacementLoaded = false
                        scope.launch(Dispatchers.Main) {
                            _adState.value = AdState.DISPLAYED
                            _statusDetail.value = "Watching ad..."
                        }
                    }

                    override fun onUnityAdsShowClick(placementId: String?) {
                        Log.d(TAG, "UnityAds click on $placementId")
                    }

                    override fun onUnityAdsShowComplete(
                        placementId: String?,
                        state: UnityAds.UnityAdsShowCompletionState?
                    ) {
                        val elapsedSec = ((System.currentTimeMillis() - showStartTimeMs) / 1000L)
                            .toInt()
                            .coerceAtLeast(5)
                        scope.launch(Dispatchers.Main) {
                            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                _adState.value = AdState.VERIFICATION_PENDING
                                _statusDetail.value = "Verifying reward..."
                                onAdCompleted(sessionId, elapsedSec)
                            } else {
                                _adState.value = AdState.SKIPPED
                                val msg = "Ad closed early — watch until the end to earn coins."
                                _statusDetail.value = msg
                                onAdSkippedOrFailed(msg)
                            }
                        }
                    }

                    override fun onUnityAdsShowFailure(
                        placementId: String?,
                        error: UnityAds.UnityAdsShowError?,
                        message: String?
                    ) {
                        isRewardedPlacementLoaded = false
                        Log.w(TAG, "UnityAds show failure ($placementId): $error - $message")
                        scope.launch(Dispatchers.Main) {
                            _adState.value = AdState.FAILED
                            _statusDetail.value = AD_UNAVAILABLE_MESSAGE
                            onAdSkippedOrFailed(AD_UNAVAILABLE_MESSAGE)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            isRewardedPlacementLoaded = false
            _adState.value = AdState.FAILED
            _statusDetail.value = AD_UNAVAILABLE_MESSAGE
            onAdSkippedOrFailed(AD_UNAVAILABLE_MESSAGE)
        }
    }

    /**
     * Shows the Unity Cloud Interstitial Ad (`"All ads"` / `"All_ads"`) at natural transition points
     * (such as completing a tournament match) with a strict frequency cap (`INTERSTITIAL_COOLDOWN_MS`).
     * Never interrupts active gameplay, and if no ad is loaded, continues seamlessly.
     */
    fun showInterstitialAtTransition(
        activity: Activity?,
        scope: CoroutineScope,
        nowMs: Long = System.currentTimeMillis(),
        onTransitionContinue: () -> Unit = {}
    ): Boolean {
        if (nowMs - lastInterstitialShownAtMs < UnityAdsConfig.INTERSTITIAL_COOLDOWN_MS) {
            onTransitionContinue()
            return false
        }
        if (activity == null || (!isSdkInitialized && !UnityAds.isInitialized) || !isInterstitialPlacementLoaded) {
            if (activity != null) {
                loadInterstitialPlacement(scope)
            }
            onTransitionContinue()
            return false
        }

        return try {
            lastInterstitialShownAtMs = nowMs
            isInterstitialPlacementLoaded = false
            _interstitialState.value = AdState.DISPLAYED
            UnityAds.show(
                activity,
                activeInterstitialPlacementId,
                UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowStart(placementId: String?) {
                        Log.i(TAG, "UnityAds interstitial started: $placementId")
                    }

                    override fun onUnityAdsShowClick(placementId: String?) {
                        Log.d(TAG, "UnityAds interstitial clicked: $placementId")
                    }

                    override fun onUnityAdsShowComplete(
                        placementId: String?,
                        state: UnityAds.UnityAdsShowCompletionState?
                    ) {
                        scope.launch(Dispatchers.Main) {
                            _interstitialState.value = AdState.COMPLETED
                            loadInterstitialPlacement(scope)
                            onTransitionContinue()
                        }
                    }

                    override fun onUnityAdsShowFailure(
                        placementId: String?,
                        error: UnityAds.UnityAdsShowError?,
                        message: String?
                    ) {
                        Log.w(TAG, "UnityAds interstitial show failed ($placementId): $error - $message")
                        scope.launch(Dispatchers.Main) {
                            _interstitialState.value = AdState.FAILED
                            loadInterstitialPlacement(scope)
                            onTransitionContinue()
                        }
                    }
                }
            )
            true
        } catch (t: Throwable) {
            _interstitialState.value = AdState.FAILED
            onTransitionContinue()
            false
        }
    }

    /**
     * Shows a Unity Video/Interstitial Ad (`"All ads"` / `"All_ads"`) during Creator Video playback
     * (Pre-roll before video starts, Mid-roll at 50% for videos >= 60s, or Post-roll after video ends).
     * Always invokes `onResumeVideoPlayback` when the ad completes, is skipped, or fails to load,
     * so the creator's video never gets stuck or loops ads.
     */
    fun showCreatorVideoAdOpportunity(
        activity: Activity?,
        slotType: String,
        scope: CoroutineScope,
        onResumeVideoPlayback: (adShown: Boolean) -> Unit
    ) {
        if (activity == null || (!isSdkInitialized && !UnityAds.isInitialized) || !isInterstitialPlacementLoaded) {
            if (activity != null) {
                loadInterstitialPlacement(scope)
            }
            onResumeVideoPlayback(false)
            return
        }

        try {
            isInterstitialPlacementLoaded = false
            _interstitialState.value = AdState.DISPLAYED
            UnityAds.show(
                activity,
                activeInterstitialPlacementId,
                UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowStart(placementId: String?) {
                        Log.i(TAG, "UnityAds creator video ad ($slotType) started: $placementId")
                    }

                    override fun onUnityAdsShowClick(placementId: String?) {
                        Log.d(TAG, "UnityAds creator video ad ($slotType) clicked: $placementId")
                    }

                    override fun onUnityAdsShowComplete(
                        placementId: String?,
                        state: UnityAds.UnityAdsShowCompletionState?
                    ) {
                        scope.launch(Dispatchers.Main) {
                            _interstitialState.value = AdState.COMPLETED
                            loadInterstitialPlacement(scope)
                            onResumeVideoPlayback(true)
                        }
                    }

                    override fun onUnityAdsShowFailure(
                        placementId: String?,
                        error: UnityAds.UnityAdsShowError?,
                        message: String?
                    ) {
                        Log.w(TAG, "UnityAds creator video ad ($slotType) failed ($placementId): $error - $message")
                        scope.launch(Dispatchers.Main) {
                            _interstitialState.value = AdState.FAILED
                            loadInterstitialPlacement(scope)
                            onResumeVideoPlayback(false)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            _interstitialState.value = AdState.FAILED
            onResumeVideoPlayback(false)
        }
    }


    fun markBannerState(state: AdState) {
        _bannerState.value = state
    }

    fun markVerificationResult(approved: Boolean, message: String) {
        _adState.value = if (approved) AdState.APPROVED else AdState.REJECTED
        _statusDetail.value = message
    }

    fun resetToIdle() {
        _adState.value = if (isRewardedPlacementLoaded) AdState.READY else AdState.IDLE
        _statusDetail.value = if (isRewardedPlacementLoaded) "Ad ready" else "Ready"
    }

    companion object {
        private const val TAG = "FoundwayUnityAds"
        const val AD_UNAVAILABLE_MESSAGE = "Ad not available, try again"
    }
}

/**
 * Non-intrusive Unity Banner Ad container styled for the original MatchPanda white UI.
 * Loads a real 320x50 Unity `BannerView` when an `Activity` context is available,
 * and handles loading / no-fill states cleanly without overlapping buttons or gameplay.
 */
@Composable
fun UnityBannerAdSlot(
    unityAdManager: UnityAdManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val bannerState by unityAdManager.bannerState.collectAsState()
    var reloadTrigger by remember { mutableIntStateOf(0) }

    Surface(
        color = PandaWhiteCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PandaCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("unity_banner_ad_slot")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sponsored • Unity Ads (${unityAdManager.interstitialPlacementId})",
                    style = MaterialTheme.typography.labelSmall,
                    color = PandaMuted,
                    fontWeight = FontWeight.SemiBold
                )
                if (bannerState == AdState.FAILED) {
                    TextButton(
                        onClick = { reloadTrigger += 1 },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelSmall,
                            color = PandaGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (activity != null) {
                key(reloadTrigger) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                val act = ctx as? Activity
                                if (act != null && UnityAds.isInitialized) {
                                    unityAdManager.markBannerState(AdState.LOADING)
                                    val bannerView = BannerView(
                                        act,
                                        unityAdManager.bannerPlacementId,
                                        UnityBannerSize(320, 50)
                                    )
                                    bannerView.listener = object : BannerView.IListener {
                                        override fun onBannerLoaded(bannerAdView: BannerView?) {
                                            unityAdManager.markBannerState(AdState.READY)
                                        }

                                        override fun onBannerShown(bannerAdView: BannerView?) {
                                            unityAdManager.markBannerState(AdState.DISPLAYED)
                                        }

                                        override fun onBannerClick(bannerAdView: BannerView?) {}

                                        override fun onBannerFailedToLoad(
                                            bannerAdView: BannerView?,
                                            errorInfo: BannerErrorInfo?
                                        ) {
                                            unityAdManager.markBannerState(AdState.FAILED)
                                        }

                                        override fun onBannerLeftApplication(bannerView: BannerView?) {}
                                    }
                                    val lp = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                        Gravity.CENTER
                                    )
                                    addView(bannerView, lp)
                                    bannerView.load()
                                } else {
                                    unityAdManager.markBannerState(AdState.FAILED)
                                }
                            }
                        }
                    )
                }
            }

            if (bannerState == AdState.FAILED || activity == null) {
                Text(
                    text = UnityAdManager.AD_UNAVAILABLE_MESSAGE,
                    style = MaterialTheme.typography.labelSmall,
                    color = PandaInk.copy(alpha = 0.65f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
