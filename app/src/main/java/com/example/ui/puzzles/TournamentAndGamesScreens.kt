package com.example.ui.puzzles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BotDifficulty
import com.example.model.HintProgressState
import com.example.model.OneVsOneMatchRoom
import com.example.model.TournamentArenaSpec
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom Canvas Vector Logo for Ludo 1v1 Tournament Card & Arena Header.
 * Draws the 4 classic quadrant home bases (Emerald, Gold, Coral, Royal Purple) and center star.
 */
@Composable
fun LudoBoardLogoCanvas(
    modifier: Modifier = Modifier.size(48.dp)
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PandaWhiteCard,
        border = BorderStroke(1.5.dp, PandaGreen.copy(alpha = 0.55f)),
        modifier = modifier.testTag("ludo_board_logo_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        ) {
            val w = size.width
            val h = size.height
            val quadW = w * 0.40f
            val quadH = h * 0.40f

            // Top-Left: Emerald Home Base
            drawRect(
                color = PandaGreen,
                topLeft = Offset(0f, 0f),
                size = Size(quadW, quadH)
            )
            // Top-Right: Gold Home Base
            drawRect(
                color = PandaGold,
                topLeft = Offset(w - quadW, 0f),
                size = Size(quadW, quadH)
            )
            // Bottom-Left: Coral Home Base
            drawRect(
                color = Color(0xFFEF4444),
                topLeft = Offset(0f, h - quadH),
                size = Size(quadW, quadH)
            )
            // Bottom-Right: Purple Home Base
            drawRect(
                color = PandaPurple,
                topLeft = Offset(w - quadW, h - quadH),
                size = Size(quadW, quadH)
            )
            // Center Star / Home Hub
            drawCircle(
                color = PandaGoldDark,
                radius = minOf(w, h) * 0.14f,
                center = Offset(w * 0.5f, h * 0.5f)
            )
        }
    }
}

/**
 * Custom Canvas Vector Logo for Carrom 1v1 Tournament Card & Arena Header.
 * Draws the classic wooden Carrom board frame, 4 corner pockets, Red Queen center, and Striker.
 */
@Composable
fun CarromBoardLogoCanvas(
    modifier: Modifier = Modifier.size(48.dp)
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFF8E7),
        border = BorderStroke(2.dp, Color(0xFFD97706)),
        modifier = modifier.testTag("carrom_board_logo_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        ) {
            val w = size.width
            val h = size.height
            val pocketR = minOf(w, h) * 0.12f

            // 4 Corner Pockets
            listOf(
                Offset(pocketR, pocketR),
                Offset(w - pocketR, pocketR),
                Offset(pocketR, h - pocketR),
                Offset(w - pocketR, h - pocketR)
            ).forEach { corner ->
                drawCircle(color = Color(0xFF334155), radius = pocketR, center = corner)
            }

            // Center Ring + Red Queen
            drawCircle(
                color = Color(0xFFD97706),
                radius = minOf(w, h) * 0.24f,
                center = Offset(w * 0.5f, h * 0.46f),
                style = Stroke(width = 2.5f)
            )
            drawCircle(
                color = Color(0xFFEF4444),
                radius = minOf(w, h) * 0.11f,
                center = Offset(w * 0.5f, h * 0.46f)
            )

            // Striker on baseline
            drawCircle(
                color = PandaGreen,
                radius = minOf(w, h) * 0.10f,
                center = Offset(w * 0.5f, h * 0.82f)
            )
        }
    }
}

// ============================================================================
// 1. FAIR LUDO ENGINE & ARENA (100% UNIFORM 1..6 DICE FOR PLAYER & BOT)
// ============================================================================

const val LUDO_BASE_POS = -1
const val LUDO_HOME_POS = 28
const val LUDO_MAIN_TRACK_SIZE = 24
val LUDO_SAFE_SQUARES = setOf(0, 6, 12, 18)

data class LudoMoveOption(
    val tokenIndex: Int,
    val fromStep: Int,
    val toStep: Int,
    val exitsBase: Boolean,
    val reachesHome: Boolean,
    val capturesOpponentIndices: List<Int>,
    val landsOnSafeSquare: Boolean
)

/**
 * Strictly fair, unmanipulated 1..6 dice roll (16.67% probability per face).
 * Used identically for both Human Player and Bot Opponent.
 */
fun rollFairLudoDice(random: Random = Random.Default): Int {
    return random.nextInt(1, 7)
}

/**
 * Converts a player's relative step (0..23) into the shared 24-square board index.
 * Player 0 starts at board square 0; Bot (Player 1) starts at board square 12.
 */
fun toSharedBoardSquare(relativeStep: Int, isBot: Boolean): Int? {
    if (relativeStep !in 0 until LUDO_MAIN_TRACK_SIZE) return null
    val offset = if (isBot) 12 else 0
    return (relativeStep + offset) % LUDO_MAIN_TRACK_SIZE
}

fun computeLegalLudoMoves(
    ownTokens: List<Int>,
    opponentTokens: List<Int>,
    diceValue: Int,
    isBot: Boolean
): List<LudoMoveOption> {
    if (diceValue !in 1..6) return emptyList()
    val moves = mutableListOf<LudoMoveOption>()

    ownTokens.forEachIndexed { idx, step ->
        if (step == LUDO_BASE_POS) {
            if (diceValue == 6) {
                val targetShared = toSharedBoardSquare(0, isBot)
                val captured = if (targetShared != null && targetShared !in LUDO_SAFE_SQUARES) {
                    opponentTokens.mapIndexedNotNull { oppIdx, oppStep ->
                        if (toSharedBoardSquare(oppStep, !isBot) == targetShared) oppIdx else null
                    }
                } else emptyList()
                moves.add(
                    LudoMoveOption(
                        tokenIndex = idx,
                        fromStep = step,
                        toStep = 0,
                        exitsBase = true,
                        reachesHome = false,
                        capturesOpponentIndices = captured,
                        landsOnSafeSquare = true
                    )
                )
            }
        } else if (step < LUDO_HOME_POS) {
            val targetStep = step + diceValue
            if (targetStep <= LUDO_HOME_POS) {
                val targetShared = toSharedBoardSquare(targetStep, isBot)
                val isSafe = targetShared != null && targetShared in LUDO_SAFE_SQUARES
                val captured = if (targetShared != null && !isSafe) {
                    opponentTokens.mapIndexedNotNull { oppIdx, oppStep ->
                        if (toSharedBoardSquare(oppStep, !isBot) == targetShared) oppIdx else null
                    }
                } else emptyList()
                moves.add(
                    LudoMoveOption(
                        tokenIndex = idx,
                        fromStep = step,
                        toStep = targetStep,
                        exitsBase = false,
                        reachesHome = targetStep == LUDO_HOME_POS,
                        capturesOpponentIndices = captured,
                        landsOnSafeSquare = isSafe
                    )
                )
            }
        }
    }
    return moves
}

