package com.example.ui.puzzles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.VaultGoldViewModel
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Reusable 2-Consecutive-Rewarded-Ads Hint Banner (`0/2` -> `1/2` -> `2/2`)
 * preserving the original MatchPanda white UI.
 * Never unlocks the hint until 2 full Unity Rewarded Ads (`BP_Rewarded_Android`) complete.
 */
@Composable
fun TwoAdHintBanner(
    hintState: HintProgressState,
    onRequestHintAd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (hintState.isUnlocked) PandaGreen.copy(alpha = 0.14f) else PandaSoftMintSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (hintState.isUnlocked) PandaGreen else PandaCardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("two_ad_hint_banner_${hintState.gameId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (hintState.isUnlocked) Icons.Default.Lightbulb else Icons.Default.OndemandVideo,
                        contentDescription = "Hint Status",
                        tint = if (hintState.isUnlocked) PandaGoldDark else PandaGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = hintState.progressLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = PandaInk,
                            modifier = Modifier.testTag("hint_progress_label_${hintState.gameId}")
                        )
                        Text(
                            text = when {
                                hintState.isUnlocked -> "2/2 Rewarded Ads verified"
                                hintState.adsCompleted == 1 -> "1st ad done! Complete 1 more rewarded ad to unlock hint."
                                else -> "Watch 2 consecutive rewarded video ads to unlock a hint."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = PandaMuted
                        )
                    }
                }

                if (!hintState.isUnlocked) {
                    Button(
                        onClick = onRequestHintAd,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hintState.adsCompleted == 1) PandaGold else PandaGreen,
                            contentColor = if (hintState.adsCompleted == 1) Color.Black else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("btn_watch_2_ads_hint_${hintState.gameId}")
                    ) {
                        Text(
                            text = if (hintState.adsCompleted == 1) {
                                if (hintState.lastError != null) "Retry 2nd Ad (1/2)" else "Watch 2nd Ad (1/2)"
                            } else {
                                "Get Hint (0/2 Ads)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            if (hintState.isUnlocked && !hintState.hintText.isNullOrBlank()) {
                Surface(
                    color = PandaWhiteCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, PandaGreen.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hint_revealed_text_${hintState.gameId}")
                ) {
                    Text(
                        text = "💡 Hint: ${hintState.hintText}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = PandaInk,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            if (!hintState.isUnlocked && !hintState.lastError.isNullOrBlank()) {
                Text(
                    text = hintState.lastError,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("hint_error_text_${hintState.gameId}")
                )
            }
        }
    }
}

@Composable
fun MemoryMatchGameCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCompleted: (moves: Int, durationSec: Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(22.dp))
            .testTag("memory_match_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
        border = BorderStroke(1.dp, PandaCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = PandaSoftMintSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🧠", fontSize = 22.sp)
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Memory Match 4×4",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = PandaInk
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PandaGreen.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    "+5 Coins",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PandaGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "Flip & match 8 cute animal pairs • Train focus",
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaMuted
                        )
                    }
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = PandaInk
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Spacer(Modifier.height(14.dp))
                MemoryMatchBoard(onGameWon = onCompleted)
            }
        }
    }
}

