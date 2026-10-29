package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

private val OriginalLightPandaScheme = lightColorScheme(
    primary = PandaGreen,
    onPrimary = Color.White,
    primaryContainer = PandaGreenContainer,
    onPrimaryContainer = PandaGreenDark,
    secondary = PandaPurple,
    onSecondary = Color.White,
    secondaryContainer = PandaPurpleContainer,
    onSecondaryContainer = PandaPurpleDark,
    tertiary = PandaGold,
    onTertiary = Color.White,
    tertiaryContainer = PandaGoldContainer,
    onTertiaryContainer = PandaGoldDark,
    background = PandaMintBg,
    onBackground = PandaInk,
    surface = PandaWhiteCard,
    onSurface = PandaInk,
    surfaceVariant = PandaSoftMintSurface,
    onSurfaceVariant = PandaMuted,
    outline = PandaBorder,
    error = PandaCoral,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OriginalLightPandaScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Official Athxo Booyah Panda Logo Badge rendered crisp with white/green/purple framing.
 */
@Composable
fun AthxoBooyahLogoImage(
    size: Dp = 44.dp,
    cornerRadius: Dp = (size.value * 0.24f).dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = Color.White,
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF22C55E), Color(0xFF7C3AED))
            )
        ),
        shadowElevation = 3.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.athxo_booyah_logo),
                contentDescription = "Athxo Booyah Official Panda Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
            )
        }
    }
}

/**
 * Official Logo Mark used in app headers and branding.
 */
@Composable
fun FoundwayLogoMark(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    AthxoBooyahLogoImage(size = size, modifier = modifier)
}

/**
 * Gold Panda Coin Icon matching the reference screenshots (gold coin with embossed panda silhouette).
 */
@Composable
fun FoundwayCoinIcon(
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 100f
        val center = Offset(s / 2f, s / 2f)

        // Outer gold rim
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFD54F), Color(0xFFF59E0B))
            ),
            radius = 47f * scale,
            center = center
        )
        // Inner coin face
        drawCircle(
            color = Color(0xFFFFC107),
            radius = 37f * scale,
            center = center
        )
        drawCircle(
            color = Color(0xFFD97706),
            radius = 37f * scale,
            center = center,
            style = Stroke(width = 3.5f * scale)
        )
        // Embossed panda ears + head silhouette in gold-brown
        val emboss = Color(0xFFB45309)
        drawCircle(color = emboss, radius = 6.5f * scale, center = Offset(36f * scale, 36f * scale))
        drawCircle(color = emboss, radius = 6.5f * scale, center = Offset(64f * scale, 36f * scale))
        drawCircle(color = Color(0xFFFFE082), radius = 16f * scale, center = Offset(50f * scale, 53f * scale))
        drawCircle(color = emboss, radius = 3.5f * scale, center = Offset(44f * scale, 50f * scale))
        drawCircle(color = emboss, radius = 3.5f * scale, center = Offset(56f * scale, 50f * scale))
        drawCircle(color = emboss, radius = 2.5f * scale, center = Offset(50f * scale, 56f * scale))
    }
}

/**
 * Faceted Purple Gem Icon matching the reference screenshots (`18-16-05-029` & `18-16-10-334`).
 */
@Composable
fun PurpleGemIcon(
    size: Dp = 26.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 100f

        val outerPath = Path().apply {
            moveTo(50f * scale, 8f * scale)
            lineTo(86f * scale, 34f * scale)
            lineTo(72f * scale, 88f * scale)
            lineTo(28f * scale, 88f * scale)
            lineTo(14f * scale, 34f * scale)
            close()
        }
        drawPath(
            path = outerPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFC084FC), Color(0xFF7C3AED), Color(0xFF5B21B6))
            )
        )

        // Inner facet highlight
        val innerFacet = Path().apply {
            moveTo(50f * scale, 18f * scale)
            lineTo(72f * scale, 38f * scale)
            lineTo(50f * scale, 74f * scale)
            lineTo(28f * scale, 38f * scale)
            close()
        }
        drawPath(
            path = innerFacet,
            color = Color(0xFFD8B4FE).copy(alpha = 0.55f)
        )
    }
}