/**
 * Legal strategic move selection for Bot difficulties.
 * Does NOT alter dice rolls — uses only legal moves available for the rolled dice value.
 */
fun chooseStrategicBotLudoMove(
    legalMoves: List<LudoMoveOption>,
    difficulty: BotDifficulty,
    random: Random = Random.Default
): LudoMoveOption? {
    if (legalMoves.isEmpty()) return null
    return when (difficulty) {
        BotDifficulty.BEGINNER -> legalMoves.first()
        BotDifficulty.INTERMEDIATE -> {
            legalMoves.firstOrNull { it.capturesOpponentIndices.isNotEmpty() || it.reachesHome }
                ?: legalMoves.random(random)
        }
        BotDifficulty.ADVANCED -> {
            legalMoves.maxByOrNull { move ->
                var weight = move.toStep * 2
                if (move.capturesOpponentIndices.isNotEmpty()) weight += 120
                if (move.reachesHome) weight += 100
                if (move.exitsBase) weight += 65
                if (move.landsOnSafeSquare) weight += 45
                weight
            }
        }
    }
}

@Composable
fun LudoGameArenaCard(
    arenaSpec: TournamentArenaSpec,
    activeRoom: OneVsOneMatchRoom? = null,
    onMatchFinished: (BotDifficulty, Int, Int, Int) -> Unit
) {
    var botDifficulty by remember { mutableStateOf(BotDifficulty.INTERMEDIATE) }
    var showRulesDialog by remember { mutableStateOf(false) }

    var playerTokens by remember { mutableStateOf(listOf(0, -1, -1, -1)) }
    var botTokens by remember { mutableStateOf(listOf(0, -1, -1, -1)) }
    var playerScore by remember { mutableIntStateOf(0) }
    var botScore by remember { mutableIntStateOf(0) }
    var turnsTaken by remember { mutableIntStateOf(0) }
    val maxTurns = 15

    var lastPlayerDice by remember { mutableIntStateOf(1) }
    var lastBotDice by remember { mutableIntStateOf(1) }
    var pendingPlayerDice by remember { mutableStateOf<Int?>(null) }
    var statusBanner by remember {
        mutableStateOf("Your turn! Roll the fair 1–6 dice to advance or exit base on a 6.")
    }
    var matchFinished by remember { mutableStateOf(false) }

    fun resetLudoMatch() {
        playerTokens = listOf(0, -1, -1, -1)
        botTokens = listOf(0, -1, -1, -1)
        playerScore = 0
        botScore = 0
        turnsTaken = 0
        pendingPlayerDice = null
        matchFinished = false
        statusBanner = "New Ludo match started vs ${botDifficulty.label} [BOT]. Fair 1–6 dice active."
    }

    fun executeBotTurn() {
        if (matchFinished) return
        val botRoll = rollFairLudoDice()
        lastBotDice = botRoll
        val legalBotMoves = computeLegalLudoMoves(botTokens, playerTokens, botRoll, isBot = true)
        val chosen = chooseStrategicBotLudoMove(legalBotMoves, botDifficulty)

        if (chosen != null) {
            val updatedBot = botTokens.toMutableList()
            updatedBot[chosen.tokenIndex] = chosen.toStep
            botTokens = updatedBot

            var deltaScore = botRoll
            if (chosen.exitsBase) deltaScore += 10
            if (chosen.reachesHome) deltaScore += 50
            if (chosen.capturesOpponentIndices.isNotEmpty()) {
                val updatedPlayer = playerTokens.toMutableList()
                chosen.capturesOpponentIndices.forEach { capIdx ->
                    updatedPlayer[capIdx] = LUDO_BASE_POS
                }
                playerTokens = updatedPlayer
                deltaScore += 20 * chosen.capturesOpponentIndices.size
            }
            botScore += deltaScore
            statusBanner = buildString {
                append("${botDifficulty.label} rolled $botRoll and moved Token #${chosen.tokenIndex + 1}")
                if (chosen.capturesOpponentIndices.isNotEmpty()) append(" (Captured your token!)")
                if (chosen.reachesHome) append(" (Reached HOME!)")
                append(". Your turn!")
            }
        } else {
            statusBanner = "${botDifficulty.label} rolled $botRoll (no legal move). Your turn!"
        }

        turnsTaken += 1
        val allPlayerHome = playerTokens.all { it == LUDO_HOME_POS }
        val allBotHome = botTokens.all { it == LUDO_HOME_POS }
        if (allPlayerHome || allBotHome || turnsTaken >= maxTurns) {
            matchFinished = true
            onMatchFinished(botDifficulty, playerScore, botScore, turnsTaken)
        }
    }

    fun applyPlayerMove(move: LudoMoveOption, diceVal: Int) {
        val updatedPlayer = playerTokens.toMutableList()
        updatedPlayer[move.tokenIndex] = move.toStep
        playerTokens = updatedPlayer

        var deltaScore = diceVal
        if (move.exitsBase) deltaScore += 10
        if (move.reachesHome) deltaScore += 50
        if (move.capturesOpponentIndices.isNotEmpty()) {
            val updatedBot = botTokens.toMutableList()
            move.capturesOpponentIndices.forEach { capIdx ->
                updatedBot[capIdx] = LUDO_BASE_POS
            }
            botTokens = updatedBot
            deltaScore += 20 * move.capturesOpponentIndices.size
        }
        playerScore += deltaScore
        pendingPlayerDice = null

        if (playerTokens.all { it == LUDO_HOME_POS }) {
            matchFinished = true
            turnsTaken += 1
            statusBanner = "All 4 tokens reached HOME! You win!"
            onMatchFinished(botDifficulty, playerScore, botScore, turnsTaken)
            return
        }

        if (diceVal == 6 || move.capturesOpponentIndices.isNotEmpty()) {
            statusBanner = "Bonus Roll! You rolled $diceVal${if (move.capturesOpponentIndices.isNotEmpty()) " & captured a bot token" else ""} — roll again!"
        } else {
            executeBotTurn()
        }
    }

    val currentLegalMoves = remember(playerTokens, botTokens, pendingPlayerDice) {
        val dice = pendingPlayerDice ?: return@remember emptyList()
        computeLegalLudoMoves(playerTokens, botTokens, dice, isBot = false)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ludo_arena_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
        border = BorderStroke(1.dp, PandaGreen.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    LudoBoardLogoCanvas(modifier = Modifier.size(44.dp))
                    Column {
                        Text(
                            arenaSpec.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = PandaInk
                        )
                        Text(
                            "1v1 (2 Players Max) • Fair 1–6 RNG • Round ${turnsTaken.coerceAtMost(maxTurns)}/$maxTurns",
                            style = MaterialTheme.typography.labelSmall,
                            color = PandaMuted
                        )
                    }
                }
                IconButton(
                    onClick = { showRulesDialog = true },
                    modifier = Modifier.testTag("ludo_rules_button")
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Ludo Rules", tint = PandaGold)
                }
            }

            // Bot Difficulty & Entry Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BotDifficulty.entries.forEach { diff ->
                    val selected = botDifficulty == diff
                    FilterChip(
                        selected = selected,
                        onClick = {
                            botDifficulty = diff
                            resetLudoMatch()
                        },
                        label = {
                            Text(
                                diff.label.replace(" Bot", ""),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PandaPurple.copy(alpha = 0.25f),
                            selectedLabelColor = PandaInk
                        )
                    )
                }
            }

            // Match Info Banner (Live 1v1 Room or Free Practice)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PandaSoftMintSurface,
                border = BorderStroke(1.dp, PandaCardStroke)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (activeRoom != null && activeRoom.gameId == arenaSpec.gameId) {
                            Text(
                                "1v1 Tournament Match (${activeRoom.playerCount}/2 Players) • Entry: ${activeRoom.entryCoins} Coins • Winner Prize: ${activeRoom.winnerPrizeCoins} Coins",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = PandaGreen
                            )
                            Text(
                                "${activeRoom.player1Name} vs ${(activeRoom.player2Name ?: "").ifBlank { "Waiting for Challenger (1/2)" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaInk
                            )
                        } else {
                            Text(
                                "1v1 Practice Match (2/2) • Win +${arenaSpec.practiceRewardCoins} Task Coins",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = PandaGreen
                            )
                            Text(
                                "Opponent: ${botDifficulty.label} [1v1] • Fair Dice Verified",
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaMuted
                            )
                        }
                    }
                }
            }

            // Live Scoreboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = PandaGreen.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, PandaGreen.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("YOU (EMERALD)", style = MaterialTheme.typography.labelSmall, color = PandaGreen, fontWeight = FontWeight.Bold)
                        Text("$playerScore pts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = PandaInk)
                        Text("Dice: $lastPlayerDice", style = MaterialTheme.typography.labelSmall, color = PandaMuted)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = PandaPurple.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, PandaPurple.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${botDifficulty.label.uppercase()} [BOT]", style = MaterialTheme.typography.labelSmall, color = PandaPurple, fontWeight = FontWeight.Bold)
                        Text("$botScore pts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = PandaInk)
                        Text("Dice: $lastBotDice", style = MaterialTheme.typography.labelSmall, color = PandaMuted)
                    }
                }
            }

            // Visual 24-Square Ludo Track + Token Progress
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PandaSoftMintSurface,
                border = BorderStroke(1.dp, PandaCardStroke)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Ludo Star Track (Safe ★ at 0, 6, 12, 18 • Home at 28)",
                        style = MaterialTheme.typography.labelSmall,
                        color = PandaMuted,
                        fontWeight = FontWeight.SemiBold
                    )

                    // 2 rows of 12 squares representing the 24 shared track squares
                    for (row in 0..1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (col in 0..11) {
                                val sq = if (row == 0) col else (23 - col)
                                val hasPlayer = playerTokens.any { toSharedBoardSquare(it, false) == sq }
                                val hasBot = botTokens.any { toSharedBoardSquare(it, true) == sq }
                                val isSafe = sq in LUDO_SAFE_SQUARES

                                val bg = when {
                                    hasPlayer && hasBot -> PandaGold
                                    hasPlayer -> PandaGreen
                                    hasBot -> PandaPurple
                                    isSafe -> Color(0xFFFFF8E5)
                                    else -> PandaWhiteCard
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(bg)
                                        .border(
                                            1.dp,
                                            if (isSafe) PandaGold.copy(alpha = 0.6f) else PandaCardStroke,
                                            RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when {
                                            hasPlayer -> "P"
                                            hasBot -> "B"
                                            isSafe -> "★"
                                            else -> "$sq"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasPlayer || hasBot) Color.White else PandaInk
                                    )
                                }
                            }
                        }
                    }

                    // Player 4 Tokens Interactive Status Row
                    Text(
                        "Your 4 Tokens (Tap a highlighted token after rolling):",
                        style = MaterialTheme.typography.labelSmall,
                        color = PandaInk,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        playerTokens.forEachIndexed { idx, pos ->
                            val moveForToken = currentLegalMoves.firstOrNull { it.tokenIndex == idx }
                            val isMovable = moveForToken != null && !matchFinished
                            val posText = when (pos) {
                                LUDO_BASE_POS -> "BASE"
                                LUDO_HOME_POS -> "HOME"
                                in 24..27 -> "H-${28 - pos}"
                                else -> "Sq $pos"
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ludo_token_$idx")
                                    .clickable(enabled = isMovable) {
                                        if (moveForToken != null && pendingPlayerDice != null) {
                                            applyPlayerMove(moveForToken, pendingPlayerDice!!)
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isMovable) PandaGold.copy(alpha = 0.25f) else PandaSoftMintSurface,
                                border = BorderStroke(
                                    width = if (isMovable) 2.dp else 1.dp,
                                    color = if (isMovable) PandaGold else PandaCardStroke
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "T#${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isMovable) PandaGold else PandaInk
                                    )
                                    Text(
                                        posText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (pos == LUDO_HOME_POS) PandaGreen else PandaMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Status Banner
            Text(
                text = statusBanner,
                style = MaterialTheme.typography.bodySmall,
                color = PandaInk,
                fontWeight = FontWeight.Medium
            )

            // Action Buttons (Roll Fair Dice / Move Token / Restart)
            if (!matchFinished) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val rolled = rollFairLudoDice()
                            lastPlayerDice = rolled
                            val legal = computeLegalLudoMoves(playerTokens, botTokens, rolled, isBot = false)
                            if (legal.isEmpty()) {
                                statusBanner = "You rolled $rolled — no legal moves available. Passing turn to Bot..."
                                executeBotTurn()
                            } else if (legal.size == 1) {
                                applyPlayerMove(legal.first(), rolled)
                            } else {
                                pendingPlayerDice = rolled
                                statusBanner = "You rolled $rolled! Tap one of your highlighted tokens (T#1–T#4) to move."
                            }
                        },
                        enabled = pendingPlayerDice == null,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ludo_roll_dice_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PandaGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (pendingPlayerDice != null) "Select Token (Rolled $pendingPlayerDice)" else "Roll Fair Dice (1–6)",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    OutlinedButton(
                        onClick = { resetLudoMatch() },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("ludo_restart_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart Ludo")
                    }
                }
            } else {
                // Match Result Summary
                val won = playerScore > botScore
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (won) PandaGreen.copy(alpha = 0.16f) else PandaGold.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, if (won) PandaGreen else PandaGold)
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
                                if (won) "VICTORY! ($playerScore vs $botScore)" else "MATCH COMPLETE ($playerScore vs $botScore)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (won) PandaGreen else PandaGoldDark
                            )
                            Text(
                                if (won) "Reward claim unlocked! Play again anytime." else "Good match! Try another round.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaInk
                            )
                        }
                        Button(
                            onClick = { resetLudoMatch() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White)
                        ) {
                            Text("Play Again", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showRulesDialog) {
        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            title = { Text("${arenaSpec.title} — Official Rules & Fair RNG", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    arenaSpec.rulesSummary.forEach { rule ->
                        Text("• $rule", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Fair Play Guarantee: Both Player and Bot use strictly uniform 1–6 random dice (16.67% per face). Bot difficulty affects only strategic token selection.",
                        style = MaterialTheme.typography.labelSmall,
                        color = PandaGreen
                    )
                    Text(
                        arenaSpec.complianceNotice,
                        style = MaterialTheme.typography.labelSmall,
                        color = PandaMuted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showRulesDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}

// ============================================================================
// 2. PLAYABLE CARROM STRIKER ARENA (PHYSICS SHOT, QUEEN COVER, FOUL RULES)
// ============================================================================

data class CarromPuck(
    val id: Int,
    val type: String, // "WHITE" (20), "BLACK" (10), "QUEEN" (50)
    val x: Float,     // Normalized 0.1f..0.9f
    val y: Float,     // Normalized 0.1f..0.9f
    val isPocketed: Boolean = false
)

data class CarromShotOutcome(
    val updatedPucks: List<CarromPuck>,
    val pocketedWhite: Int,
    val pocketedBlack: Int,
    val pocketedQueen: Boolean,
    val strikerFoul: Boolean,
    val pointsDelta: Int,
    val summary: String
)

fun defaultCarromPucks(): List<CarromPuck> = listOf(
    CarromPuck(id = 1, type = "QUEEN", x = 0.50f, y = 0.45f),
    CarromPuck(id = 2, type = "WHITE", x = 0.42f, y = 0.38f),
    CarromPuck(id = 3, type = "WHITE", x = 0.58f, y = 0.38f),
    CarromPuck(id = 4, type = "WHITE", x = 0.50f, y = 0.32f),
    CarromPuck(id = 5, type = "BLACK", x = 0.36f, y = 0.46f),
    CarromPuck(id = 6, type = "BLACK", x = 0.64f, y = 0.46f),
    CarromPuck(id = 7, type = "BLACK", x = 0.45f, y = 0.52f),
    CarromPuck(id = 8, type = "BLACK", x = 0.55f, y = 0.52f)
)

fun simulateCarromShot(
    pucks: List<CarromPuck>,
    strikerX: Float,
    aimAngleDeg: Float,
    powerPercent: Float
): CarromShotOutcome {
    val rad = Math.toRadians(aimAngleDeg.toDouble())
    val dirX = sin(rad).toFloat()
    val dirY = -cos(rad).toFloat()
    val strikerStartY = 0.82f
    val reach = (powerPercent / 100f).coerceIn(0.2f, 1.0f) * 0.95f

    var pocketWhite = 0
    var pocketBlack = 0
    var pocketQueen = false

    val corners = listOf(
        0.08f to 0.08f,
        0.92f to 0.08f,
        0.08f to 0.92f,
        0.92f to 0.92f
    )

    val nextPucks = pucks.map { puck ->
        if (puck.isPocketed) return@map puck
        // Ray distance from striker line to puck center
        val dx = puck.x - strikerX
        val dy = puck.y - strikerStartY
        val proj = dx * dirX + dy * dirY
        if (proj > 0f && proj <= reach) {
            val closestX = strikerX + dirX * proj
            val closestY = strikerStartY + dirY * proj
            val perpDist = hypot((puck.x - closestX).toDouble(), (puck.y - closestY).toDouble()).toFloat()
            if (perpDist <= 0.13f) {
                val impulse = (reach - proj + 0.28f).coerceAtLeast(0.22f)
                val newX = (puck.x + dirX * impulse).coerceIn(0.06f, 0.94f)
                val newY = (puck.y + dirY * impulse).coerceIn(0.06f, 0.94f)
                val nearCorner = corners.any { (cx, cy) ->
                    hypot((newX - cx).toDouble(), (newY - cy).toDouble()) <= 0.16
                } || (powerPercent >= 65f && perpDist <= 0.07f)

                if (nearCorner) {
                    when (puck.type) {
                        "WHITE" -> pocketWhite += 1
                        "BLACK" -> pocketBlack += 1
                        "QUEEN" -> pocketQueen = true
                    }
                    puck.copy(x = newX, y = newY, isPocketed = true)
                } else {
                    puck.copy(x = newX, y = newY)
                }
            } else puck
        } else puck
    }

    // Striker foul occurs if shot directly into corner at extreme angle & max power without hitting pucks
    val strikerEndX = (strikerX + dirX * reach).coerceIn(0.05f, 0.95f)
    val strikerEndY = (strikerStartY + dirY * reach).coerceIn(0.05f, 0.95f)
    val strikerFoul = (pocketWhite == 0 && pocketBlack == 0 && !pocketQueen) &&
        powerPercent >= 92f &&
        corners.any { (cx, cy) -> hypot((strikerEndX - cx).toDouble(), (strikerEndY - cy).toDouble()) <= 0.11 }

    var delta = pocketWhite * 20 + pocketBlack * 10 + (if (pocketQueen) 50 else 0)
    if (strikerFoul) delta -= 10

    val desc = buildString {
        if (pocketQueen) append("Pocketed RED QUEEN (+50)! ")
        if (pocketWhite > 0) append("Pocketed $pocketWhite White (+${pocketWhite * 20})! ")
        if (pocketBlack > 0) append("Pocketed $pocketBlack Black (+${pocketBlack * 10})! ")
        if (strikerFoul) append("Striker pocket foul (-10 pts).")
        if (isEmpty()) append("Clean strike — pucks repositioned on board.")
    }

    return CarromShotOutcome(
        updatedPucks = nextPucks,
        pocketedWhite = pocketWhite,
        pocketedBlack = pocketBlack,
        pocketedQueen = pocketQueen,
        strikerFoul = strikerFoul,
        pointsDelta = delta,
        summary = desc.trim()
    )
}

@Composable
fun CarromGameArenaCard(
    arenaSpec: TournamentArenaSpec,
    activeRoom: OneVsOneMatchRoom? = null,
    onMatchFinished: (BotDifficulty, Int, Int, Int) -> Unit
) {
    var botDifficulty by remember { mutableStateOf(BotDifficulty.INTERMEDIATE) }
    var pucks by remember { mutableStateOf(defaultCarromPucks()) }
    var strikerX by remember { mutableFloatStateOf(0.50f) }
    var aimAngleDeg by remember { mutableFloatStateOf(0f) }
    var shotPower by remember { mutableFloatStateOf(75f) }

    var playerScore by remember { mutableIntStateOf(0) }
    var botScore by remember { mutableIntStateOf(0) }
    var shotsTaken by remember { mutableIntStateOf(0) }
    val maxShots = 8
    var statusText by remember {
        mutableStateOf("Aim your striker along the baseline and tap Strike! Red Queen = 50 pts.")
    }
    var matchDone by remember { mutableStateOf(false) }

    fun resetCarrom() {
        pucks = defaultCarromPucks()
        strikerX = 0.50f
        aimAngleDeg = 0f
        shotPower = 75f
        playerScore = 0
        botScore = 0
        shotsTaken = 0
        matchDone = false
        statusText = "Fresh Carrom board racked vs ${botDifficulty.label} [1v1]."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("carrom_arena_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
        border = BorderStroke(1.dp, PandaGold.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CarromBoardLogoCanvas(modifier = Modifier.size(44.dp))
                    Column {
                        Text(
                            arenaSpec.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = PandaInk
                        )
                        Text(
                            "1v1 (2 Players Max) • White: 20 • Black: 10 • Queen: 50 • Shot ${shotsTaken.coerceAtMost(maxShots)}/$maxShots",
                            style = MaterialTheme.typography.labelSmall,
                            color = PandaMuted
                        )
                    }
                }
                OutlinedButton(
                    onClick = { resetCarrom() },
                    modifier = Modifier.testTag("carrom_restart_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Reset", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (activeRoom != null && activeRoom.gameId == arenaSpec.gameId) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PandaSoftMintSurface,
                    border = BorderStroke(1.dp, PandaCardStroke)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "1v1 Tournament Match (${activeRoom.playerCount}/2 Players) • Entry: ${activeRoom.entryCoins} Coins • Winner Prize: ${activeRoom.winnerPrizeCoins} Coins",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PandaGreen
                        )
                        Text(
                            "${activeRoom.player1Name} vs ${(activeRoom.player2Name ?: "").ifBlank { "Waiting for Challenger (1/2)" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaInk
                        )
                    }
                }
            }

            // Scoreboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = PandaGreen.copy(alpha = 0.14f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("YOUR SCORE", style = MaterialTheme.typography.labelSmall, color = PandaGreen, fontWeight = FontWeight.Bold)
                        Text("$playerScore pts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = PandaInk)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = PandaPurple.copy(alpha = 0.14f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${botDifficulty.label.uppercase()} [BOT]", style = MaterialTheme.typography.labelSmall, color = PandaPurple, fontWeight = FontWeight.Bold)
                        Text("$botScore pts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = PandaInk)
                    }
                }
            }

            // Interactive Carrom Board Canvas (Light Warm Classic Board)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFFFF8E7))
                    .border(3.dp, Color(0xFFD97706), RoundedCornerShape(18.dp))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Board surface (warm cream wood)
                    drawRect(color = Color(0xFFFFF8E7), size = Size(w, h))

                    // Center ring
                    drawCircle(
                        color = Color(0xFFD97706).copy(alpha = 0.45f),
                        radius = minOf(w, h) * 0.18f,
                        center = Offset(w * 0.5f, h * 0.45f),
                        style = Stroke(width = 3f)
                    )

                    // 4 Corner pockets
                    val pocketRadius = minOf(w, h) * 0.075f
                    val corners = listOf(
                        Offset(w * 0.08f, h * 0.08f),
                        Offset(w * 0.92f, h * 0.08f),
                        Offset(w * 0.08f, h * 0.92f),
                        Offset(w * 0.92f, h * 0.92f)
                    )
                    corners.forEach { c ->
                        drawCircle(color = Color(0xFF475569), radius = pocketRadius, center = c)
                        drawCircle(
                            color = Color(0xFFD97706),
                            radius = pocketRadius,
                            center = c,
                            style = Stroke(width = 2f)
                        )
                    }

                    // Striker baseline
                    drawLine(
                        color = PandaGreen.copy(alpha = 0.6f),
                        start = Offset(w * 0.2f, h * 0.82f),
                        end = Offset(w * 0.8f, h * 0.82f),
                        strokeWidth = 3f
                    )

                    // Aim guide line
                    val rad = Math.toRadians(aimAngleDeg.toDouble())
                    val sx = w * strikerX
                    val sy = h * 0.82f
                    val guideLen = minOf(w, h) * 0.42f * (shotPower / 100f)
                    val gx = sx + sin(rad).toFloat() * guideLen
                    val gy = sy - cos(rad).toFloat() * guideLen
                    drawLine(
                        color = PandaGreen,
                        start = Offset(sx, sy),
                        end = Offset(gx, gy),
                        strokeWidth = 4f
                    )

                    // Active Carrommen Pucks
                    pucks.filter { !it.isPocketed }.forEach { puck ->
                        val puckColor = when (puck.type) {
                            "QUEEN" -> Color(0xFFEF4444)
                            "WHITE" -> Color(0xFFF59E0B)
                            else -> Color(0xFF334155)
                        }
                        drawCircle(
                            color = puckColor,
                            radius = minOf(w, h) * 0.048f,
                            center = Offset(w * puck.x, h * puck.y)
                        )
                    }

                    // Striker disc
                    drawCircle(
                        color = PandaGold,
                        radius = minOf(w, h) * 0.06f,
                        center = Offset(sx, sy)
                    )
                }
            }

            // Controls: Striker Position, Aim Angle, Power
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Striker Position", style = MaterialTheme.typography.labelSmall, color = PandaMuted)
                    Text("Aim: ${aimAngleDeg.toInt()}° • Power: ${shotPower.toInt()}%", style = MaterialTheme.typography.labelSmall, color = PandaGold)
                }
                Slider(
                    value = strikerX,
                    onValueChange = { strikerX = it },
                    valueRange = 0.22f..0.78f,
                    modifier = Modifier.height(28.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aim Angle", style = MaterialTheme.typography.labelSmall, color = PandaMuted)
                        Slider(
                            value = aimAngleDeg,
                            onValueChange = { aimAngleDeg = it },
                            valueRange = -65f..65f,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Shot Power", style = MaterialTheme.typography.labelSmall, color = PandaMuted)
                        Slider(
                            value = shotPower,
                            onValueChange = { shotPower = it },
                            valueRange = 30f..100f,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            Text(statusText, style = MaterialTheme.typography.bodySmall, color = PandaInk)

            Button(
                onClick = {
                    val outcome = simulateCarromShot(pucks, strikerX, aimAngleDeg, shotPower)
                    pucks = outcome.updatedPucks
                    playerScore = (playerScore + outcome.pointsDelta).coerceAtLeast(0)
                    shotsTaken += 1

                    // Bot counter-shot on remaining pucks
                    val remaining = pucks.filter { !it.isPocketed }
                    val botDelta = if (remaining.isNotEmpty()) {
                        val target = remaining.first()
                        val botHit = when (botDifficulty) {
                            BotDifficulty.BEGINNER -> 10
                            BotDifficulty.INTERMEDIATE -> if (target.type == "WHITE") 20 else 10
                            BotDifficulty.ADVANCED -> if (target.type == "QUEEN") 30 else 20
                        }
                        botHit
                    } else 0
                    botScore += botDelta

                    statusText = "${outcome.summary} | Bot scored +$botDelta pts."
                    if (pucks.all { it.isPocketed } || shotsTaken >= maxShots) {
                        matchDone = true
                        onMatchFinished(botDifficulty, playerScore, botScore, shotsTaken)
                    }
                },
                enabled = !matchDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("carrom_strike_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PandaGold, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (matchDone) "Match Complete ($playerScore - $botScore)" else "Strike Carrom Shot",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// ============================================================================
// 3. FIXED 8x8 PANDA BLOCK PUZZLE (ZERO DEBUG ARTIFACTS, MULTI-LEVEL, COMBOS)
// ============================================================================

data class BlockPieceShape(
    val id: String,
    val name: String,
    val offsets: List<Pair<Int, Int>>, // (rowOffset, colOffset)
    val cellType: Int                  // 1 = Bamboo, 2 = Gold, 3 = Panda, 4 = Royal Gem
)

data class BlockPlacementResult(
    val newGrid: List<Int>,
    val rowsCleared: Int,
    val colsCleared: Int,
    val pointsEarned: Int
)

val PANDA_BLOCK_SHAPES = listOf(
    BlockPieceShape("panda_1x1", "Panda Tile (1×1)", listOf(0 to 0), 3),
    BlockPieceShape("bamboo_1x2", "Bamboo Pair (1×2)", listOf(0 to 0, 0 to 1), 1),
    BlockPieceShape("bamboo_2x1", "Bamboo Pillar (2×1)", listOf(0 to 0, 1 to 0), 1),
    BlockPieceShape("gold_2x2", "Gold Block (2×2)", listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1), 2),
    BlockPieceShape("bamboo_1x3", "Bamboo Beam (1×3)", listOf(0 to 0, 0 to 1, 0 to 2), 1),
    BlockPieceShape("gem_corner", "Royal L-Block", listOf(0 to 0, 1 to 0, 1 to 1), 4)
)

fun canPlaceBlockShape(
    grid: List<Int>,
    shape: BlockPieceShape,
    anchorRow: Int,
    anchorCol: Int,
    gridSize: Int = 8
): Boolean {
    if (grid.size != gridSize * gridSize) return false
    for ((dr, dc) in shape.offsets) {
        val r = anchorRow + dr
        val c = anchorCol + dc
        if (r !in 0 until gridSize || c !in 0 until gridSize) return false
        if (grid[r * gridSize + c] != 0) return false
    }
    return true
}

fun placeShapeAndClearLines(
    grid: List<Int>,
    shape: BlockPieceShape,
    anchorRow: Int,
    anchorCol: Int,
    gridSize: Int = 8
): BlockPlacementResult? {
    if (!canPlaceBlockShape(grid, shape, anchorRow, anchorCol, gridSize)) return null
    val mutable = grid.toMutableList()
    for ((dr, dc) in shape.offsets) {
        val r = anchorRow + dr
        val c = anchorCol + dc
        mutable[r * gridSize + c] = shape.cellType
    }

    val fullRows = (0 until gridSize).filter { r ->
        (0 until gridSize).all { c -> mutable[r * gridSize + c] != 0 }
    }
    val fullCols = (0 until gridSize).filter { c ->
        (0 until gridSize).all { r -> mutable[r * gridSize + c] != 0 }
    }

    fullRows.forEach { r ->
        for (c in 0 until gridSize) {
            mutable[r * gridSize + c] = 0
        }
    }
    fullCols.forEach { c ->
        for (r in 0 until gridSize) {
            mutable[r * gridSize + c] = 0
        }
    }

    val linesCleared = fullRows.size + fullCols.size
    val basePoints = shape.offsets.size * 10
    val lineBonus = if (linesCleared > 0) linesCleared * 100 * linesCleared else 0

    return BlockPlacementResult(
        newGrid = mutable,
        rowsCleared = fullRows.size,
        colsCleared = fullCols.size,
        pointsEarned = basePoints + lineBonus
    )
}

fun findFirstValidBlockPlacement(
    grid: List<Int>,
    shape: BlockPieceShape,
    gridSize: Int = 8
): Pair<Int, Int>? {
    for (r in 0 until gridSize) {
        for (c in 0 until gridSize) {
            if (canPlaceBlockShape(grid, shape, r, c, gridSize)) {
                return r to c
            }
        }
    }
    return null
}

@Composable
fun PandaBlockPuzzleArenaCard(
    hintState: HintProgressState = HintProgressState(gameId = "panda_block_puzzle_8x8"),
    onRequestHint: (String) -> Unit = {},
    onLevelVictory: (level: Int, score: Int, moves: Int) -> Unit
) {
    val gridSize = 8
    var grid by remember { mutableStateOf(List(gridSize * gridSize) { 0 }) }
    var currentLevel by remember { mutableIntStateOf(1) }
    var currentScore by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(0) }
    var movesCount by remember { mutableIntStateOf(0) }
    var selectedShapeIdx by remember { mutableIntStateOf(0) }
    var trayShapes by remember {
        mutableStateOf(listOf(PANDA_BLOCK_SHAPES[0], PANDA_BLOCK_SHAPES[1], PANDA_BLOCK_SHAPES[3]))
    }
    var feedbackBanner by remember {
        mutableStateOf("Select a block piece below and tap any valid square on the 8×8 grid.")
    }
    var showInstructions by remember { mutableStateOf(false) }
    var levelTargetScore by remember(currentLevel) { mutableIntStateOf(currentLevel * 180) }

    val activeShape = trayShapes.getOrNull(selectedShapeIdx) ?: trayShapes.first()
    val validAnchor = remember(grid, activeShape) {
        findFirstValidBlockPlacement(grid, activeShape, gridSize)
    }
    val hintedCellCoordinates = remember(validAnchor, activeShape, hintState.isUnlocked) {
        if (!hintState.isUnlocked || validAnchor == null) {
            emptySet()
        } else {
            val (ar, ac) = validAnchor
            activeShape.offsets.map { (dr, dc) -> (ar + dr) to (ac + dc) }.toSet()
        }
    }

    fun resetBoard(keepLevel: Boolean = true) {
        grid = List(gridSize * gridSize) { 0 }
        if (!keepLevel) {
            currentLevel = 1
            currentScore = 0
        }
        movesCount = 0
        trayShapes = PANDA_BLOCK_SHAPES.shuffled().take(3)
        selectedShapeIdx = 0
        feedbackBanner = "Clean 8×8 Panda Block board ready! Target: $levelTargetScore pts."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("panda_block_puzzle_8x8_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
        border = BorderStroke(1.dp, PandaGreen.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Panda Block Puzzle (8×8)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = PandaInk
                    )
                    Text(
                        "Level $currentLevel / 10 • Target: $levelTargetScore pts • Best: $bestScore",
                        style = MaterialTheme.typography.labelSmall,
                        color = PandaGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showInstructions = true }) {
                        Icon(Icons.Default.Info, contentDescription = "How to play", tint = PandaGold)
                    }
                    IconButton(
                        onClick = { resetBoard(keepLevel = true) },
                        modifier = Modifier.testTag("panda_block_restart_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart board", tint = PandaInk)
                    }
                }
            }

            // 10-Level Selector Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (lvl in 1..5) {
                    val isCurrent = currentLevel == lvl
                    Surface(
                        onClick = {
                            currentLevel = lvl
                            levelTargetScore = lvl * 180
                            resetBoard(keepLevel = true)
                        },
                        shape = RoundedCornerShape(50),
                        color = if (isCurrent) PandaGreen else PandaSoftMintSurface,
                        border = BorderStroke(1.dp, if (isCurrent) PandaGreen else PandaCardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("panda_block_lvl_$lvl")
                    ) {
                        Text(
                            text = "Lv.$lvl",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isCurrent) Color.White else PandaInk,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // Score Progress Bar
            LinearProgressIndicator(
                progress = { (currentScore.toFloat() / levelTargetScore.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = PandaGreen,
                trackColor = PandaSoftMintSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Score: $currentScore pts", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = PandaGold)
                Text("Moves: $movesCount", style = MaterialTheme.typography.labelMedium, color = PandaMuted)
            }

            // 2-Consecutive-Rewarded-Ads Hint Banner
            TwoAdHintBanner(
                hintState = hintState,
                onRequestHintAd = {
                    val clue = if (validAnchor != null) {
                        "Place ${activeShape.name} at Row ${validAnchor.first + 1}, Column ${validAnchor.second + 1} (highlighted in Gold)."
                    } else {
                        "Tap another block shape in your tray to find an open 8×8 placement."
                    }
                    onRequestHint(clue)
                }
            )

            // Clean 8x8 Grid (Zero Debug Text / Zero 'O' or 'EMP' Artifacts, Light White UI)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PandaSoftMintSurface,
                border = BorderStroke(1.5.dp, PandaCardStroke)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (r in 0 until gridSize) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (c in 0 until gridSize) {
                                val cellIndex = r * gridSize + c
                                val cellValue = grid[cellIndex]
                                val isHintCell = (r to c) in hintedCellCoordinates && cellValue == 0

                                val cellColor = when {
                                    cellValue == 1 -> PandaGreen
                                    cellValue == 2 -> PandaGold
                                    cellValue == 3 -> Color(0xFF38BDF8)
                                    cellValue == 4 -> PandaPurple
                                    isHintCell -> PandaGold.copy(alpha = 0.28f)
                                    else -> PandaWhiteCard
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(cellColor)
                                        .border(
                                            width = if (isHintCell) 2.dp else 1.dp,
                                            color = when {
                                                isHintCell -> PandaGoldDark
                                                cellValue != 0 -> Color.White.copy(alpha = 0.45f)
                                                else -> PandaCardStroke
                                            },
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .testTag("block_cell_${r}_${c}")
                                        .clickable {
                                            val result = placeShapeAndClearLines(grid, activeShape, r, c, gridSize)
                                            if (result != null) {
                                                grid = result.newGrid
                                                movesCount += 1
                                                currentScore += result.pointsEarned
                                                if (currentScore > bestScore) bestScore = currentScore

                                                // Refill placed shape
                                                val updatedTray = trayShapes.toMutableList()
                                                updatedTray[selectedShapeIdx] = PANDA_BLOCK_SHAPES.random()
                                                trayShapes = updatedTray

                                                val clearedTotal = result.rowsCleared + result.colsCleared
                                                feedbackBanner = if (clearedTotal > 0) {
                                                    "COMBO! Cleared $clearedTotal line(s) for +${result.pointsEarned} pts!"
                                                } else {
                                                    "Placed ${activeShape.name} (+${result.pointsEarned} pts)."
                                                }

                                                if (currentScore >= levelTargetScore) {
                                                    val completedLevel = currentLevel
                                                    onLevelVictory(completedLevel, currentScore, movesCount)
                                                    currentLevel = (currentLevel + 1).coerceAtMost(10)
                                                    levelTargetScore = currentLevel * 180
                                                    feedbackBanner = "LEVEL $completedLevel COMPLETE! Advanced to Level $currentLevel!"
                                                }
                                            } else {
                                                feedbackBanner = "Cannot place ${activeShape.name} at Row ${r + 1}, Col ${c + 1} — choose an open spot."
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cellValue != 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.35f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selectable Block Piece Tray
            Text(
                "Available Block Shapes (Tap to select, then tap board):",
                style = MaterialTheme.typography.labelSmall,
                color = PandaMuted,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trayShapes.forEachIndexed { idx, piece ->
                    val isSelected = idx == selectedShapeIdx
                    val badgeColor = when (piece.cellType) {
                        1 -> PandaGreen
                        2 -> PandaGold
                        3 -> Color(0xFF38BDF8)
                        else -> PandaPurple
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("block_tray_piece_$idx")
                            .clickable { selectedShapeIdx = idx },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) badgeColor.copy(alpha = 0.22f) else PandaSoftMintSurface,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) badgeColor else PandaCardStroke
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                piece.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PandaInk,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            Text(
                                "${piece.offsets.size} tile(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Text(
                text = feedbackBanner,
                style = MaterialTheme.typography.bodySmall,
                color = PandaInk
            )
        }
    }

    if (showInstructions) {
        AlertDialog(
            onDismissRequest = { showInstructions = false },
            title = { Text("Panda Block Puzzle — How to Play", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1. Tap one of the 3 block shapes in your tray at the bottom.")
                    Text("2. Tap any valid cell on the 8×8 board to place the shape.")
                    Text("3. Fill any complete horizontal row or vertical column of 8 tiles to clear it and earn a +100x Line Combo Bonus.")
                    Text("4. Need help? Watch 2 consecutive rewarded video ads to highlight a valid placement cell!")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInstructions = false }) {
                    Text("Close")
                }
            }
        )
    }
}