@Composable
private fun MemoryMatchBoard(
    onGameWon: (moves: Int, durationSec: Long) -> Unit
) {
    val emojis = remember {
        listOf("🐼", "🦁", "🦊", "🐨", "🐸", "🐯", "🐵", "🐰")
    }

    var cards by remember {
        mutableStateOf(generateShuffledCards(emojis))
    }
    var flippedIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var movesCount by remember { mutableIntStateOf(0) }
    var isChecking by remember { mutableStateOf(false) }
    var gameWon by remember { mutableStateOf(false) }
    var secondsElapsed by remember { mutableLongStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(true) }

    LaunchedEffect(isTimerRunning, gameWon) {
        while (isTimerRunning && !gameWon) {
            delay(1000L)
            secondsElapsed += 1L
        }
    }

    LaunchedEffect(flippedIndices) {
        if (flippedIndices.size == 2) {
            isChecking = true
            movesCount += 1
            val first = flippedIndices[0]
            val second = flippedIndices[1]
            delay(700L)
            if (cards[first].pairId == cards[second].pairId) {
                cards = cards.mapIndexed { idx, card ->
                    if (idx == first || idx == second) card.copy(isMatched = true) else card
                }
                if (cards.all { it.isMatched }) {
                    gameWon = true
                    isTimerRunning = false
                    onGameWon(movesCount, secondsElapsed)
                }
            } else {
                cards = cards.mapIndexed { idx, card ->
                    if (idx == first || idx == second) card.copy(isFaceUp = false) else card
                }
            }
            flippedIndices = emptyList()
            isChecking = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Moves: $movesCount",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = PandaInk
            )
            Text(
                "Time: ${secondsElapsed}s",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = PandaGold
            )
            Button(
                onClick = {
                    cards = generateShuffledCards(emojis)
                    flippedIndices = emptyList()
                    movesCount = 0
                    gameWon = false
                    secondsElapsed = 0L
                    isTimerRunning = true
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = PandaSoftMintSurface, contentColor = PandaGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Restart", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(cards.indices.toList()) { index ->
                val card = cards[index]
                val rotation by animateFloatAsState(
                    targetValue = if (card.isFaceUp || card.isMatched) 180f else 0f,
                    label = "card_flip"
                )

                Surface(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .rotate(if (rotation > 90f) 180f else 0f)
                        .clickable(enabled = !card.isFaceUp && !card.isMatched && !isChecking && !gameWon) {
                            cards = cards.mapIndexed { idx, c ->
                                if (idx == index) c.copy(isFaceUp = true) else c
                            }
                            flippedIndices = flippedIndices + index
                        }
                        .testTag("memory_card_$index"),
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        card.isMatched -> PandaGreen.copy(alpha = 0.2f)
                        card.isFaceUp -> PandaWhiteCard
                        else -> PandaSoftMintSurface
                    },
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = when {
                            card.isMatched -> PandaGreen
                            card.isFaceUp -> PandaCardBorder
                            else -> PandaMintBg
                        }
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (card.isFaceUp || card.isMatched) {
                            Text(card.emoji, fontSize = 28.sp)
                        } else {
                            Text("🐾", fontSize = 20.sp, color = PandaMuted)
                        }
                    }
                }
            }
        }

        if (gameWon) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = PandaGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🎉", fontSize = 24.sp)
                    Column {
                        Text(
                            "Puzzle Solved in $movesCount moves (${secondsElapsed}s)!",
                            fontWeight = FontWeight.ExtraBold,
                            color = PandaGreen,
                            fontSize = 14.sp
                        )
                        Text(
                            "Watch ad to claim +5 Coins in your Claim Overlay!",
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaInk
                        )
                    }
                }
            }
        }
    }
}

private fun generateShuffledCards(emojis: List<String>): List<MemoryCard> {
    val list = mutableListOf<MemoryCard>()
    var idCounter = 0
    emojis.forEachIndexed { pairIndex, emoji ->
        list.add(MemoryCard(id = idCounter++, pairId = pairIndex, emoji = emoji, label = emoji))
        list.add(MemoryCard(id = idCounter++, pairId = pairIndex, emoji = emoji, label = emoji))
    }
    return list.shuffled()
}