/**
 * Cute Panda Face Graphic used in the Panda 3x3 game pill and revealed tiles (`18-16-10-334`).
 */
@Composable
fun PandaFaceGraphic(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 100f

        // Black ears
        drawCircle(color = Color(0xFF1C221C), radius = 15f * scale, center = Offset(24f * scale, 24f * scale))
        drawCircle(color = Color(0xFF1C221C), radius = 15f * scale, center = Offset(76f * scale, 24f * scale))

        // Soft shadow + white head
        drawCircle(color = Color(0xFFDDE7DC), radius = 37f * scale, center = Offset(50f * scale, 56f * scale))
        drawCircle(color = Color.White, radius = 36f * scale, center = Offset(50f * scale, 54f * scale))

        // Black eye patches
        drawOval(
            color = Color(0xFF1C221C),
            topLeft = Offset(26f * scale, 40f * scale),
            size = Size(17f * scale, 21f * scale)
        )
        drawOval(
            color = Color(0xFF1C221C),
            topLeft = Offset(57f * scale, 40f * scale),
            size = Size(17f * scale, 21f * scale)
        )

        // Green eyes + white shine
        drawCircle(color = Color(0xFF4CAF50), radius = 5f * scale, center = Offset(35f * scale, 49f * scale))
        drawCircle(color = Color(0xFF4CAF50), radius = 5f * scale, center = Offset(65f * scale, 49f * scale))
        drawCircle(color = Color.White, radius = 2.2f * scale, center = Offset(36.5f * scale, 47.5f * scale))
        drawCircle(color = Color.White, radius = 2.2f * scale, center = Offset(66.5f * scale, 47.5f * scale))

        // Pink cheeks
        drawCircle(color = Color(0xFFFFB3C1).copy(alpha = 0.7f), radius = 5.5f * scale, center = Offset(25f * scale, 64f * scale))
        drawCircle(color = Color(0xFFFFB3C1).copy(alpha = 0.7f), radius = 5.5f * scale, center = Offset(75f * scale, 64f * scale))

        // Nose & happy mouth
        drawOval(
            color = Color(0xFF1C221C),
            topLeft = Offset(44f * scale, 57f * scale),
            size = Size(12f * scale, 8f * scale)
        )
        drawCircle(color = Color(0xFFFF6B81), radius = 5.5f * scale, center = Offset(50f * scale, 70f * scale))
    }
}

/**
 * Full Sitting / Celebrating Panda Mascot holding green bamboo reward notes (`18-15-58-711` & `18-16-02-118`).
 */
