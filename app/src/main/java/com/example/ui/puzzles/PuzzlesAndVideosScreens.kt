package com.example.ui.puzzles

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.UnityBannerAdSlot
import com.example.model.BotDifficulty
import com.example.model.HintProgressState
import com.example.model.TournamentTierSpec
import com.example.ui.missions.ClaimRewardWithAdOverlay
import com.example.ui.theme.*
import com.example.viewmodel.VaultGoldViewModel
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.random.Random

data class BrainPuzzleSpec(
    val gameId: String,
    val title: String,
    val subtitle: String,
    val instructions: String,
    val difficultyLabel: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val rewardCoins: Long,
    val hintExplanation: String = ""
)

private enum class CrateSlotType { PANDA, BOMB, EMPTY }

private enum class PandaRoundState {
    MEMORIZING,
    PLAYING,
    BOMB_HIT,
    WON
}

@Composable
fun PlayTournamentDedicatedPage(
    viewModel: VaultGoldViewModel,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .testTag("play_tournament_dedicated_page")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = onBack,
                color = PandaWhiteCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, PandaCardBorder),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("play_tournament_back_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PandaInk
                    )
                }
            }
            Column {
                Text(
                    text = "Play 1v1 Tournament",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = PandaInk
                )
                Text(
                    text = "Strictly 1v1 (2 Players Max) • Ludo & Carrom Only",
                    style = MaterialTheme.typography.bodySmall,
                    color = PandaMuted
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            PuzzlesAndVideosScreen(
                viewModel = viewModel,
                initialSubTab = 1
            )
        }
    }
}