@Composable
fun NumberSequenceGameCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    hintState: HintProgressState = HintProgressState(gameId = "number_sequence"),
    onRequestHint: (String) -> Unit = {},
    onCompleted: (questionId: String, rewardCoins: Long) -> Unit
) {
    val questions = remember {
        listOf(
            NumberSequenceQuestion(
                id = "seq_1",
                prompt = "What number comes next in this arithmetic progression?",
                sequenceDisplay = "2,  5,  8,  11,  ?",
                options = listOf("13", "14", "15", "16"),
                correctIndex = 1,
                ruleExplanation = "Rule: +3 each step (11 + 3 = 14)",
                rewardCoins = 4L
            ),
            NumberSequenceQuestion(
                id = "seq_2",
                prompt = "Identify the missing square power in the series:",
                sequenceDisplay = "1,  4,  9,  16,  25,  ?",
                options = listOf("30", "34", "36", "49"),
                correctIndex = 2,
                ruleExplanation = "Rule: n² sequence (6² = 36)",
                rewardCoins = 4L
            ),
            NumberSequenceQuestion(
                id = "seq_3",
                prompt = "Find the next Fibonacci number in this sequence:",
                sequenceDisplay = "1,  1,  2,  3,  5,  8,  ?",
                options = listOf("11", "12", "13", "15"),
                correctIndex = 2,
                ruleExplanation = "Rule: Each term is sum of previous two (5 + 8 = 13)",
                rewardCoins = 4L
            ),
            NumberSequenceQuestion(
                id = "seq_4",
                prompt = "Determine the missing geometric multiple:",
                sequenceDisplay = "3,  6,  12,  24,  ?",
                options = listOf("36", "42", "48", "54"),
                correctIndex = 2,
                ruleExplanation = "Rule: ×2 each step (24 × 2 = 48)",
                rewardCoins = 4L
            )
        )
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var isSubmitted by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }

    val currentQ = questions[currentIndex]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(22.dp))
            .testTag("number_sequence_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
        border = BorderStroke(1.dp, PandaCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = PandaSoftMintSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🔢", fontSize = 22.sp)
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Number Sequence Challenge",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = PandaInk
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PandaGreen.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    "+4 Coins",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PandaGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "Logic puzzles & mathematical progression trivia",
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaMuted
                        )
                    }
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = PandaInk
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Text(
                        "Question ${currentIndex + 1} of ${questions.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PandaMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        currentQ.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PandaInk
                    )
                    Spacer(Modifier.height(10.dp))

                    Surface(
                        color = PandaSoftMintSurface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = currentQ.sequenceDisplay,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = PandaInk,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    TwoAdHintBanner(
                        hintState = hintState,
                        onRequestHintAd = {
                            onRequestHint(currentQ.ruleExplanation)
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        currentQ.options.forEachIndexed { optIndex, optText ->
                            val isChosen = selectedOptionIndex == optIndex
                            val bg = when {
                                isSubmitted && optIndex == currentQ.correctIndex -> PandaGreen.copy(alpha = 0.2f)
                                isSubmitted && isChosen && optIndex != currentQ.correctIndex -> Color(0xFFFFEBEE)
                                isChosen -> PandaSoftMintSurface
                                else -> PandaWhiteCard
                            }
                            val borderCol = when {
                                isSubmitted && optIndex == currentQ.correctIndex -> PandaGreen
                                isSubmitted && isChosen && optIndex != currentQ.correctIndex -> Color(0xFFD32F2F)
                                isChosen -> PandaGreen
                                else -> PandaCardBorder
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isSubmitted) { selectedOptionIndex = optIndex }
                                    .testTag("seq_opt_$optIndex"),
                                shape = RoundedCornerShape(12.dp),
                                color = bg,
                                border = BorderStroke(1.5.dp, borderCol)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = optText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = PandaInk
                                    )
                                    if (isSubmitted) {
                                        if (optIndex == currentQ.correctIndex) {
                                            Icon(Icons.Default.CheckCircle, "Correct", tint = PandaGreen)
                                        } else if (isChosen) {
                                            Icon(Icons.Default.Cancel, "Incorrect", tint = Color(0xFFD32F2F))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isSubmitted) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = currentQ.ruleExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCorrect) PandaGreen else PandaGold,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isSubmitted) {
                            Button(
                                onClick = {
                                    if (selectedOptionIndex != null) {
                                        isSubmitted = true
                                        isCorrect = selectedOptionIndex == currentQ.correctIndex
                                        if (isCorrect) {
                                            onCompleted(currentQ.id, currentQ.rewardCoins)
                                        }
                                    }
                                },
                                enabled = selectedOptionIndex != null,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White)
                            ) {
                                Text("Check Answer", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    currentIndex = (currentIndex + 1) % questions.size
                                    selectedOptionIndex = null
                                    isSubmitted = false
                                    isCorrect = false
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White)
                            ) {
                                Text("Next Challenge", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// GAME A: PANDA MEMORY MATCH (3 DIFFICULTIES, EMOJIS, TIMER & PERSONAL BEST)
// ============================================================================

@Composable
fun PandaMemoryMatchScreen(
    viewModel: VaultGoldViewModel,
    onBack: () -> Unit
) {
    var selectedDifficulty by remember { mutableStateOf(com.example.model.MemoryDifficulty.MEDIUM) }
    var sessionSeed by remember { mutableIntStateOf(0) }

    val personalBests by viewModel.miniGamePersonalBests.collectAsStateWithLifecycle()
    val pb = remember(personalBests, selectedDifficulty) {
        personalBests.firstOrNull { it.gameType == "MEMORY_MATCH" && it.difficulty == selectedDifficulty.id }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(16.dp)
            .testTag("panda_memory_match_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onBack,
                    color = PandaWhiteCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PandaCardBorder),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PandaInk)
                    }
                }
                Column {
                    Text(
                        text = "Panda Memory Match",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = PandaInk
                    )
                    Text(
                        text = "Flip & match cute panda pairs",
                        style = MaterialTheme.typography.bodySmall,
                        color = PandaMuted
                    )
                }
            }

            // Personal best chip
            if (pb != null && pb.gamesPlayed > 0) {
                Surface(
                    color = PandaSoftMintSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PandaGreenBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = "Best", tint = PandaGoldDark, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Best: ${pb.bestMoves} moves",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PandaInk
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Difficulty selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.example.model.MemoryDifficulty.entries.forEach { diff ->
                val isSelected = selectedDifficulty == diff
                Surface(
                    onClick = {
                        if (selectedDifficulty != diff) {
                            selectedDifficulty = diff
                            sessionSeed++
                        }
                    },
                    color = if (isSelected) PandaGreen else PandaWhiteCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isSelected) PandaGreen else PandaCardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("memory_diff_${diff.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = diff.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else PandaInk
                        )
                        Text(
                            text = "+${diff.baseRewardCoins} Coins",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) Color.White.copy(alpha = 0.9f) else PandaGreen
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        key(selectedDifficulty, sessionSeed) {
            PandaMemoryPlayArea(
                difficulty = selectedDifficulty,
                onRestart = { sessionSeed++ },
                onGameComplete = { moves, durationSec ->
                    viewModel.completeMiniGameSession(
                        gameType = "MEMORY_MATCH",
                        difficulty = selectedDifficulty.id,
                        score = maxOf(10, 100 - (moves * 3)),
                        movesCount = moves,
                        durationSeconds = durationSec.toInt()
                    )
                },
                onBack = onBack
            )
        }
    }
}

@Composable
private fun PandaMemoryPlayArea(
    difficulty: com.example.model.MemoryDifficulty,
    onRestart: () -> Unit,
    onGameComplete: (moves: Int, durationSec: Long) -> Unit,
    onBack: () -> Unit
) {
    val pool = remember {
        listOf("🐼", "🎋", "👑", "🍇", "💎", "⭐", "🍀", "🍉", "🎯", "🔥", "🚀", "🌸")
    }

    val cards = remember(difficulty) {
        val selected = pool.shuffled().take(difficulty.pairsCount)
        val deck = (selected + selected).shuffled().mapIndexed { idx, symbol ->
            MemoryCard(
                id = idx,
                pairId = symbol.hashCode(),
                emoji = symbol,
                label = symbol,
                isFaceUp = false,
                isMatched = false
            )
        }
        mutableStateListOf<MemoryCard>().apply { addAll(deck) }
    }

    var flippedIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var movesCount by remember { mutableIntStateOf(0) }
    var isChecking by remember { mutableStateOf(false) }
    var gameWon by remember { mutableStateOf(false) }
    var secondsElapsed by remember { mutableLongStateOf(0L) }
    var timerActive by remember { mutableStateOf(true) }

    LaunchedEffect(timerActive, gameWon) {
        while (timerActive && !gameWon) {
            delay(1000L)
            secondsElapsed++
        }
    }

    fun handleCardClick(idx: Int) {
        if (isChecking || gameWon || cards[idx].isFaceUp || cards[idx].isMatched) return

        cards[idx] = cards[idx].copy(isFaceUp = true)
        val newFlipped = flippedIndices + idx
        flippedIndices = newFlipped

        if (newFlipped.size == 2) {
            movesCount++
            isChecking = true
            val first = cards[newFlipped[0]]
            val second = cards[newFlipped[1]]
            if (first.pairId == second.pairId) {
                cards[newFlipped[0]] = first.copy(isMatched = true)
                cards[newFlipped[1]] = second.copy(isMatched = true)
                flippedIndices = emptyList()
                isChecking = false

                if (cards.all { it.isMatched }) {
                    gameWon = true
                    timerActive = false
                    onGameComplete(movesCount, secondsElapsed)
                }
            }
        }
    }

    LaunchedEffect(flippedIndices) {
        if (flippedIndices.size == 2 && isChecking) {
            val first = cards[flippedIndices[0]]
            val second = cards[flippedIndices[1]]
            if (first.pairId != second.pairId) {
                delay(850L)
                cards[flippedIndices[0]] = first.copy(isFaceUp = false)
                cards[flippedIndices[1]] = second.copy(isFaceUp = false)
                flippedIndices = emptyList()
                isChecking = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status Bar (Moves, Timer, Pairs)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = PandaWhiteCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PandaCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Moves: $movesCount", fontWeight = FontWeight.Bold, color = PandaInk, fontSize = 13.sp)
                    Text("⏱ ${secondsElapsed}s", fontWeight = FontWeight.Bold, color = PandaInk, fontSize = 13.sp)
                    val matchedPairs = cards.count { it.isMatched } / 2
                    Text("Pairs: $matchedPairs/${difficulty.pairsCount}", fontWeight = FontWeight.Bold, color = PandaGreen, fontSize = 13.sp)
                }
            }

            IconButton(
                onClick = onRestart,
                modifier = Modifier
                    .size(36.dp)
                    .background(PandaWhiteCard, RoundedCornerShape(10.dp))
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = PandaInk, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(difficulty.cols),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cards.size) { idx ->
                val card = cards[idx]
                Surface(
                    onClick = { handleCardClick(idx) },
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        card.isMatched -> PandaGreen.copy(alpha = 0.22f)
                        card.isFaceUp -> PandaSoftMintSurface
                        else -> PandaWhiteCard
                    },
                    border = BorderStroke(
                        1.5.dp,
                        when {
                            card.isMatched -> PandaGreen
                            card.isFaceUp -> PandaPurple
                            else -> PandaCardBorder
                        }
                    ),
                    shadowElevation = if (card.isFaceUp || card.isMatched) 1.dp else 3.dp,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .testTag("memory_card_$idx")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (card.isFaceUp || card.isMatched) {
                            Text(card.emoji, fontSize = 28.sp)
                        } else {
                            PandaFaceGraphic(size = 28.dp)
                        }
                    }
                }
            }
        }

        // Win Dialog / Overlay
        if (gameWon) {
            Surface(
                color = PandaWhiteCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, PandaGreen),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉 Booyah! Level Cleared!", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = PandaGreen)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Completed in $movesCount moves and ${secondsElapsed}s! (+${difficulty.baseRewardCoins} Coins)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PandaInk,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Back to Games")
                        }
                        Button(
                            onClick = onRestart,
                            colors = ButtonDefaults.buttonColors(containerColor = PandaGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Play Again")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// GAME B: PANDA BUBBLE POP (FLOOD-FILL CLUSTER MATCH & DROP PHYSICS)
// ============================================================================

@Composable
fun PandaBubblePopScreen(
    viewModel: VaultGoldViewModel,
    onBack: () -> Unit
) {
    val bubbleColors = remember {
        listOf(
            Color(0xFF22C55E), // Green
            Color(0xFF7C3AED), // Purple
            Color(0xFFF59E0B), // Gold
            Color(0xFF3B82F6), // Blue
            Color(0xFFEF4444)  // Coral
        )
    }

    val bubbleNames = remember { listOf("Green", "Purple", "Gold", "Blue", "Coral") }

    val rows = 8
    val cols = 7

    var grid by remember {
        mutableStateOf(
            List(rows) { r ->
                List(cols) { c ->
                    if (r < 5) (0 until bubbleColors.size).random() else -1
                }
            }
        )
    }

    var currentShooterColor by remember { mutableIntStateOf((0 until bubbleColors.size).random()) }
    var shotsRemaining by remember { mutableIntStateOf(20) }
    var score by remember { mutableIntStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var gameWon by remember { mutableStateOf(false) }

    fun floodFillCluster(startR: Int, startC: Int, targetColor: Int): Set<Pair<Int, Int>> {
        if (targetColor == -1) return emptySet()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startR, startC))
        visited.add(Pair(startR, startC))

        val neighbors = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))

        while (queue.isNotEmpty()) {
            val (currR, currC) = queue.removeFirst()
            for ((dr, dc) in neighbors) {
                val nr = currR + dr
                val nc = currC + dc
                if (nr in 0 until rows && nc in 0 until cols && Pair(nr, nc) !in visited) {
                    if (grid[nr][nc] == targetColor) {
                        visited.add(Pair(nr, nc))
                        queue.add(Pair(nr, nc))
                    }
                }
            }
        }
        return visited
    }

    fun removeFloatingClusters(currentGrid: List<MutableList<Int>>) {
        // BFS from top row (r = 0) to find all anchored bubbles
        val anchored = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        for (c in 0 until cols) {
            if (currentGrid[0][c] != -1) {
                anchored.add(Pair(0, c))
                queue.add(Pair(0, c))
            }
        }
        val neighbors = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))
        while (queue.isNotEmpty()) {
            val (cr, cc) = queue.removeFirst()
            for ((dr, dc) in neighbors) {
                val nr = cr + dr
                val nc = cc + dc
                if (nr in 0 until rows && nc in 0 until cols && Pair(nr, nc) !in anchored) {
                    if (currentGrid[nr][nc] != -1) {
                        anchored.add(Pair(nr, nc))
                        queue.add(Pair(nr, nc))
                    }
                }
            }
        }
        // Any bubble not anchored drops
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (currentGrid[r][c] != -1 && Pair(r, c) !in anchored) {
                    currentGrid[r][c] = -1
                    score += 15
                }
            }
        }
    }

    fun shootAtColumn(col: Int) {
        if (gameOver || gameWon || shotsRemaining <= 0) return

        // Find the lowest empty cell in this column
        var targetRow = -1
        for (r in rows - 1 downTo 0) {
            if (grid[r][col] == -1) {
                targetRow = r
                break
            }
        }
        if (targetRow == -1) return

        val newGrid = grid.map { it.toMutableList() }
        newGrid[targetRow][col] = currentShooterColor
        shotsRemaining--

        // Check matching cluster
        val cluster = floodFillCluster(targetRow, col, currentShooterColor)
        if (cluster.size >= 3) {
            cluster.forEach { (r, c) ->
                newGrid[r][c] = -1
            }
            score += cluster.size * 20
            removeFloatingClusters(newGrid)
        }

        grid = newGrid
        currentShooterColor = (0 until bubbleColors.size).random()

        // Check win or loss
        val bubblesRemaining = newGrid.flatten().count { it != -1 }
        val reachedBottom = newGrid[rows - 1].any { it != -1 }

        if (bubblesRemaining == 0) {
            gameWon = true
            viewModel.completeMiniGameSession(
                gameType = "BUBBLE_POP",
                difficulty = "MEDIUM",
                score = score + 100,
                movesCount = 20 - shotsRemaining,
                durationSeconds = 45
            )
        } else if (reachedBottom || shotsRemaining <= 0) {
            gameOver = true
            viewModel.completeMiniGameSession(
                gameType = "BUBBLE_POP",
                difficulty = "MEDIUM",
                score = score,
                movesCount = 20 - shotsRemaining,
                durationSeconds = 45
            )
        }
    }

    fun restartGame() {
        grid = List(rows) { r ->
            List(cols) { c ->
                if (r < 5) (0 until bubbleColors.size).random() else -1
            }
        }
        currentShooterColor = (0 until bubbleColors.size).random()
        shotsRemaining = 20
        score = 0
        gameOver = false
        gameWon = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(16.dp)
            .testTag("panda_bubble_pop_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onBack,
                    color = PandaWhiteCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PandaCardBorder),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PandaInk)
                    }
                }
                Column {
                    Text(
                        text = "Panda Bubble Pop",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = PandaInk
                    )
                    Text(
                        text = "Aim & match 3+ bubbles to pop!",
                        style = MaterialTheme.typography.bodySmall,
                        color = PandaMuted
                    )
                }
            }

            Surface(
                color = PandaSoftMintSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PandaGreenBorder)
            ) {
                Text(
                    text = "Score: $score",
                    fontWeight = FontWeight.ExtraBold,
                    color = PandaGreenDark,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Bubble Board
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Bubbles Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (c in 0 until cols) {
                                val colorIdx = grid[r][c]
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (colorIdx != -1) bubbleColors[colorIdx] else Color.Transparent
                                        )
                                        .border(
                                            width = if (colorIdx != -1) 1.dp else 0.dp,
                                            color = Color.White.copy(alpha = 0.35f),
                                            shape = CircleShape
                                        )
                                        .clickable { shootAtColumn(c) }
                                )
                            }
                        }
                    }
                }

                // Danger Line & Shooter Column Buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color(0xFFEF4444).copy(alpha = 0.6f))
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (c in 0 until cols) {
                            IconButton(
                                onClick = { shootAtColumn(c) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Shoot col $c", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Bottom Shooter Controls
        Surface(
            color = PandaWhiteCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, PandaCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(bubbleColors[currentShooterColor])
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                    Column {
                        Text("Current Bubble", style = MaterialTheme.typography.labelSmall, color = PandaMuted)
                        Text(bubbleNames[currentShooterColor], fontWeight = FontWeight.ExtraBold, color = PandaInk)
                    }
                }

                Text(
                    text = "Shots Left: $shotsRemaining",
                    fontWeight = FontWeight.Bold,
                    color = if (shotsRemaining <= 5) Color(0xFFEF4444) else PandaInk
                )

                IconButton(onClick = { restartGame() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = PandaInk)
                }
            }
        }

        // Game Over / Win Banner
        if (gameOver || gameWon) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = PandaWhiteCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, if (gameWon) PandaGreen else PandaPurple),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (gameWon) "🏆 Board Cleared!" else "Match Finished",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (gameWon) PandaGreen else PandaInk,
                        fontSize = 16.sp
                    )
                    Text("Final Score: $score", fontWeight = FontWeight.Bold, color = PandaMuted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBack) { Text("Back") }
                        Button(
                            onClick = { restartGame() },
                            colors = ButtonDefaults.buttonColors(containerColor = PandaGreen)
                        ) {
                            Text("Play Again")
                        }
                    }
                }
            }
        }
    }
}
