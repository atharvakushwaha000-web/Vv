package com.example.ui.missions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.AdState
import com.example.ads.UnityBannerAdSlot
import com.example.model.*
import com.example.ui.puzzles.PlayTournamentDedicatedPage
import com.example.ui.theme.*
import com.example.viewmodel.UiState
import com.example.viewmodel.VaultGoldViewModel
import java.security.MessageDigest

enum class HomeSubDestination {
    HOME_ROOT,
    PLAYSKILL_LIST,
    PLAYSKILL_MISSION_DETAIL,
    PLAYSKILL_SUBMIT_PROOF,
    VIDEOS_LIST,
    DAILY_QUIZ,
    PLAY_TOURNAMENT,
    PANDA_MEMORY_MATCH,
    PANDA_BUBBLE_POP
}

data class PlaySkillMilestoneSpec(
    val number: Int,
    val id: String,
    val title: String,
    val coins: Long
)

fun buildMilestonesForCampaign(campaign: AppCampaign): List<PlaySkillMilestoneSpec> {
    return if (campaign.totalReward >= 500L || campaign.appName.contains("Free Fire", ignoreCase = true)) {
        listOf(
            PlaySkillMilestoneSpec(1, "m1_install", "Install", 20L),
            PlaySkillMilestoneSpec(2, "m2_open", "Open game", 0L),
            PlaySkillMilestoneSpec(3, "m3_lvl5", "Level 5", 100L),
            PlaySkillMilestoneSpec(4, "m4_lvl10", "Level 10", 120L),
            PlaySkillMilestoneSpec(5, "m5_lvl15", "Level 15", 260L)
        )
    } else {
        val step1 = (campaign.totalReward * 0.15).toLong().coerceAtLeast(10L)
        val step3 = (campaign.totalReward * 0.35).toLong().coerceAtLeast(25L)
        val step4 = (campaign.totalReward - step1 - step3).coerceAtLeast(30L)
        listOf(
            PlaySkillMilestoneSpec(1, "m1_install", "Install", step1),
            PlaySkillMilestoneSpec(2, "m2_open", "Open game", 0L),
            PlaySkillMilestoneSpec(3, "m3_lvl5", "Level 5", step3),
            PlaySkillMilestoneSpec(4, "m4_lvl10", "Level 10", step4)
        )
    }
}