@Composable
fun PandaMascotIllustration(
    size: Dp = 160.dp,
    celebrating: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 200f

        // Soft ground shadow
        drawOval(
            color = Color(0xFFDDF0DA),
            topLeft = Offset(36f * scale, 168f * scale),
            size = Size(128f * scale, 18f * scale)
        )

        // Decorative bamboo leaves behind panda
        val leafColor1 = Color(0xFFA5E19C)
        val leafColor2 = Color(0xFF66BB6A)
        drawOval(color = leafColor1, topLeft = Offset(16f * scale, 82f * scale), size = Size(34f * scale, 14f * scale))
        drawOval(color = leafColor2, topLeft = Offset(150f * scale, 82f * scale), size = Size(34f * scale, 14f * scale))

        // Sparkle stars
        drawCircle(color = Color(0xFF4CAF50), radius = 4f * scale, center = Offset(28f * scale, 48f * scale))
        drawCircle(color = Color(0xFFFFB800), radius = 4.5f * scale, center = Offset(172f * scale, 44f * scale))
        drawCircle(color = Color(0xFF4CAF50), radius = 3f * scale, center = Offset(164f * scale, 130f * scale))

        // Panda Torso / Belly
        drawOval(
            color = Color(0xFF1D241E),
            topLeft = Offset(54f * scale, 88f * scale),
            size = Size(92f * scale, 78f * scale)
        )
        drawOval(
            color = Color.White,
            topLeft = Offset(64f * scale, 98f * scale),
            size = Size(72f * scale, 62f * scale)
        )

        // Panda Ears
        drawCircle(color = Color(0xFF1D241E), radius = 18f * scale, center = Offset(64f * scale, 34f * scale))
        drawCircle(color = Color(0xFF1D241E), radius = 18f * scale, center = Offset(136f * scale, 34f * scale))

        // Panda Head
        drawOval(
            color = Color.White,
            topLeft = Offset(52f * scale, 26f * scale),
            size = Size(96f * scale, 82f * scale)
        )
        drawOval(
            color = Color(0xFFE3ECE2),
            topLeft = Offset(52f * scale, 26f * scale),
            size = Size(96f * scale, 82f * scale),
            style = Stroke(width = 2.5f * scale)
        )

        // Eye patches
        drawOval(color = Color(0xFF1D241E), topLeft = Offset(68f * scale, 52f * scale), size = Size(22f * scale, 26f * scale))
        drawOval(color = Color(0xFF1D241E), topLeft = Offset(110f * scale, 52f * scale), size = Size(22f * scale, 26f * scale))

        // Green irises & white pupils
        drawCircle(color = Color(0xFF4CAF50), radius = 6.5f * scale, center = Offset(79f * scale, 64f * scale))
        drawCircle(color = Color(0xFF4CAF50), radius = 6.5f * scale, center = Offset(121f * scale, 64f * scale))
        drawCircle(color = Color(0xFF111711), radius = 3.5f * scale, center = Offset(79f * scale, 64f * scale))
        drawCircle(color = Color(0xFF111711), radius = 3.5f * scale, center = Offset(121f * scale, 64f * scale))
        drawCircle(color = Color.White, radius = 2.5f * scale, center = Offset(81f * scale, 61.5f * scale))
        drawCircle(color = Color.White, radius = 2.5f * scale, center = Offset(123f * scale, 61.5f * scale))

        // Nose & happy open smile
        drawOval(color = Color(0xFF1D241E), topLeft = Offset(92f * scale, 72f * scale), size = Size(16f * scale, 10f * scale))
        drawOval(color = Color(0xFFD81B60), topLeft = Offset(91f * scale, 84f * scale), size = Size(18f * scale, 13f * scale))
        drawOval(color = Color(0xFFFF80AB), topLeft = Offset(94f * scale, 89f * scale), size = Size(12f * scale, 7f * scale))

        // Fanned Green Reward Cash Notes held in front of chest
        for (angle in listOf(-16f, 0f, 16f)) {
            rotate(degrees = angle, pivot = Offset(100f * scale, 138f * scale)) {
                drawRoundRect(
                    color = Color(0xFF66BB6A),
                    topLeft = Offset(84f * scale, 100f * scale),
                    size = Size(32f * scale, 46f * scale),
                    cornerRadius = CornerRadius(5f * scale, 5f * scale)
                )
                drawRoundRect(
                    color = Color(0xFF2E7D32),
                    topLeft = Offset(84f * scale, 100f * scale),
                    size = Size(32f * scale, 46f * scale),
                    cornerRadius = CornerRadius(5f * scale, 5f * scale),
                    style = Stroke(width = 2f * scale)
                )
                drawCircle(
                    color = Color(0xFFE8F5E9),
                    radius = 7f * scale,
                    center = Offset(100f * scale, 122f * scale)
                )
            }
        }

        // Panda Arms (raised if celebrating, hugging notes otherwise)
        if (celebrating) {
            drawOval(color = Color(0xFF1D241E), topLeft = Offset(26f * scale, 70f * scale), size = Size(34f * scale, 24f * scale))
            drawOval(color = Color(0xFF1D241E), topLeft = Offset(140f * scale, 70f * scale), size = Size(34f * scale, 24f * scale))
        } else {
            drawOval(color = Color(0xFF1D241E), topLeft = Offset(48f * scale, 104f * scale), size = Size(28f * scale, 36f * scale))
            drawOval(color = Color(0xFF1D241E), topLeft = Offset(124f * scale, 104f * scale), size = Size(28f * scale, 36f * scale))
        }

        // Panda Feet with cute grey paw pads
        drawOval(color = Color(0xFF1D241E), topLeft = Offset(50f * scale, 142f * scale), size = Size(34f * scale, 32f * scale))
        drawOval(color = Color(0xFF1D241E), topLeft = Offset(116f * scale, 142f * scale), size = Size(34f * scale, 32f * scale))
        drawCircle(color = Color(0xFF4A524A), radius = 6f * scale, center = Offset(67f * scale, 160f * scale))
        drawCircle(color = Color(0xFF4A524A), radius = 6f * scale, center = Offset(133f * scale, 160f * scale))
    }
}