@Composable
fun PuzzlesAndVideosScreen(
    viewModel: VaultGoldViewModel,
    initialSubTab: Int = 0
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val adState by viewModel.unityAdManager.adState.collectAsStateWithLifecycle()
    val activePendingClaim by viewModel.activePendingClaim.collectAsStateWithLifecycle()
    val puzzleAttempts by viewModel.puzzleAttempts.collectAsStateWithLifecycle()
    val tournamentArenas by viewModel.tournamentArenas.collectAsStateWithLifecycle()
    val tournamentMatches by viewModel.tournamentMatches.collectAsStateWithLifecycle()
    val current1v1Match by viewModel.current1v1Match.collectAsStateWithLifecycle()
    val hintStates by viewModel.hintStates.collectAsStateWithLifecycle()

    var selectedSubTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }
    var activePuzzle by remember { mutableStateOf<BrainPuzzleSpec?>(null) }
    var selectedTournamentGameId by remember { mutableStateOf("ludo_arena") }
    var selectedTier by remember { mutableStateOf(TournamentTierSpec.TIER_5_WIN_8) }
    var showMemoryMatchArena by remember { mutableStateOf(false) }

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

    val allSkillGames = remember {
        listOf(
            BrainPuzzleSpec(
                gameId = "mind_quiz",
                title = "1. Mind Quiz",
                subtitle = "Rapid general knowledge & gaming trivia",
                instructions = "Read the question carefully and select the single accurate option before submitting.",
                difficultyLabel = "Medium",
                prompt = "Which planet in our solar system has the highest number of confirmed moons?",
                options = listOf("Jupiter", "Saturn", "Neptune", "Uranus"),
                correctIndex = 1,
                rewardCoins = 4L,
                hintExplanation = "Eliminate Jupiter and Neptune — this ringed gas giant has over 140 confirmed moons."
            ),
            BrainPuzzleSpec(
                gameId = "logic_challenge",
                title = "2. Logic Challenge",
                subtitle = "Deductive reasoning & syllogisms",
                instructions = "Analyze the logical premises and choose the conclusion that must be true.",
                difficultyLabel = "Hard",
                prompt = "All Emeralds are Gems. Some Gems glow in the dark. Which statement is guaranteed?",
                options = listOf(
                    "All Gems are Emeralds",
                    "Some Emeralds are Gems",
                    "No Emeralds glow",
                    "All glowing items are Emeralds"
                ),
                correctIndex = 1,
                rewardCoins = 5L,
                hintExplanation = "Since ALL Emeralds are Gems, it is guaranteed that at least SOME Emeralds are Gems."
            ),
            BrainPuzzleSpec(
                gameId = "puz_number_grid",
                title = "3. Number Puzzle",
                subtitle = "Complete the 3×3 magic square sum (15)",
                instructions = "Every row, column, and diagonal adds up to 15. Find the missing middle-bottom value.",
                difficultyLabel = "Medium",
                prompt = "Row 1: [8, 1, 6] = 15\nRow 2: [3, 5, 7] = 15\nRow 3: [4, ?, 2] = 15",
                options = listOf("7", "8", "9", "5"),
                correctIndex = 2,
                rewardCoins = 4L,
                hintExplanation = "Subtract 4 and 2 from 15: 15 - (4 + 2) = 9."
            ),
            BrainPuzzleSpec(
                gameId = "puz_pattern_match",
                title = "4. Pattern Recognition",
                subtitle = "Geometric progression sequence",
                instructions = "Identify the multiplier between consecutive numbers to predict the next value.",
                difficultyLabel = "Easy",
                prompt = "Sequence: 3, 6, 12, 24, ?",
                options = listOf("36", "48", "42", "54"),
                correctIndex = 1,
                rewardCoins = 4L,
                hintExplanation = "Each number doubles the previous one: 24 × 2 = 48."
            ),
            BrainPuzzleSpec(
                gameId = "brain_teaser",
                title = "5. Brain Teaser",
                subtitle = "Lateral thinking riddle",
                instructions = "Solve the classic riddle by checking the literal constraint.",
                difficultyLabel = "Medium",
                prompt = "I have cities, but no houses. I have mountains, but no trees. I have water, but no fish. What am I?",
                options = listOf("A Map", "A Mirage", "A Cloud", "A Compass"),
                correctIndex = 0,
                rewardCoins = 4L,
                hintExplanation = "Think of a flat printed guide that labels cities, mountain ranges, and oceans."
            ),
            BrainPuzzleSpec(
                gameId = "timed_trivia",
                title = "6. Timed Trivia",
                subtitle = "Speed calculation sprint",
                instructions = "Compute the arithmetic expression using standard order of operations (BODMAS).",
                difficultyLabel = "Hard",
                prompt = "What is the exact value of: 18 + (6 × 4) - 12 ÷ 3 ?",
                options = listOf("34", "38", "28", "40"),
                correctIndex = 1,
                rewardCoins = 5L,
                hintExplanation = "First compute (6 × 4) = 24 and (12 ÷ 3) = 4. Then 18 + 24 - 4 = 38."
            ),
            BrainPuzzleSpec(
                gameId = "daily_challenge",
                title = "7. Daily Challenge",
                subtitle = "Daily champion code breaker",
                instructions = "Decode the shifted alphabet cipher (+1 forward shift).",
                difficultyLabel = "Hard",
                prompt = "If PANDA is coded as QBOEB, how is GOLD coded?",
                options = listOf("HPME", "FNKC", "HPLD", "GOME"),
                correctIndex = 0,
                rewardCoins = 6L,
                hintExplanation = "Shift each letter of G-O-L-D forward by 1 letter in the alphabet (G->H, O->P, L->M, D->E)."
            ),
            BrainPuzzleSpec(
                gameId = "puz_odd_one_out",
                title = "8. Odd One Out",
                subtitle = "Spot the non-square integer",
                instructions = "Four of these numbers are perfect squares except one.",
                difficultyLabel = "Easy",
                prompt = "Which number does NOT belong: 16, 25, 36, 48, 64?",
                options = listOf("25", "36", "48", "64"),
                correctIndex = 2,
                rewardCoins = 3L,
                hintExplanation = "16=4², 25=5², 36=6², 64=8². Only 48 is not a perfect square."
            ),
            BrainPuzzleSpec(
                gameId = "word_puzzle_anagram",
                title = "9. Word Puzzle",
                subtitle = "Unscramble the 7-letter gaming word",
                instructions = "Rearrange all scrambled letters to form the valid English word.",
                difficultyLabel = "Medium",
                prompt = "Unscramble the letters: D - I - A - M - O - N - D",
                options = listOf("DIAMOND", "DOMINAD", "MONADID", "MANDIOD"),
                correctIndex = 0,
                rewardCoins = 4L,
                hintExplanation = "It starts with D-I-A and names a precious gemstone."
            )
        )
    }

    val pandaPlaysToday = remember(puzzleAttempts) {
        puzzleAttempts.count { it.gameId == "find_the_panda_3x3" }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        // 3-Way Sub-Navigation:
        // 0 = Panda & 8x8 Block (keeps Catch the Panda first for instant visibility)
        // 1 = Play Tournament (Strictly 1v1 Ludo & Carrom Only)
        // 2 = Other Mini-Games (10 Playable Skill & Puzzle Games in Task & Earn)
        item(key = "play_mode_tabs") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PandaWhiteCard, RoundedCornerShape(50))
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(50))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf("Panda & Block", "Play Tournament", "Other Mini-Games")
                tabs.forEachIndexed { idx, title ->
                    val isSelected = selectedSubTab == idx
                    Surface(
                        color = if (isSelected) PandaGreen else Color.Transparent,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedSubTab = idx }
                            .testTag("play_subtab_$idx")
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) Color.White else PandaMuted,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }

        when (selectedSubTab) {
            0 -> {
                // 1) Catch the Panda 3x3 Crate Game (First item so all existing tests find it immediately)
                item(key = "panda_3x3_game") {
                    FindThePandasScreenContent(
                        pandaShowMillis = settings.pandaShowMillis,
                        rewardCoins = settings.pandaRewardCoins,
                        dailyLimit = settings.pandaDailyLimit,
                        playsToday = pandaPlaysToday,
                        onRoundWon = { moves, durationSec, seed ->
                            viewModel.completePuzzleChallenge(
                                gameId = "find_the_panda_3x3",
                                category = "Find the pandas",
                                difficulty = "Hard",
                                score = 300L,
                                movesCount = moves.toLong(),
                                durationSeconds = durationSec.coerceAtLeast(1L),
                                seedToken = seed,
                                baseReward = settings.pandaRewardCoins
                            )
                        }
                    )
                }

                // 2) Fixed 8x8 Panda Block Puzzle with 2-Ad Hint & 10 Levels
                item(key = "panda_block_8x8_game") {
                    val blockHintState = hintStates["panda_block_puzzle_8x8"]
                        ?: HintProgressState(gameId = "panda_block_puzzle_8x8")
                    PandaBlockPuzzleArenaCard(
                        hintState = blockHintState,
                        onRequestHint = { hintClue ->
                            viewModel.requestTwoAdHint(
                                activity = activity,
                                gameId = "panda_block_puzzle_8x8",
                                hintExplanation = hintClue
                            )
                        },
                        onLevelVictory = { level, score, moves ->
                            viewModel.completePuzzleChallenge(
                                gameId = "panda_block_puzzle_8x8",
                                category = "Panda Block Puzzle",
                                difficulty = "Level $level",
                                score = score.toLong(),
                                movesCount = moves.toLong().coerceAtLeast(1L),
                                durationSeconds = 20L,
                                seedToken = "block_lvl_${level}_${UUID.randomUUID().toString().take(6)}",
                                baseReward = 6L
                            )
                        }
                    )
                }
            }

            1 -> {
                // Dedicated Play Tournament Hub — Strictly 1v1 (2 Players Only) Ludo & Carrom
                item(key = "tournament_header_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("play_tournament_hub_banner"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
                        border = BorderStroke(1.dp, PandaGold.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "1V1 TOURNAMENT ARENA",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PandaGold
                                    )
                                    Text(
                                        "Ludo 1v1 • Carrom 1v1 • Strictly 2 Players Per Match",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PandaInk
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = PandaGreen.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, PandaGreen)
                                ) {
                                    Text(
                                        "1v1 • Max 2 Players",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PandaInk,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            Text(
                                "Tiers: 5 Coins Entry → Win 8 Coins • 15 Coins Entry → Win 25 Coins • 20 Coins Entry → Win 35 Coins",
                                style = MaterialTheme.typography.labelSmall,
                                color = PandaMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Strictly 2 Tournament Game Cards: Ludo 1v1 & Carrom 1v1 with Custom Canvas Logos
                item(key = "tournament_game_cards_grid") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        tournamentArenas
                            .filter { it.gameId == "ludo_arena" || it.gameId == "carrom_arena" }
                            .forEach { arena ->
                                val isSelected = selectedTournamentGameId == arena.gameId
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedTournamentGameId = arena.gameId }
                                        .testTag("tournament_card_${arena.gameId}"),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) PandaSoftMintSurface else PandaWhiteCard
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) PandaGreen else PandaCardBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (arena.gameId == "ludo_arena") {
                                                LudoBoardLogoCanvas(modifier = Modifier.size(48.dp))
                                            } else {
                                                CarromBoardLogoCanvas(modifier = Modifier.size(48.dp))
                                            }

                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        arena.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = PandaInk
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = PandaGreen.copy(alpha = 0.18f)
                                                    ) {
                                                        Text(
                                                            "1v1 (2/2)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = PandaGreen,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    arena.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = PandaMuted
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "Entry Tiers: 5→8 • 15→25 • 20→35 Coins",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = PandaGoldDark,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Button(
                                            onClick = { selectedTournamentGameId = arena.gameId },
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) PandaGreen else PandaPurple,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text(
                                                if (isSelected) "Active" else "Select",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }

                // 1v1 Entry Fee & Winner Prize Tier Selector Card (5->8, 15->25, 20->35)
                item(key = "tournament_1v1_tier_selector_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tournament_1v1_tier_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
                        border = BorderStroke(1.dp, PandaCardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Select 1v1 Entry Tier (2 Players Only)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PandaInk
                                )
                                val statusBadge = when {
                                    current1v1Match?.status == "IN_PROGRESS" -> "2/2 Matched"
                                    current1v1Match?.status == "WAITING" -> "1/2 Waiting"
                                    else -> "Ready (Max 2)"
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = PandaSoftMintSurface,
                                    border = BorderStroke(1.dp, PandaGreen.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = statusBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PandaGreen,
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                            .testTag("tournament_1v1_player_status_badge")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TournamentTierSpec.STANDARD_1V1_TIERS.forEach { tier ->
                                    val isChosen = selectedTier.tierId == tier.tierId
                                    Surface(
                                        onClick = { selectedTier = tier },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isChosen) PandaGreen.copy(alpha = 0.16f) else PandaSoftMintSurface,
                                        border = BorderStroke(
                                            width = if (isChosen) 2.dp else 1.dp,
                                            color = if (isChosen) PandaGreen else PandaCardBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("tier_option_${tier.entryCoins}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                "Entry ${tier.entryCoins}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = PandaInk
                                            )
                                            Text(
                                                "Win ${tier.winnerPrizeCoins} Coins",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = PandaGreen
                                            )
                                            Text(
                                                "Pool: ${tier.totalPoolCoins}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PandaMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.start1v1TournamentMatch(
                                            gameId = selectedTournamentGameId,
                                            tier = selectedTier,
                                            autoMatchChallenger = true
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PandaGreen,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("btn_join_1v1_match")
                                ) {
                                    Text(
                                        "Join 1v1 Match (${selectedTier.entryCoins} Coins → Win ${selectedTier.winnerPrizeCoins})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                if (current1v1Match?.status == "WAITING") {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.cancelWaiting1v1TournamentMatch(current1v1Match!!.id)
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .height(46.dp)
                                            .testTag("btn_cancel_1v1_match")
                                    ) {
                                        Text("Cancel & Refund", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Selected Tournament Arena (Strictly Ludo or Carrom)
                item(key = "active_tournament_arena_view") {
                    val activeArena = tournamentArenas.firstOrNull { it.gameId == selectedTournamentGameId }
                        ?: tournamentArenas.first()

                    if (activeArena.gameId == "carrom_arena") {
                        CarromGameArenaCard(
                            arenaSpec = activeArena,
                            activeRoom = current1v1Match,
                            onMatchFinished = { diff, pScore, bScore, rounds ->
                                viewModel.recordTournamentMatch(
                                    tournament = activeArena,
                                    botDifficulty = diff,
                                    playerScore = pScore,
                                    botScore = bScore,
                                    roundsPlayed = rounds,
                                    activity = activity
                                )
                            }
                        )
                    } else {
                        LudoGameArenaCard(
                            arenaSpec = activeArena,
                            activeRoom = current1v1Match,
                            onMatchFinished = { diff, pScore, bScore, rounds ->
                                viewModel.recordTournamentMatch(
                                    tournament = activeArena,
                                    botDifficulty = diff,
                                    playerScore = pScore,
                                    botScore = bScore,
                                    roundsPlayed = rounds,
                                    activity = activity
                                )
                            }
                        )
                    }
                }

                // Non-intrusive Unity Banner Ad Slot in Tournament Hub
                item(key = "tournament_unity_banner_slot") {
                    UnityBannerAdSlot(unityAdManager = viewModel.unityAdManager)
                }

                // Match History Section
                if (tournamentMatches.isNotEmpty()) {
                    item(key = "tournament_match_history_header") {
                        Text(
                            "Recent 1v1 Tournament Match History",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = PandaInk
                        )
                    }
                    items(tournamentMatches.take(8), key = { it.id }) { match ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PandaWhiteCard,
                            border = BorderStroke(1.dp, PandaCardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${match.gameTitle} vs ${match.opponentLabel} (${match.playerCountLabel})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PandaInk
                                    )
                                    Text(
                                        "Score: ${match.playerScore} - ${match.botScore} • Entry: ${match.entryCoinsPaid} Coins • Prize: +${match.winnerPrizeCoins} Coins",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PandaMuted
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (match.resultStatus == "VICTORY") PandaGreen.copy(alpha = 0.2f) else PandaSoftMintSurface
                                ) {
                                    Text(
                                        match.resultStatus,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (match.resultStatus == "VICTORY") PandaGreen else PandaGold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                // Sub-Tab 2: 10 Playable Puzzle & Skill-Based Mini-Games with 2-Ad Hint System
                item(key = "memory_match_feature_card") {
                    val memoryHintState = hintStates["memory_match_4x4"]
                        ?: HintProgressState(gameId = "memory_match_4x4")
                    MemoryMatchMiniGameCard(
                        isExpanded = showMemoryMatchArena,
                        onToggleExpand = { showMemoryMatchArena = !showMemoryMatchArena },
                        hintState = memoryHintState,
                        onRequestHint = { clue ->
                            viewModel.requestTwoAdHint(
                                activity = activity,
                                gameId = "memory_match_4x4",
                                hintExplanation = clue
                            )
                        },
                        onCompleted = { moves, durationSec ->
                            viewModel.completePuzzleChallenge(
                                gameId = "memory_match_4x4",
                                category = "Memory Match",
                                difficulty = "Medium",
                                score = 200L,
                                movesCount = moves.toLong(),
                                durationSeconds = durationSec.coerceAtLeast(3L),
                                seedToken = "mem_${UUID.randomUUID().toString().take(6)}",
                                baseReward = 5L
                            )
                        }
                    )
                }

                item(key = "number_sequence_feature_card") {
                    var showSequenceArena by remember { mutableStateOf(false) }
                    val seqHintState = hintStates["number_sequence"]
                        ?: HintProgressState(gameId = "number_sequence")
                    NumberSequenceGameCard(
                        isExpanded = showSequenceArena,
                        onToggleExpand = { showSequenceArena = !showSequenceArena },
                        hintState = seqHintState,
                        onRequestHint = { ruleClue ->
                            viewModel.requestTwoAdHint(
                                activity = activity,
                                gameId = "number_sequence",
                                hintExplanation = ruleClue
                            )
                        },
                        onCompleted = { qId, reward ->
                            viewModel.completePuzzleChallenge(
                                gameId = "logic_challenge",
                                category = "Number Sequence",
                                difficulty = "Medium",
                                score = 150L,
                                movesCount = 1L,
                                durationSeconds = 5L,
                                seedToken = "seq_${qId}_${UUID.randomUUID().toString().take(4)}",
                                baseReward = reward
                            )
                        }
                    )
                }

                if (activePuzzle != null) {
                    item(key = "active_brain_puzzle") {
                        val puz = activePuzzle!!
                        val puzHintState = hintStates[puz.gameId]
                            ?: HintProgressState(gameId = puz.gameId)
                        ActiveBrainPuzzleCard(
                            puzzle = puz,
                            hintState = puzHintState,
                            onRequestHint = { clue ->
                                viewModel.requestTwoAdHint(
                                    activity = activity,
                                    gameId = puz.gameId,
                                    hintExplanation = clue
                                )
                            },
                            onCancel = { activePuzzle = null },
                            onSolved = { spec, durationSec, seed ->
                                activePuzzle = null
                                viewModel.completePuzzleChallenge(
                                    gameId = spec.gameId,
                                    category = spec.title,
                                    difficulty = spec.difficultyLabel,
                                    score = 120L,
                                    movesCount = 1L,
                                    durationSeconds = durationSec.coerceAtLeast(2L),
                                    seedToken = seed,
                                    baseReward = spec.rewardCoins
                                )
                            }
                        )
                    }
                }

                items(allSkillGames, key = { it.gameId }) { puz ->
                    val enabled = viewModel.isGameEnabled(puz.gameId)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(22.dp))
                            .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp))
                            .clickable(enabled = enabled) { activePuzzle = puz }
                            .testTag("puzzle_card_${puz.gameId}"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = puz.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PandaInk,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = PandaSoftMintSurface
                                    ) {
                                        Text(
                                            text = puz.difficultyLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PandaGold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = puz.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PandaMuted
                                )
                            }

                            Button(
                                onClick = { activePuzzle = puz },
                                enabled = enabled,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PandaGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text(
                                    text = if (enabled) "Win +${puz.rewardCoins}" else "Paused",
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
}

@Composable
private fun MemoryMatchMiniGameCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    hintState: HintProgressState = HintProgressState(gameId = "memory_match_4x4"),
    onRequestHint: (String) -> Unit = {},
    onCompleted: (moves: Int, durationSec: Long) -> Unit
) {
    val symbols = remember { listOf("🐼", "🎋", "👑", "💎", "⚡", "🎯", "🔥", "🏆") }
    var deck by remember { mutableStateOf((symbols + symbols).shuffled()) }
    var flippedIndices by remember { mutableStateOf(listOf<Int>()) }
    var matchedIndices by remember { mutableStateOf(setOf<Int>()) }
    var moves by remember { mutableIntStateOf(0) }
    var startTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val unmatchedPairHint = remember(deck, matchedIndices) {
        val unmatched = deck.indices.filter { it !in matchedIndices }
        val firstIdx = unmatched.firstOrNull()
        if (firstIdx != null) {
            val targetSym = deck[firstIdx]
            val partnerIdx = unmatched.firstOrNull { it != firstIdx && deck[it] == targetSym }
            if (partnerIdx != null) {
                "Matching pair $targetSym is at Tile #${firstIdx + 1} and Tile #${partnerIdx + 1}."
            } else {
                "Flip adjacent tiles in the top row to uncover a pair."
            }
        } else {
            "All pairs matched!"
        }
    }

    LaunchedEffect(flippedIndices) {
        if (flippedIndices.size == 2) {
            val first = flippedIndices[0]
            val second = flippedIndices[1]
            if (deck[first] == deck[second]) {
                matchedIndices = matchedIndices + first + second
                flippedIndices = emptyList()
                if (matchedIndices.size == deck.size) {
                    val elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceAtLeast(2L)
                    onCompleted(moves, elapsed)
                }
            } else {
                delay(650L)
                flippedIndices = emptyList()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_match_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
        border = BorderStroke(1.dp, PandaPurple.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Memory Match (4×4 Pair Grid)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = PandaInk
                    )
                    Text(
                        "Match all 8 pairs • Moves: $moves • Matched: ${matchedIndices.size / 2}/8",
                        style = MaterialTheme.typography.bodySmall,
                        color = PandaMuted
                    )
                }
                Button(
                    onClick = onToggleExpand,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = PandaPurple, contentColor = Color.White)
                ) {
                    Text(if (isExpanded) "Hide" else "Play +5", fontWeight = FontWeight.Bold)
                }
            }

            if (isExpanded) {
                TwoAdHintBanner(
                    hintState = hintState,
                    onRequestHintAd = {
                        onRequestHint(unmatchedPairHint)
                    }
                )

                for (r in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (c in 0 until 4) {
                            val idx = r * 4 + c
                            val isRevealed = idx in flippedIndices || idx in matchedIndices
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clickable(enabled = !isRevealed && flippedIndices.size < 2) {
                                        flippedIndices = flippedIndices + idx
                                        moves += 1
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (idx in matchedIndices) PandaGreen.copy(alpha = 0.25f)
                                else if (isRevealed) PandaWhiteCard
                                else PandaSoftMintSurface,
                                border = BorderStroke(1.dp, if (isRevealed) PandaGreen else PandaCardBorder)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (isRevealed) deck[idx] else "?",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PandaInk
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        deck = (symbols + symbols).shuffled()
                        flippedIndices = emptyList()
                        matchedIndices = emptySet()
                        moves = 0
                        startTimeMs = System.currentTimeMillis()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Restart Grid")
                }
            }
        }
    }
}

@Composable
fun FindThePandasScreenContent(
    pandaShowMillis: Long = 1000L,
    rewardCoins: Long = 5L,
    dailyLimit: Int = 5,
    playsToday: Int = 0,
    onRoundWon: (moves: Int, durationSec: Long, seedToken: String) -> Unit
) {
    var roundNumber by remember { mutableIntStateOf(1) }
    var roundSeed by remember { mutableStateOf("seed_panda_${UUID.randomUUID().toString().take(8)}") }
    var roundState by remember { mutableStateOf(PandaRoundState.MEMORIZING) }
    var roundStartTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val boardSlots = remember(roundNumber, roundSeed) {
        val rng = Random(roundSeed.hashCode())
        listOf(
            CrateSlotType.PANDA, CrateSlotType.PANDA, CrateSlotType.PANDA,
            CrateSlotType.BOMB, CrateSlotType.BOMB,
            CrateSlotType.EMPTY, CrateSlotType.EMPTY, CrateSlotType.EMPTY, CrateSlotType.EMPTY
        ).shuffled(rng)
    }

    var revealedIndices by remember(roundNumber) { mutableStateOf(setOf<Int>()) }
    var pandasFound by remember(roundNumber) { mutableIntStateOf(0) }
    var tapsCount by remember(roundNumber) { mutableIntStateOf(0) }

    LaunchedEffect(roundNumber, pandaShowMillis) {
        roundState = PandaRoundState.MEMORIZING
        revealedIndices = emptySet()
        pandasFound = 0
        tapsCount = 0
        delay(pandaShowMillis.coerceIn(400L, 5000L))
        roundState = PandaRoundState.PLAYING
        roundStartTimeMs = System.currentTimeMillis()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(28.dp))
            .border(1.dp, PandaCardBorder, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Catch the Panda (3×3)",
                    style = MaterialTheme.typography.titleLarge,
                    color = PandaInk,
                    fontWeight = FontWeight.ExtraBold
                )

                Surface(
                    color = PandaSoftMintSurface,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, PandaCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FoundwayCoinIcon(size = 20.dp)
                        Text(
                            text = "+$rewardCoins Coins",
                            style = MaterialTheme.typography.labelLarge,
                            color = PandaGreen,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when (roundState) {
                    PandaRoundState.MEMORIZING -> "Memorize! They hide in 1 second."
                    PandaRoundState.PLAYING -> "Remember where are the pandas, And\ndont tap on the bombs"
                    PandaRoundState.BOMB_HIT -> "Boom! You hit a bomb. Try again."
                    PandaRoundState.WON -> "You found them all!"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (roundState == PandaRoundState.BOMB_HIT) PandaCoral else PandaInk,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("panda_status_subtitle")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = PandaMintBg,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.5.dp, PandaCardBorder),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PandaFaceGraphic(size = 28.dp)
                    Text(
                        text = "Pandas Found $pandasFound / 3",
                        style = MaterialTheme.typography.titleMedium,
                        color = PandaInk,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("panda_found_counter")
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0 until 3) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (col in 0 until 3) {
                            val idx = row * 3 + col
                            val slot = boardSlots[idx]

                            val showPandaDuringMemorize =
                                roundState == PandaRoundState.MEMORIZING && slot == CrateSlotType.PANDA
                            val showBombDuringMemorize =
                                roundState == PandaRoundState.MEMORIZING && slot == CrateSlotType.BOMB
                            val showRevealedSlot =
                                idx in revealedIndices ||
                                    roundState == PandaRoundState.BOMB_HIT ||
                                    roundState == PandaRoundState.WON

                            val isShowingOpenContent =
                                showPandaDuringMemorize || showBombDuringMemorize || showRevealedSlot

                            val tileBg = when {
                                showPandaDuringMemorize || (showRevealedSlot && slot == CrateSlotType.PANDA) ->
                                    PandaSoftMintSurface
                                showBombDuringMemorize || (showRevealedSlot && slot == CrateSlotType.BOMB) ->
                                    PandaCoralContainer
                                showRevealedSlot && slot == CrateSlotType.EMPTY ->
                                    Color(0xFF1E293B)
                                else -> Color.Transparent
                            }

                            Surface(
                                onClick = {
                                    if (roundState == PandaRoundState.PLAYING && idx !in revealedIndices) {
                                        revealedIndices = revealedIndices + idx
                                        tapsCount += 1
                                        when (slot) {
                                            CrateSlotType.PANDA -> {
                                                val newCount = pandasFound + 1
                                                pandasFound = newCount
                                                if (newCount >= 3) {
                                                    roundState = PandaRoundState.WON
                                                    val elapsedSec =
                                                        ((System.currentTimeMillis() - roundStartTimeMs) / 1000L)
                                                            .coerceAtLeast(1L)
                                                    onRoundWon(tapsCount, elapsedSec, roundSeed)
                                                }
                                            }
                                            CrateSlotType.BOMB -> {
                                                roundState = PandaRoundState.BOMB_HIT
                                            }
                                            CrateSlotType.EMPTY -> Unit
                                        }
                                    }
                                },
                                color = tileBg,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .size(96.dp)
                                    .then(
                                        if (isShowingOpenContent) {
                                            Modifier.border(
                                                width = 2.dp,
                                                color = when (slot) {
                                                    CrateSlotType.PANDA -> PandaGreen
                                                    CrateSlotType.BOMB -> PandaCoral
                                                    CrateSlotType.EMPTY -> PandaCardBorder
                                                },
                                                shape = RoundedCornerShape(22.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .testTag("panda_crate_$idx")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    when {
                                        showPandaDuringMemorize || (showRevealedSlot && slot == CrateSlotType.PANDA) -> {
                                            PandaFaceGraphic(size = 64.dp)
                                        }
                                        showBombDuringMemorize || (showRevealedSlot && slot == CrateSlotType.BOMB) -> {
                                            BombIconGraphic(size = 54.dp)
                                        }
                                        showRevealedSlot && slot == CrateSlotType.EMPTY -> {
                                            Text(
                                                text = "Empty",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = PandaMuted,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        else -> {
                                            WoodenCrateGraphic(size = 96.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (roundState == PandaRoundState.BOMB_HIT || roundState == PandaRoundState.WON) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        roundSeed = "seed_panda_${UUID.randomUUID().toString().take(8)}"
                        roundNumber += 1
                    },
                    enabled = playsToday < dailyLimit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PandaGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .width(210.dp)
                        .height(48.dp)
                        .testTag("panda_new_round_button")
                ) {
                    Text(
                        text = if (playsToday >= dailyLimit) "Daily limit reached" else "Play again",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BombIconGraphic(
    size: Dp = 54.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(
            color = Color(0xFF23272E),
            radius = s * 0.34f,
            center = Offset(s * 0.48f, s * 0.56f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.28f),
            radius = s * 0.09f,
            center = Offset(s * 0.38f, s * 0.46f)
        )
        drawCircle(
            color = Color(0xFFFF5A36),
            radius = s * 0.11f,
            center = Offset(s * 0.68f, s * 0.24f)
        )
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = s * 0.06f,
            center = Offset(s * 0.68f, s * 0.24f)
        )
    }
}

@Composable
private fun ActiveBrainPuzzleCard(
    puzzle: BrainPuzzleSpec,
    hintState: HintProgressState = HintProgressState(gameId = puzzle.gameId),
    onRequestHint: (String) -> Unit = {},
    onCancel: () -> Unit,
    onSolved: (BrainPuzzleSpec, Long, String) -> Unit
) {
    val seedToken = remember(puzzle.gameId) { "seed_${UUID.randomUUID().toString().take(8)}" }
    val startMs = remember(puzzle.gameId) { System.currentTimeMillis() }
    var elapsedSeconds by remember(puzzle.gameId) { mutableLongStateOf(0L) }
    var wrongAttemptMessage by remember(puzzle.gameId) { mutableStateOf<String?>(null) }

    val eliminatedWrongIndex = remember(puzzle.gameId, hintState.isUnlocked) {
        if (!hintState.isUnlocked) null
        else puzzle.options.indices.firstOrNull { it != puzzle.correctIndex }
    }

    LaunchedEffect(puzzle.gameId) {
        while (true) {
            delay(1000L)
            elapsedSeconds = (System.currentTimeMillis() - startMs) / 1000L
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .border(1.5.dp, PandaGreen, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = puzzle.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = PandaInk,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${puzzle.difficultyLabel} • ${puzzle.instructions}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PandaMuted
                    )
                }
                Text(
                    text = "${elapsedSeconds}s",
                    style = MaterialTheme.typography.titleMedium,
                    color = PandaGreen,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = puzzle.prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = PandaInk
            )

            Spacer(modifier = Modifier.height(10.dp))

            TwoAdHintBanner(
                hintState = hintState,
                onRequestHintAd = {
                    val clue = puzzle.hintExplanation.ifBlank {
                        "Eliminated one wrong option. Check ${puzzle.options[puzzle.correctIndex]}."
                    }
                    onRequestHint(clue)
                }
            )

            if (wrongAttemptMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = wrongAttemptMessage!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = PandaCoral,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                puzzle.options.forEachIndexed { idx, opt ->
                    val isEliminated = eliminatedWrongIndex == idx
                    Button(
                        onClick = {
                            if (idx == puzzle.correctIndex) {
                                wrongAttemptMessage = null
                                val finalDuration = elapsedSeconds.coerceAtLeast(2L)
                                onSolved(puzzle, finalDuration, seedToken)
                            } else {
                                wrongAttemptMessage = "Not quite right — check the clue and try again!"
                            }
                        },
                        enabled = !isEliminated,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PandaGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("puzzle_option_$idx")
                    ) {
                        Text(
                            text = opt,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Close", color = PandaMuted, fontWeight = FontWeight.Bold)
            }
        }
    }
}