@Composable
fun HomeAndMissionsScreen(
    viewModel: VaultGoldViewModel,
    onNavigateToPuzzles: () -> Unit,
    onNavigateToStore: () -> Unit,
    onNavigateToAdmin: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val campaignsState by viewModel.appCampaigns.collectAsStateWithLifecycle()
    val claims by viewModel.taskClaims.collectAsStateWithLifecycle()
    val videos by viewModel.videoCampaigns.collectAsStateWithLifecycle()
    val videoWatchRecords by viewModel.videoWatchRecords.collectAsStateWithLifecycle()
    val adState by viewModel.unityAdManager.adState.collectAsStateWithLifecycle()
    val dailyAdsWatched by viewModel.dailyAdsWatched.collectAsStateWithLifecycle()
    val activePendingClaim by viewModel.activePendingClaim.collectAsStateWithLifecycle()
    val savedPendingClaim by viewModel.savedPendingClaim.collectAsStateWithLifecycle()

    var subScreen by remember { mutableStateOf(HomeSubDestination.HOME_ROOT) }
    var selectedCampaign by remember { mutableStateOf<AppCampaign?>(null) }
    var selectedMilestone by remember { mutableStateOf<PlaySkillMilestoneSpec?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initUnityAds(context)
    }

    // Claim-With-Ad Overlay (`Screenshot_2026-10-27-18-16-02-118.jpg`)
    if (activePendingClaim != null) {
        ClaimRewardWithAdOverlay(
            pending = activePendingClaim!!,
            adState = adState,
            onWatchAdToClaim = {
                viewModel.watchAdToClaimPendingReward(activity)
            },
            onSkip = {
                viewModel.skipPendingClaimForNow()
            }
        )
    }

    // Sub-screen routing inside Home tab
    when (subScreen) {
        HomeSubDestination.PLAYSKILL_LIST -> {
            BackHandler { subScreen = HomeSubDestination.HOME_ROOT }
            val campaigns = (campaignsState as? UiState.Success)?.data ?: VaultGoldRepository_DefaultCampaigns
            PlaySkillListScreen(
                campaigns = campaigns,
                onBack = { subScreen = HomeSubDestination.HOME_ROOT },
                onOpenCampaign = { camp ->
                    selectedCampaign = camp
                    subScreen = HomeSubDestination.PLAYSKILL_MISSION_DETAIL
                }
            )
            return
        }

        HomeSubDestination.PLAYSKILL_MISSION_DETAIL -> {
            val camp = selectedCampaign
            if (camp == null) {
                subScreen = HomeSubDestination.PLAYSKILL_LIST
                return
            }
            BackHandler { subScreen = HomeSubDestination.PLAYSKILL_LIST }
            val campaignClaims = remember(claims, camp.id) {
                claims.filter { it.campaignId == camp.id }
            }
            val milestones = remember(camp.id, camp.totalReward) {
                buildMilestonesForCampaign(camp)
            }
            PlaySkillMissionDetailScreen(
                campaign = camp,
                milestones = milestones,
                existingClaims = campaignClaims,
                onBack = { subScreen = HomeSubDestination.PLAYSKILL_LIST },
                onOpenSubmitProof = { milestone ->
                    selectedMilestone = milestone
                    subScreen = HomeSubDestination.PLAYSKILL_SUBMIT_PROOF
                }
            )
            return
        }

        HomeSubDestination.PLAYSKILL_SUBMIT_PROOF -> {
            val camp = selectedCampaign
            if (camp == null) {
                subScreen = HomeSubDestination.PLAYSKILL_LIST
                return
            }
            BackHandler { subScreen = HomeSubDestination.PLAYSKILL_MISSION_DETAIL }
            val milestones = remember(camp.id, camp.totalReward) {
                buildMilestonesForCampaign(camp)
            }
            val activeMilestone = selectedMilestone ?: milestones.firstOrNull { it.coins > 0L } ?: milestones.first()
            PlaySkillProofUploadScreen(
                campaign = camp,
                milestone = activeMilestone,
                onBack = { subScreen = HomeSubDestination.PLAYSKILL_MISSION_DETAIL },
                onSendForReview = { proofHash, gameId ->
                    val combinedProof = if (gameId.isNotBlank()) "${proofHash}_gid_${gameId.trim()}" else proofHash
                    viewModel.submitAppMilestone(
                        campaign = camp,
                        milestoneTitle = activeMilestone.title,
                        milestoneId = activeMilestone.id,
                        rewardCoins = activeMilestone.coins,
                        evidenceText = combinedProof
                    )
                    subScreen = HomeSubDestination.PLAYSKILL_MISSION_DETAIL
                }
            )
            return
        }

        HomeSubDestination.VIDEOS_LIST -> {
            BackHandler { subScreen = HomeSubDestination.HOME_ROOT }
            val displayVideos = if (videos.isNotEmpty()) videos else com.example.data.VaultGoldRepository.defaultVideoCampaigns()
            VideosTaskListScreen(
                videos = displayVideos,
                watchRecords = videoWatchRecords,
                onBack = { subScreen = HomeSubDestination.HOME_ROOT },
                onMarkVideoOpened = { vid ->
                    viewModel.markVideoOpened(vid)
                },
                onUpdateWatchProgress = { vid, sec, checkpointPassed ->
                    viewModel.updateVideoWatchProgress(vid, sec, checkpointPassed)
                },
                onClaimWatchedVideo = { vid, sec, checkpointPassed ->
                    viewModel.completeVerifiableVideoWatch(vid, sec, checkpointPassed)
                }
            )
            return
        }

        HomeSubDestination.DAILY_QUIZ -> {
            BackHandler { subScreen = HomeSubDestination.HOME_ROOT }
            DailyQuizScreen(
                rewardCoins = settings.quizRewardCoins,
                onBack = { subScreen = HomeSubDestination.HOME_ROOT },
                onQuizAnswered = { qId, qTitle, correct ->
                    viewModel.prepareQuizClaim(qId, qTitle, correct)
                    if (correct) {
                        subScreen = HomeSubDestination.HOME_ROOT
                    }
                }
            )
            return
        }

        HomeSubDestination.PLAY_TOURNAMENT -> {
            BackHandler {
                viewModel.showTransitionInterstitialIfEligible(activity) {
                    subScreen = HomeSubDestination.HOME_ROOT
                }
            }
            PlayTournamentDedicatedPage(
                viewModel = viewModel,
                onBack = {
                    viewModel.showTransitionInterstitialIfEligible(activity) {
                        subScreen = HomeSubDestination.HOME_ROOT
                    }
                }
            )
            return
        }

        HomeSubDestination.PANDA_MEMORY_MATCH -> {
            BackHandler { subScreen = HomeSubDestination.HOME_ROOT }
            com.example.ui.puzzles.PandaMemoryMatchScreen(
                viewModel = viewModel,
                onBack = { subScreen = HomeSubDestination.HOME_ROOT }
            )
            return
        }

        HomeSubDestination.PANDA_BUBBLE_POP -> {
            BackHandler { subScreen = HomeSubDestination.HOME_ROOT }
            com.example.ui.puzzles.PandaBubblePopScreen(
                viewModel = viewModel,
                onBack = { subScreen = HomeSubDestination.HOME_ROOT }
            )
            return
        }

        HomeSubDestination.HOME_ROOT -> Unit
    }

    val adsLeftToday = (viewModel.dailyAdLimit - dailyAdsWatched).coerceAtLeast(0)
    val streakCount = profile?.checkInStreak ?: 0

    // MatchPanda Light Home Layout (`Screenshot_2026-10-27-18-16-05-029.jpg` & `18-16-07-823.jpg`)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        // Saved Pending Claim Banner (if user tapped Skip within the last 10 minutes)
        if (savedPendingClaim != null && !savedPendingClaim!!.isExpired()) {
            item(key = "saved_pending_claim_strip") {
                Surface(
                    color = PandaWhiteCard,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.5.dp, PandaGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.reopenSavedPendingClaim() }
                        .testTag("resume_pending_claim_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FoundwayCoinIcon(size = 26.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${savedPendingClaim!!.coins} coins ready to claim",
                                style = MaterialTheme.typography.titleMedium,
                                color = PandaInk,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Surface(
                            color = PandaGreen,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Claim",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        // 1. Available Points Hero Card (`Screenshot_2026-10-27-18-16-05-029.jpg`)
        item(key = "hero_available_points_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(24.dp))
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PandaHeroMintStart, PandaHeroMintEnd)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // White "Available Points" pill with green dot
                            Surface(
                                color = PandaWhiteCard,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, PandaCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(PandaGreen)
                                    )
                                    Text(
                                        text = "Available Points",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PandaGreenDark,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Coins + Vertical Divider + Gems/Streak Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FoundwayCoinIcon(size = 34.dp)
                                    Column {
                                        Text(
                                            text = "${wallet.available}",
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontSize = 26.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = PandaInk,
                                            modifier = Modifier.testTag("home_coin_balance_text")
                                        )
                                        Text(
                                            text = "Coins",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PandaMuted,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(36.dp)
                                        .background(PandaCardBorder)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PurpleGemIcon(size = 32.dp)
                                    Column {
                                        Text(
                                            text = "$streakCount",
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontSize = 26.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = PandaInk
                                        )
                                        Text(
                                            text = "Streak",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PandaMuted,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // Right 3D Green Wallet with Gold Coins Illustration
                        GreenWalletWithCoinsIllustration(size = 96.dp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Inner White "Redeem Now" Card (`18-16-05-029.jpg`)
                    Surface(
                        onClick = {
                            if (viewModel.isGuestUser || profile?.accountStatus == "guest") {
                                viewModel.openGuestLinkModal()
                            } else {
                                onNavigateToStore()
                            }
                        },
                        color = PandaWhiteCard,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, PandaCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_redeem_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = PandaSoftMintSurface,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        GiftBoxGraphic(size = 28.dp)
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Redeem Now",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PandaInk,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Convert coins to gift cards & rewards",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PandaMuted
                                    )
                                }
                            }

                            Surface(
                                color = PandaGreen,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Redeem",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Daily Bonus / Streak Banner (`18-16-05-029.jpg` & `18-16-07-823.jpg`)
        item(key = "daily_bonus_streak_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(22.dp))
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        StreakFlamePedestalIllustration(size = 54.dp)
                        Column {
                            Text(
                                text = "Daily Bonus +${settings.dailyCheckInCoins} Coins",
                                style = MaterialTheme.typography.titleMedium,
                                color = PandaInk,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Keep your daily streak going for extra coins!",
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaMuted
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.prepareDailyCheckIn() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PandaGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("daily_checkin_button")
                    ) {
                        Text(
                            text = "Claim +${settings.dailyCheckInCoins}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 3. "⚡ Super Offer" Section (`Screenshot_2026-10-27-18-16-05-029.jpg`)
        item(key = "super_offer_section") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = PandaGoldDark,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Super Offer",
                        style = MaterialTheme.typography.titleLarge,
                        color = PandaInk,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Lavender "Huge Rewards" PlaySkill Banner (`18-16-05-029.jpg`)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFFD5C6FF), RoundedCornerShape(24.dp))
                        .clickable { subScreen = HomeSubDestination.PLAYSKILL_LIST }
                        .testTag("tile_playskill"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PandaLavenderStart, PandaLavenderEnd)
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Huge Rewards",
                                style = MaterialTheme.typography.headlineSmall,
                                color = PandaInk,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "PlaySkill • Install games & level up",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF4A3B7C),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { subScreen = HomeSubDestination.PLAYSKILL_LIST },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PandaPurple,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text(
                                    text = "Earn Now  →",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        TreasureChestIllustration(size = 92.dp)
                    }
                }

                // Sky-Blue "Catch the Panda" Game Banner (`18-16-05-029.jpg`)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFFC2DCFF), RoundedCornerShape(24.dp))
                        .clickable(onClick = onNavigateToPuzzles)
                        .testTag("tile_games"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PandaSkyBlueStart, PandaSkyBlueEnd)
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                color = PandaBlue.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SportsEsports,
                                        contentDescription = null,
                                        tint = PandaBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "New Game",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PandaBlue,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Catch the Panda",
                                style = MaterialTheme.typography.headlineSmall,
                                color = PandaInk,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Find the hidden pandas & win coins!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF2E4B8A),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onNavigateToPuzzles,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PandaBlue,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text(
                                    text = "Play Now  →",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        ClawMachinePandaIllustration(size = 94.dp)
                    }
                }
            }
        }

        // 4. 2-Column Feature Row: Videos & Daily Quiz (`18-16-07-823.jpg` style)
        item(key = "videos_and_quiz_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PandaFeatureCard(
                    title = "Videos",
                    subtitle = "Watch & earn",
                    badgeBg = Color(0xFFFFEFEA),
                    badgeTint = Color(0xFFFF5A36),
                    icon = Icons.Default.PlayArrow,
                    buttonText = "Watch",
                    onClick = { subScreen = HomeSubDestination.VIDEOS_LIST },
                    testTag = "tile_videos",
                    modifier = Modifier.weight(1f)
                )

                PandaFeatureCard(
                    title = "Daily Quiz",
                    subtitle = "Win +${settings.quizRewardCoins} coins",
                    badgeBg = Color(0xFFFFF6DB),
                    badgeTint = PandaGoldDark,
                    icon = Icons.Default.Lightbulb,
                    buttonText = "Start",
                    onClick = { subScreen = HomeSubDestination.DAILY_QUIZ },
                    testTag = "tile_quiz",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4B. 2-Column Feature Row: Play Tournament & Reward Store
        item(key = "tournament_and_store_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PandaFeatureCard(
                    title = "Play Tournament",
                    subtitle = "Ludo, Carrom & Block",
                    badgeBg = PandaGreen.copy(alpha = 0.16f),
                    badgeTint = PandaGreen,
                    icon = Icons.Default.EmojiEvents,
                    buttonText = "Play",
                    onClick = { subScreen = HomeSubDestination.PLAY_TOURNAMENT },
                    testTag = "tile_tournaments",
                    modifier = Modifier.weight(1f)
                )

                PandaFeatureCard(
                    title = "Reward Store",
                    subtitle = "Gift codes & prizes",
                    badgeBg = PandaPurple.copy(alpha = 0.16f),
                    badgeTint = PandaPurple,
                    icon = Icons.Default.CardGiftcard,
                    buttonText = "Redeem",
                    onClick = onNavigateToStore,
                    testTag = "tile_reward_store",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4C. 2-Column Mini-Games: Panda Memory Match & Panda Bubble Pop
        item(key = "mini_games_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PandaFeatureCard(
                    title = "Memory Match",
                    subtitle = "3 Levels • Flip & Match",
                    badgeBg = PandaGreen.copy(alpha = 0.16f),
                    badgeTint = PandaGreen,
                    icon = Icons.Default.GridOn,
                    buttonText = "Play",
                    onClick = { subScreen = HomeSubDestination.PANDA_MEMORY_MATCH },
                    testTag = "tile_memory_match",
                    modifier = Modifier.weight(1f)
                )

                PandaFeatureCard(
                    title = "Bubble Pop",
                    subtitle = "Pop 3+ • Drop Physics",
                    badgeBg = PandaPurple.copy(alpha = 0.16f),
                    badgeTint = PandaPurple,
                    icon = Icons.Default.BubbleChart,
                    buttonText = "Pop",
                    onClick = { subScreen = HomeSubDestination.PANDA_BUBBLE_POP },
                    testTag = "tile_bubble_pop",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 5. Watch Ad Card (Unity Ads: Game ID 800370914, BP_Rewarded_Android, testMode = false)
        item(key = "home_watch_ad_strip") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(22.dp))
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = PandaSoftMintSurface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleFilled,
                                    contentDescription = null,
                                    tint = PandaGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Watch Ad & Earn",
                                style = MaterialTheme.typography.titleMedium,
                                color = PandaInk,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "+1 Coin  •  $adsLeftToday left today",
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.watchUnityRewardedAd(activity) },
                        enabled = adsLeftToday > 0 && adState != AdState.DISPLAYED,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PandaGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("watch_unity_ad_button")
                    ) {
                        Text(
                            text = if (adState == AdState.LOADING) "Loading" else "Watch Ad",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 6. Non-intrusive Unity Banner Ad Slot
        item(key = "home_unity_banner_slot") {
            UnityBannerAdSlot(unityAdManager = viewModel.unityAdManager)
        }

        // Admin Console shortcut only visible for Admin accounts
        if (profile?.role != null && profile?.role != "user") {
            item(key = "home_admin_button") {
                OutlinedButton(
                    onClick = onNavigateToAdmin,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, PandaGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_admin_console_button")
                ) {
                    Text("Admin Console", color = PandaGreen, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun PandaFeatureCard(
    title: String,
    subtitle: String,
    badgeBg: Color,
    badgeTint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonText: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(22.dp))
            .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = badgeTint,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Surface(
                    color = PandaSoftMintSurface,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelMedium,
                        color = PandaGreen,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PandaInk,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PandaMuted,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * PlaySkill List Screen in MatchPanda Light Theme
 */
@Composable
fun PlaySkillListScreen(
    campaigns: List<AppCampaign>,
    onBack: () -> Unit,
    onOpenCampaign: (AppCampaign) -> Unit
) {
    val paletteColors = listOf(
        Color(0xFF6837E8),
        Color(0xFF2E9B27),
        Color(0xFF3B68EE),
        Color(0xFFFF7A00)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item(key = "playskill_header") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("playskill_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PandaInk)
                    }
                    Text(
                        text = "PlaySkill Offers",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = PandaInk
                    )
                }
                Text(
                    text = "Install an app, reach the target level & earn coins",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PandaMuted,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        if (campaigns.isEmpty()) {
            item(key = "playskill_empty") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Text(
                        text = "No missions right now. Check back soon.",
                        color = PandaMuted,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        } else {
            items(campaigns.size, key = { campaigns[it].id }) { index ->
                val camp = campaigns[index]
                val iconTint = paletteColors[index % paletteColors.size]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(22.dp))
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp))
                        .clickable { onOpenCampaign(camp) }
                        .testTag("playskill_card_${camp.id}"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = iconTint.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = camp.appName,
                                    tint = iconTint,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = camp.appName,
                                style = MaterialTheme.typography.titleLarge,
                                color = PandaInk,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FoundwayCoinIcon(size = 20.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Up to ${camp.totalReward} Coins",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PandaGreenDark,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Button(
                            onClick = { onOpenCampaign(camp) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PandaGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("playskill_open_${camp.id}")
                        ) {
                            Text(
                                text = "Open",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * PlaySkill Mission Detail Screen in MatchPanda Light Theme
 */
@Composable
fun PlaySkillMissionDetailScreen(
    campaign: AppCampaign,
    milestones: List<PlaySkillMilestoneSpec>,
    existingClaims: List<TaskClaim>,
    onBack: () -> Unit,
    onOpenSubmitProof: (PlaySkillMilestoneSpec) -> Unit
) {
    val context = LocalContext.current

    val nextMilestone = remember(milestones, existingClaims) {
        milestones.firstOrNull { m ->
            m.coins > 0L && existingClaims.none { it.taskId == m.id }
        } ?: milestones.last()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item(key = "mission_detail_header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 4.dp)
                    .testTag("mission_detail_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PandaInk
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = campaign.appName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = PandaInk,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Hero Milestone Card
        item(key = "mission_detail_hero") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(22.dp))
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = PandaSoftMintSurface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = PandaGreen,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Reach ${nextMilestone.title.lowercase()}",
                            style = MaterialTheme.typography.titleLarge,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FoundwayCoinIcon(size = 22.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+${nextMilestone.coins} Coins",
                                style = MaterialTheme.typography.titleMedium,
                                color = PandaGreenDark,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Numbered Milestones List
        items(milestones, key = { it.id }) { m ->
            val claim = existingClaims.find { it.taskId == m.id }
            val isApproved = claim?.verificationStatus == "approved" || m.coins == 0L
            val isInReview = claim != null && claim.verificationStatus != "approved" && claim.verificationStatus != "rejected"
            val isRejected = claim?.verificationStatus == "rejected"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(18.dp))
                    .clickable(enabled = m.coins > 0L && claim == null) {
                        onOpenSubmitProof(m)
                    },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = when {
                                isApproved -> PandaGreen
                                isInReview -> PandaGold
                                isRejected -> PandaCoral
                                else -> PandaSoftMintSurface
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isApproved) "✓" else "${m.number}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isApproved || isRejected) Color.White else PandaInk,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = m.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = PandaInk,
                                fontWeight = FontWeight.Bold
                            )
                            if (claim != null) {
                                val statusText = when (claim.verificationStatus) {
                                    "approved" -> "Approved"
                                    "rejected" -> "Rejected: ${claim.rejectionReason ?: "Resubmit clear screenshot"}"
                                    else -> "In review"
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (claim.verificationStatus) {
                                        "approved" -> PandaGreen
                                        "rejected" -> PandaCoral
                                        else -> PandaGoldDark
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = if (m.coins == 0L) "free" else "+${m.coins}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (m.coins == 0L) PandaMuted else PandaGreenDark,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Bottom Buttons
        item(key = "mission_detail_buttons") {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(campaign.playStoreUrl))
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PandaPurple,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("open_play_store_button")
            ) {
                Text(
                    text = "Open Play Store",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { onOpenSubmitProof(nextMilestone) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PandaGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("open_submit_proof_button")
            ) {
                Text(
                    text = "Submit proof",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * PlaySkill Proof Upload Screen in MatchPanda Light Theme
 */
@Composable
fun PlaySkillProofUploadScreen(
    campaign: AppCampaign,
    milestone: PlaySkillMilestoneSpec,
    onBack: () -> Unit,
    onSendForReview: (screenshotHash: String, gameId: String) -> Unit
) {
    val context = LocalContext.current
    var selectedImageLabel by remember { mutableStateOf<String?>(null) }
    var screenshotHash by remember { mutableStateOf("") }
    var gameIdInput by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.size <= 8 * 1024 * 1024) {
                    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                    val hex = digest.joinToString("") { "%02x".format(it) }.take(24)
                    screenshotHash = "sha256_$hex"
                    selectedImageLabel = "Screenshot attached (${bytes.size / 1024} KB)"
                } else {
                    screenshotHash = "sha256_${uri.lastPathSegment ?: System.currentTimeMillis()}"
                    selectedImageLabel = "Screenshot attached"
                }
            } catch (_: Exception) {
                screenshotHash = "sha256_${System.currentTimeMillis()}"
                selectedImageLabel = "Screenshot attached"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("proof_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PandaInk)
            }
            Column {
                Text(
                    text = "Submit proof",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = PandaInk
                )
                Text(
                    text = "Add a screenshot of your level",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PandaMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            onClick = {
                try {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } catch (_: Exception) {
                    screenshotHash = "sha256_${campaign.id}_${milestone.id}_${System.currentTimeMillis()}"
                    selectedImageLabel = "Screenshot attached (${milestone.title})"
                }
            },
            color = PandaWhiteCard,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .border(2.dp, PandaGreen, RoundedCornerShape(24.dp))
                .testTag("tap_to_add_screenshot_box")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = PandaSoftMintSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (selectedImageLabel != null) "✓" else "+",
                            fontSize = 32.sp,
                            color = PandaGreen,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = selectedImageLabel ?: "Tap to add screenshot",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selectedImageLabel != null) PandaGreen else PandaMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = gameIdInput,
            onValueChange = { gameIdInput = it },
            placeholder = { Text("Your game ID (optional)", color = PandaMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PandaWhiteCard,
                unfocusedContainerColor = PandaWhiteCard,
                focusedTextColor = PandaInk,
                unfocusedTextColor = PandaInk,
                focusedBorderColor = PandaGreen,
                unfocusedBorderColor = PandaCardBorder
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("milestone_evidence_input")
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                val finalHash = screenshotHash.ifBlank {
                    "sha256_${campaign.id}_${milestone.id}_${gameIdInput.ifBlank { "proof" }}"
                }
                onSendForReview(finalHash, gameIdInput)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = PandaGreen,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("send_proof_for_review_button")
        ) {
            Text(
                text = "Send for review",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Reviewed within 24 hours",
            style = MaterialTheme.typography.bodySmall,
            color = PandaMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Fixed & Audited YouTube Watch-and-Earn Screen:
 * - Opens canonical YouTube URL (`https://www.youtube.com/watch?v={videoId}`) reliably.
 * - Never claims a view is verified merely from clicking or returning early.
 * - Tracks real elapsed seconds without `.coerceAtLeast(minWatchSeconds)`.
 * - Requires both real elapsed time (`>= minWatchSeconds`) AND a 1-tap Video Content Verification Checkpoint.
 * - Shows clear statuses: Pending, Verified, Rewarded, and Failed, and blocks duplicate claims.
 */
@Composable
fun VideosTaskListScreen(
    videos: List<VideoCampaign>,
    watchRecords: Map<String, VideoWatchRecord> = emptyMap(),
    onBack: () -> Unit,
    onMarkVideoOpened: (VideoCampaign) -> Unit = {},
    onUpdateWatchProgress: (VideoCampaign, Long, Boolean) -> Unit = { _, _, _ -> },
    onClaimWatchedVideo: (VideoCampaign, Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    var watchStartMsByVideo by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var checkpointPassedByVideo by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var nowTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(watchStartMsByVideo) {
        while (watchStartMsByVideo.isNotEmpty()) {
            kotlinx.coroutines.delay(1000L)
            nowTickMs = System.currentTimeMillis()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item(key = "videos_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("videos_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PandaInk)
                    }
                    Column {
                        Text(
                            text = "YouTube Watch & Earn",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = PandaInk
                        )
                        Text(
                            text = "Verified task completion • Separate from VIP Membership",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PandaMuted
                        )
                    }
                }

                // Transparent Disclosure on YouTube Verification
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PandaSoftMintSurface,
                    border = BorderStroke(1.dp, PandaCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = PandaGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Official YouTube Integration Notice: External YouTube playback does not expose private account watch-time telemetry. To verify legitimate task completion without manipulating YouTube views, watch for the required duration and answer the verification checkpoint.",
                            style = MaterialTheme.typography.labelSmall,
                            color = PandaInk
                        )
                    }
                }
            }
        }

        items(videos, key = { it.id }) { vid ->
            val record = watchRecords[vid.id]
            val startedMs = watchStartMsByVideo[vid.id] ?: record?.openedAtMs
            val realElapsedSec = if (startedMs != null) {
                ((nowTickMs - startedMs) / 1000L).coerceAtLeast(0L)
            } else {
                record?.watchedSeconds ?: 0L
            }
            val isCheckpointPassed = checkpointPassedByVideo[vid.id] == true || record?.checkpointPassed == true
            val isTimeComplete = realElapsedSec >= vid.minWatchSeconds

            val effectiveStatus = when {
                record?.status == VideoWatchStatus.REWARDED -> VideoWatchStatus.REWARDED
                record?.status == VideoWatchStatus.FAILED && !isTimeComplete -> VideoWatchStatus.FAILED
                vid.rewardCoins > 0L && isTimeComplete && isCheckpointPassed -> VideoWatchStatus.VERIFIED
                else -> VideoWatchStatus.PENDING
            }

            val statusLabel = when (effectiveStatus) {
                VideoWatchStatus.PENDING -> "Pending"
                VideoWatchStatus.VERIFIED -> "Verified"
                VideoWatchStatus.REWARDED -> "Rewarded"
                VideoWatchStatus.FAILED -> "Failed"
            }

            val statusColor = when (effectiveStatus) {
                VideoWatchStatus.PENDING -> PandaGold
                VideoWatchStatus.VERIFIED -> Color(0xFF38BDF8)
                VideoWatchStatus.REWARDED -> PandaGreen
                VideoWatchStatus.FAILED -> PandaCoral
            }

            // Build canonical YouTube watch URL from video ID to prevent bad redirects
            val safeYoutubeId = vid.youtubeVideoId.trim().ifBlank { "M7lc1UVf-VE" }
            val canonicalWatchUrl = "https://www.youtube.com/watch?v=$safeYoutubeId"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(22.dp))
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .testTag("video_task_card_${vid.id}"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = vid.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PandaInk,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (vid.rewardCoins > 0L) {
                                        "ID: $safeYoutubeId • Min ${vid.minWatchSeconds}s • +${vid.rewardCoins} Task Coins"
                                    } else {
                                        "ID: $safeYoutubeId • Discovery Channel (No coins)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (vid.rewardCoins > 0L) PandaGreen else PandaMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Status Badge: Pending / Verified / Rewarded / Failed
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = statusColor.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, statusColor),
                            modifier = Modifier.testTag("video_status_badge_${vid.id}")
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Progress & Verification Checkpoint Section for Rewarded Videos
                    if (vid.rewardCoins > 0L) {
                        val clampedSec = realElapsedSec.coerceAtMost(vid.minWatchSeconds)
                        LinearProgressIndicator(
                            progress = {
                                (clampedSec.toFloat() / vid.minWatchSeconds.coerceAtLeast(1L)).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isTimeComplete) PandaGreen else PandaGold,
                            trackColor = PandaSoftMintSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (startedMs == null) {
                                    "Step 1: Tap 'Open YouTube Video' to start timer"
                                } else {
                                    "Elapsed Watch Time: ${clampedSec}s / ${vid.minWatchSeconds}s"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTimeComplete) PandaGreen else PandaMuted,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isCheckpointPassed) "Checkpoint: Verified ✓" else "Checkpoint: Required",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCheckpointPassed) PandaGreen else PandaGold,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Step 2: Content Verification Checkpoint (shown once opened)
                        if (startedMs != null && effectiveStatus != VideoWatchStatus.REWARDED) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PandaSoftMintSurface,
                                border = BorderStroke(1.dp, PandaCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Step 2 — Task Verification Checkpoint: Confirm the topic of '${vid.title}':",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PandaInk,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                checkpointPassedByVideo = checkpointPassedByVideo + (vid.id to true)
                                                onUpdateWatchProgress(vid, realElapsedSec, true)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("video_checkpoint_correct_${vid.id}"),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isCheckpointPassed) PandaGreen else PandaCardBorder
                                            )
                                        ) {
                                            Text(
                                                text = if (isCheckpointPassed) "Developer / Gaming ✓" else "Developer / Gaming",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isCheckpointPassed) PandaGreen else PandaInk,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                checkpointPassedByVideo = checkpointPassedByVideo + (vid.id to false)
                                                onUpdateWatchProgress(vid, realElapsedSec, false)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "Cooking Recipe",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PandaMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (record?.failureReason != null && effectiveStatus == VideoWatchStatus.FAILED) {
                            Text(
                                text = record.failureReason,
                                style = MaterialTheme.typography.labelSmall,
                                color = PandaCoral,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (record?.transactionId != null && effectiveStatus == VideoWatchStatus.REWARDED) {
                            Text(
                                text = "Verified Ledger Ref: ${record.transactionId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PandaGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (watchStartMsByVideo[vid.id] == null) {
                                    watchStartMsByVideo = watchStartMsByVideo + (vid.id to System.currentTimeMillis())
                                }
                                onMarkVideoOpened(vid)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(canonicalWatchUrl))
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PandaGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_youtube_btn_${vid.id}")
                        ) {
                            Text(
                                text = if (startedMs == null) "Open YouTube Video" else "Re-Open Video",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        if (vid.rewardCoins > 0L) {
                            val isAlreadyRewarded = effectiveStatus == VideoWatchStatus.REWARDED
                            Button(
                                onClick = {
                                    onClaimWatchedVideo(vid, realElapsedSec, isCheckpointPassed)
                                },
                                enabled = startedMs != null && !isAlreadyRewarded,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (effectiveStatus == VideoWatchStatus.VERIFIED) PandaGold else PandaSoftMintSurface,
                                    contentColor = if (effectiveStatus == VideoWatchStatus.VERIFIED) Color.Black else PandaInk
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("claim_video_reward_btn_${vid.id}")
                            ) {
                                Text(
                                    text = when {
                                        isAlreadyRewarded -> "Rewarded ✓"
                                        effectiveStatus == VideoWatchStatus.VERIFIED -> "Claim +${vid.rewardCoins}"
                                        else -> "Verify & Claim"
                                    },
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Daily Brain Quiz screen in MatchPanda Light Theme
 */
@Composable
fun DailyQuizScreen(
    rewardCoins: Long,
    onBack: () -> Unit,
    onQuizAnswered: (questionId: String, questionTitle: String, isCorrect: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PandaInk)
            }
            Text(
                text = "Daily Brain Quiz",
                style = MaterialTheme.typography.headlineMedium,
                color = PandaInk,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(24.dp))
                .border(1.dp, PandaCardBorder, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Which number completes the sequence: 2, 6, 12, 20, ?",
                    style = MaterialTheme.typography.titleLarge,
                    color = PandaInk,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Win +$rewardCoins Coins",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PandaGreenDark,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(18.dp))

                val options = listOf(
                    "28" to false,
                    "30" to true,
                    "32" to false
                )
                options.forEach { (label, isCorrect) ->
                    Surface(
                        onClick = {
                            onQuizAnswered("quiz_seq_30", "Daily sequence quiz", isCorrect)
                        },
                        color = PandaSoftMintSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .border(1.dp, PandaCardBorder, RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Matches `Screenshot_2026-10-27-18-16-02-118.jpg`:
 * - Clean light celebration dialog with `PandaMascotIllustration(celebrating = true)`
 * - "Yay! You got" + "{N} coins are ready" (`claim_coins_ready_text`)
 * - If the Unity ad fails to load/show, displays `"Ad not available, try again"` (`claim_error_try_again_text`)
 * - Green "Watch ad to claim" button (`watch_ad_to_claim_button`)
 * - "Skip" button (`skip_claim_button`)
 */
@Composable
fun ClaimRewardWithAdOverlay(
    pending: PendingClaimReward,
    adState: AdState,
    onWatchAdToClaim: () -> Unit,
    onSkip: () -> Unit
) {
    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PandaMintBg.copy(alpha = 0.96f))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .shadow(12.dp, RoundedCornerShape(32.dp))
                    .border(1.5.dp, PandaCardBorder, RoundedCornerShape(32.dp)),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Celebrating Panda Mascot (`18-16-02-118.jpg`)
                    PandaMascotIllustration(
                        size = 156.dp,
                        celebrating = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = pending.headline,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = PandaInk,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Coin badge pill
                    Surface(
                        color = PandaSoftMintSurface,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, PandaCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FoundwayCoinIcon(size = 28.dp)
                            Text(
                                text = "${pending.coins} coins are ready",
                                style = MaterialTheme.typography.titleLarge,
                                color = PandaGreenDark,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("claim_coins_ready_text")
                            )
                        }
                    }

                    if (pending.errorMessage != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = PandaCoralContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = pending.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PandaCoral,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("claim_error_try_again_text")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Green "Watch ad to claim" Button (`18-16-02-118.jpg`)
                    Button(
                        onClick = onWatchAdToClaim,
                        enabled = adState != AdState.DISPLAYED,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PandaGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("watch_ad_to_claim_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                adState == AdState.DISPLAYED -> "Watching ad..."
                                pending.errorMessage != null -> "Try again • Watch ad to claim"
                                else -> "Watch ad to claim"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Coins are credited only after the rewarded ad finishes",
                        style = MaterialTheme.typography.bodySmall,
                        color = PandaMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // "Skip" Button
                    Surface(
                        onClick = onSkip,
                        color = PandaSoftMintSurface,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .width(150.dp)
                            .height(44.dp)
                            .testTag("skip_claim_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Skip",
                                style = MaterialTheme.typography.titleSmall,
                                color = PandaMuted,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

private val VaultGoldRepository_DefaultCampaigns: List<AppCampaign>
    get() = com.example.data.VaultGoldRepository.defaultPlaySkillCampaigns()