/**
 * 3D Green Wallet overflowing with Gold Coins (`18-16-05-029` Available Points card right illustration).
 */
@Composable
fun GreenWalletWithCoinsIllustration(
    size: Dp = 88.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 120f

        // Overflowing Gold Coins at the top
        val coinPositions = listOf(
            Offset(42f * scale, 34f * scale),
            Offset(66f * scale, 26f * scale),
            Offset(86f * scale, 38f * scale),
            Offset(54f * scale, 44f * scale)
        )
        coinPositions.forEach { pos ->
            drawCircle(color = Color(0xFFF59E0B), radius = 14f * scale, center = pos)
            drawCircle(color = Color(0xFFFFD54F), radius = 10.5f * scale, center = pos)
        }

        // Tilted 3D Green Leather Wallet Body
        rotate(degrees = -10f, pivot = Offset(60f * scale, 74f * scale)) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF66BB6A), Color(0xFF2E9B27))
                ),
                topLeft = Offset(22f * scale, 44f * scale),
                size = Size(76f * scale, 56f * scale),
                cornerRadius = CornerRadius(14f * scale, 14f * scale)
            )
            // Clasp strap on right
            drawRoundRect(
                color = Color(0xFF1E7A18),
                topLeft = Offset(72f * scale, 62f * scale),
                size = Size(28f * scale, 20f * scale),
                cornerRadius = CornerRadius(8f * scale, 8f * scale)
            )
            // Gold button on clasp
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = 5.5f * scale,
                center = Offset(82f * scale, 72f * scale)
            )
        }
    }
}

/**
 * 3D Purple Treasure Chest overflowing with Gold Coins (`18-16-05-029` "Huge Rewards" card).
 */
@Composable
fun TreasureChestIllustration(
    size: Dp = 88.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 120f

        // Base of chest
        drawRoundRect(
            brush = Brush.linearGradient(colors = listOf(Color(0xFF8B5CF6), Color(0xFF5B21B6))),
            topLeft = Offset(18f * scale, 54f * scale),
            size = Size(74f * scale, 46f * scale),
            cornerRadius = CornerRadius(10f * scale, 10f * scale)
        )
        // Silver trim border
        drawRoundRect(
            color = Color(0xFFDDD6FE),
            topLeft = Offset(18f * scale, 54f * scale),
            size = Size(74f * scale, 46f * scale),
            cornerRadius = CornerRadius(10f * scale, 10f * scale),
            style = Stroke(width = 4f * scale)
        )

        // Gleaming pile of gold coins inside chest
        listOf(
            Offset(36f * scale, 50f * scale),
            Offset(54f * scale, 44f * scale),
            Offset(72f * scale, 50f * scale),
            Offset(46f * scale, 54f * scale),
            Offset(64f * scale, 54f * scale)
        ).forEach { pos ->
            drawCircle(color = Color(0xFFF59E0B), radius = 12f * scale, center = pos)
            drawCircle(color = Color(0xFFFFE082), radius = 8.5f * scale, center = pos)
        }

        // Open upper lid
        drawRoundRect(
            brush = Brush.linearGradient(colors = listOf(Color(0xFFA78BFA), Color(0xFF6D28D9))),
            topLeft = Offset(22f * scale, 22f * scale),
            size = Size(68f * scale, 24f * scale),
            cornerRadius = CornerRadius(10f * scale, 10f * scale)
        )

        // Extra stacked coins in front-right
        drawCircle(color = Color(0xFFF59E0B), radius = 14f * scale, center = Offset(92f * scale, 88f * scale))
        drawCircle(color = Color(0xFFFFD54F), radius = 10f * scale, center = Offset(92f * scale, 88f * scale))
    }
}

/**
 * Cute Panda with Claw Machine & Floating Gold Coins (`18-16-05-029` "Catch the Panda" card).
 */
@Composable
fun ClawMachinePandaIllustration(
    size: Dp = 88.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 120f

        // Purple Claw at top center
        drawLine(
            color = Color(0xFF7C3AED),
            start = Offset(60f * scale, 4f * scale),
            end = Offset(60f * scale, 22f * scale),
            strokeWidth = 4f * scale
        )
        drawCircle(color = Color(0xFF6D28D9), radius = 9f * scale, center = Offset(60f * scale, 24f * scale))

        // Floating gold coins around panda
        listOf(
            Offset(20f * scale, 42f * scale),
            Offset(100f * scale, 38f * scale),
            Offset(24f * scale, 86f * scale),
            Offset(98f * scale, 84f * scale)
        ).forEach { pos ->
            drawCircle(color = Color(0xFFF59E0B), radius = 9f * scale, center = pos)
            drawCircle(color = Color(0xFFFFD54F), radius = 6.5f * scale, center = pos)
        }

        // Cute Panda body & head
        drawOval(color = Color(0xFF1D241E), topLeft = Offset(42f * scale, 68f * scale), size = Size(36f * scale, 34f * scale))
        drawCircle(color = Color.White, radius = 13f * scale, center = Offset(60f * scale, 85f * scale))

        // Panda ears & head
        drawCircle(color = Color(0xFF1D241E), radius = 9f * scale, center = Offset(42f * scale, 40f * scale))
        drawCircle(color = Color(0xFF1D241E), radius = 9f * scale, center = Offset(78f * scale, 40f * scale))
        drawCircle(color = Color.White, radius = 22f * scale, center = Offset(60f * scale, 54f * scale))

        // Eyes & smile
        drawOval(color = Color(0xFF1D241E), topLeft = Offset(46f * scale, 46f * scale), size = Size(10f * scale, 12f * scale))
        drawOval(color = Color(0xFF1D241E), topLeft = Offset(64f * scale, 46f * scale), size = Size(10f * scale, 12f * scale))
        drawCircle(color = Color.White, radius = 2f * scale, center = Offset(51f * scale, 50f * scale))
        drawCircle(color = Color.White, radius = 2f * scale, center = Offset(69f * scale, 50f * scale))
        drawCircle(color = Color(0xFFFF6B81), radius = 3.5f * scale, center = Offset(60f * scale, 62f * scale))
    }
}

/**
 * 3D Streak Flame on a Green Pedestal (`18-16-07-823` Streak Bonus screen).
 */
@Composable
fun StreakFlamePedestalIllustration(
    size: Dp = 76.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 100f

        // Green pedestal disc
        drawOval(
            color = Color(0xFF2E7D32),
            topLeft = Offset(22f * scale, 74f * scale),
            size = Size(56f * scale, 18f * scale)
        )
        drawOval(
            color = Color(0xFF66BB6A),
            topLeft = Offset(22f * scale, 68f * scale),
            size = Size(56f * scale, 16f * scale)
        )

        // Outer orange flame
        val outerFlame = Path().apply {
            moveTo(50f * scale, 12f * scale)
            cubicTo(74f * scale, 32f * scale, 78f * scale, 58f * scale, 50f * scale, 76f * scale)
            cubicTo(22f * scale, 58f * scale, 26f * scale, 32f * scale, 50f * scale, 12f * scale)
            close()
        }
        drawPath(
            path = outerFlame,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFB300), Color(0xFFFF5722))
            )
        )

        // Inner bright yellow flame core
        val innerFlame = Path().apply {
            moveTo(50f * scale, 32f * scale)
            cubicTo(64f * scale, 46f * scale, 64f * scale, 62f * scale, 50f * scale, 72f * scale)
            cubicTo(36f * scale, 62f * scale, 36f * scale, 46f * scale, 50f * scale, 32f * scale)
            close()
        }
        drawPath(path = innerFlame, color = Color(0xFFFFEE58))
    }
}

/**
 * Wooden Crate Tile with green ribbon bookmark & brown paw print (`18-16-10-334` Play Panda Game 3x3 grid).
 */
@Composable
fun WoodenCrateGraphic(
    size: Dp = 72.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 100f

        // Outer wooden frame
        drawRoundRect(
            color = Color(0xFFB56E29),
            cornerRadius = CornerRadius(16f * scale, 16f * scale)
        )
        // Inner warm wood face
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFE7AD6B), Color(0xFFD6934A))
            ),
            topLeft = Offset(6f * scale, 6f * scale),
            size = Size(88f * scale, 88f * scale),
            cornerRadius = CornerRadius(12f * scale, 12f * scale)
        )

        // Horizontal plank grooves
        drawLine(
            color = Color(0xFFB56E29),
            start = Offset(8f * scale, 34f * scale),
            end = Offset(92f * scale, 34f * scale),
            strokeWidth = 2.5f * scale
        )
        drawLine(
            color = Color(0xFFB56E29),
            start = Offset(8f * scale, 68f * scale),
            end = Offset(92f * scale, 68f * scale),
            strokeWidth = 2.5f * scale
        )

        // Green ribbon tag at top center
        val ribbon = Path().apply {
            moveTo(41f * scale, 6f * scale)
            lineTo(59f * scale, 6f * scale)
            lineTo(59f * scale, 29f * scale)
            lineTo(50f * scale, 23f * scale)
            lineTo(41f * scale, 29f * scale)
            close()
        }
        drawPath(path = ribbon, color = Color(0xFF4CAF50))
        drawPath(path = ribbon, color = Color(0xFF2E7D32), style = Stroke(width = 2f * scale))

        // Cute brown paw print in center
        val pawColor = Color(0xFF7C4312)
        // Main pad
        drawOval(
            color = pawColor,
            topLeft = Offset(39f * scale, 52f * scale),
            size = Size(22f * scale, 17f * scale)
        )
        // 4 toes
        drawCircle(color = pawColor, radius = 4.2f * scale, center = Offset(35f * scale, 47f * scale))
        drawCircle(color = pawColor, radius = 4.5f * scale, center = Offset(44f * scale, 42f * scale))
        drawCircle(color = pawColor, radius = 4.5f * scale, center = Offset(56f * scale, 42f * scale))
        drawCircle(color = pawColor, radius = 4.2f * scale, center = Offset(65f * scale, 47f * scale))
    }
}

/**
 * Green Gift Box with Gold Ribbon (`18-16-02-118` & `18-16-05-029`).
 */
@Composable
fun GiftBoxGraphic(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 100f

        // Box body
        drawRoundRect(
            color = Color(0xFF43A047),
            topLeft = Offset(18f * scale, 40f * scale),
            size = Size(64f * scale, 48f * scale),
            cornerRadius = CornerRadius(10f * scale, 10f * scale)
        )
        // Box lid
        drawRoundRect(
            color = Color(0xFF2E7D32),
            topLeft = Offset(13f * scale, 28f * scale),
            size = Size(74f * scale, 16f * scale),
            cornerRadius = CornerRadius(6f * scale, 6f * scale)
        )
        // Vertical & horizontal gold ribbon
        drawRect(
            color = Color(0xFFFFCA28),
            topLeft = Offset(43f * scale, 28f * scale),
            size = Size(14f * scale, 60f * scale)
        )
        drawRect(
            color = Color(0xFFFFCA28),
            topLeft = Offset(18f * scale, 58f * scale),
            size = Size(64f * scale, 12f * scale)
        )
        // Ribbon bow loops
        drawOval(
            color = Color(0xFFFFB300),
            topLeft = Offset(28f * scale, 14f * scale),
            size = Size(22f * scale, 16f * scale),
            style = Stroke(width = 5f * scale)
        )
        drawOval(
            color = Color(0xFFFFB300),
            topLeft = Offset(50f * scale, 14f * scale),
            size = Size(22f * scale, 16f * scale),
            style = Stroke(width = 5f * scale)
        )
    }
}
