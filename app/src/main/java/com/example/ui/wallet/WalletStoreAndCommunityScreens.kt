package com.example.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.UiState
import com.example.viewmodel.VaultGoldViewModel

/**
 * Matches `Screenshot_2026-10-27-18-15-27-066.jpg`:
 * - Celebrating Panda Mascot header + Balance card with Gold Coin icon & large balance
 * - Segmented tabs: Redeem, History, Orders
 * - "Available Payment Methods" with Google Play Redeem Code cards and green "Redeem" button
 */
@Composable
fun WalletAndStoreScreen(
    viewModel: VaultGoldViewModel
) {
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val ledgerState by viewModel.ledger.collectAsStateWithLifecycle()
    val rewards by viewModel.rewardsCatalog.collectAsStateWithLifecycle()
    val redemptions by viewModel.redemptions.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Redeem, 1 = History, 2 = Orders
    var ledgerLimit by remember { mutableIntStateOf(15) }

    val isGuest = viewModel.isGuestUser || profile?.accountStatus == "guest"

    val promoCodes by viewModel.promoCodes.collectAsStateWithLifecycle()
    var promoCodeInput by remember { mutableStateOf("") }

    var pendingRedeemReward by remember { mutableStateOf<RewardCatalogItem?>(null) }
    var revealedCodesMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    if (pendingRedeemReward != null) {
        RedeemEmailAndConfirmDialog(
            reward = pendingRedeemReward!!,
            initialEmail = profile?.email ?: "",
            onConfirmSubmit = { rewardItem, confirmedEmail ->
                viewModel.submitRedemptionRequestWithEmail(
                    reward = rewardItem,
                    email = confirmedEmail,
                    onSuccess = {
                        pendingRedeemReward = null
                        selectedTab = 2
                    }
                )
            },
            onDismiss = { pendingRedeemReward = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        // 1. Balance Hero Card with Panda Mascot (`18-15-27-066.jpg`)
        item(key = "wallet_hero_balance") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(26.dp))
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp),
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
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Balance",
                                style = MaterialTheme.typography.titleMedium,
                                color = PandaMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FoundwayCoinIcon(size = 36.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${wallet.available}",
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = PandaInk
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Shared Task & Creator Coins",
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaGreenDark,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        PandaMascotIllustration(
                            size = 96.dp,
                            celebrating = true
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WalletStatChip("Pending", "${wallet.pending}", Modifier.weight(1f))
                        WalletStatChip("Earned", "${wallet.lifetimeEarned}", Modifier.weight(1f))
                        WalletStatChip("Redeemed", "${wallet.redeemed}", Modifier.weight(1f))
                    }
                }
            }
        }

        // Guest Notice (REDEEM requires Google sign-in)
        if (isGuest) {
            item(key = "wallet_guest_notice") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(22.dp))
                        .border(1.5.dp, PandaGold, RoundedCornerShape(22.dp))
                        .clickable { viewModel.openGuestLinkModal() }
                        .testTag("guest_wallet_signin_banner"),
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
                            Text(
                                text = "Sign in with Google to redeem",
                                style = MaterialTheme.typography.titleMedium,
                                color = PandaInk,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Link your Google account to keep your ${wallet.available} coins",
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { viewModel.openGuestLinkModal() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PandaGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("wallet_link_google_button")
                        ) {
                            Text("Sign in", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // 2. Segmented Tab Bar
        item(key = "wallet_tabs") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PandaWhiteCard, RoundedCornerShape(50))
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(50))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf("Redeem", "History", "Orders (${redemptions.size})")
                tabs.forEachIndexed { idx, label ->
                    val isSelected = selectedTab == idx
                    Surface(
                        color = if (isSelected) PandaGreen else Color.Transparent,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = idx }
                            .testTag("wallet_subtab_$idx")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) Color.White else PandaMuted,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }

        // Tab 0: Redeem Promo Code & Available Payment Methods
        if (selectedTab == 0) {
            // A. Redeem Promo Code Section (Part 3)
            item(key = "redeem_code_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(24.dp))
                        .border(1.dp, PandaGreen.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .testTag("redeem_promo_code_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Redeem Promo Code",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PandaInk
                                )
                                Text(
                                    text = "Enter a promo code to receive instant wallet coins",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PandaMuted
                                )
                            }
                            FoundwayCoinIcon(size = 32.dp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = promoCodeInput,
                                onValueChange = { promoCodeInput = it.uppercase() },
                                placeholder = { Text("e.g. PANDA100", color = PandaMuted, fontSize = 14.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = PandaMintBg,
                                    unfocusedContainerColor = PandaMintBg,
                                    focusedBorderColor = PandaGreen,
                                    unfocusedBorderColor = PandaCardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("promo_code_input")
                            )

                            Button(
                                onClick = {
                                    if (promoCodeInput.isNotBlank()) {
                                        viewModel.redeemPromoCode(promoCodeInput) {
                                            promoCodeInput = ""
                                        }
                                    }
                                },
                                enabled = promoCodeInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PandaGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("submit_promo_code_button")
                            ) {
                                Text("Claim", fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        // Active Community & Welcome Codes with Explicit Status Pills
                        Text(
                            text = "Active Codes & Community Drops:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PandaInk
                        )

                        promoCodes.forEach { (codeItem, status) ->
                            val statusColor = when (status) {
                                RedeemCodeStatus.AVAILABLE -> PandaGreen
                                RedeemCodeStatus.ALREADY_REDEEMED -> Color(0xFFF59E0B)
                                RedeemCodeStatus.OUT_OF_STOCK -> PandaCoral
                                RedeemCodeStatus.EXPIRED -> PandaMuted
                                RedeemCodeStatus.DISABLED -> PandaMuted
                                RedeemCodeStatus.INVALID -> PandaCoral
                            }

                            Surface(
                                color = PandaSoftMintSurface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, PandaCardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (status == RedeemCodeStatus.AVAILABLE) {
                                            promoCodeInput = codeItem.code
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = codeItem.code,
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = PandaInk
                                            )
                                            Text(
                                                text = "+${codeItem.rewardCoins} Coins",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PandaGreenDark
                                            )
                                        }
                                        Text(
                                            text = "${codeItem.description} (${codeItem.remainingStock} remaining)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PandaMuted,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Surface(
                                        color = statusColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, statusColor)
                                    ) {
                                        Text(
                                            text = status.label,
                                            color = statusColor,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "payment_methods_header") {
                Text(
                    text = "Google Play Gift Cards",
                    style = MaterialTheme.typography.titleMedium,
                    color = PandaInk,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            items(rewards, key = { it.id }) { item ->
                val inStock = item.stockCount > 0L && item.availability == "available"
                val canAfford = wallet.available >= item.pointsCost

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
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = PandaSoftMintSurface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    GiftBoxGraphic(size = 32.dp)
                                }
                            }

                            Column {
                                Text(
                                    text = item.rewardName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PandaInk,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FoundwayCoinIcon(size = 18.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${item.pointsCost} Coins",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = PandaGreenDark,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (inStock) "${item.stockCount} available" else "Out of stock",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (inStock) PandaMuted else PandaCoral
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isGuest) {
                                    viewModel.openGuestLinkModal()
                                } else {
                                    pendingRedeemReward = item
                                }
                            },
                            enabled = isGuest || (inStock && canAfford),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isGuest || (inStock && canAfford)) PandaGreen else PandaSoftMintSurface,
                                contentColor = if (isGuest || (inStock && canAfford)) Color.White else PandaMuted
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("redeem_reward_${item.id}")
                        ) {
                            Text(
                                text = when {
                                    isGuest -> "Redeem"
                                    !inStock -> "Sold out"
                                    !canAfford -> "Need ${item.pointsCost}"
                                    else -> "Redeem"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Tab 1: Paginated Coin History
        if (selectedTab == 1) {
            when (val state = ledgerState) {
                is UiState.Loading -> {
                    item(key = "ledger_loading") {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PandaGreen)
                        }
                    }
                }
                is UiState.Error -> {
                    item(key = "ledger_error") {
                        Text(state.message, color = PandaCoral)
                    }
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item(key = "ledger_empty") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                            ) {
                                Text(
                                    text = "No coin activity yet.",
                                    color = PandaMuted,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }
                    } else {
                        val visibleEntries = state.data.take(ledgerLimit)
                        items(visibleEntries, key = { it.id }) { entry ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, PandaCardBorder, RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        FoundwayCoinIcon(size = 28.dp)
                                        Column {
                                            Text(
                                                text = entry.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = PandaInk,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Balance: ${entry.balanceAfter}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PandaMuted
                                            )
                                        }
                                    }
                                    val isPositive = entry.amount >= 0
                                    Text(
                                        text = if (isPositive) "+${entry.amount}" else "${entry.amount}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (isPositive) PandaGreen else PandaCoral,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        if (state.data.size > ledgerLimit) {
                            item(key = "ledger_load_more") {
                                OutlinedButton(
                                    onClick = { ledgerLimit += 15 },
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, PandaGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Load more", color = PandaGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tab 2: Orders / Redemptions (Complete Redeem Workflow: Pending -> Approved/Reveal/Copy or Rejected)
        if (selectedTab == 2) {
            if (redemptions.isEmpty()) {
                item(key = "redemptions_empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No redemption requests yet.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PandaInk
                            )
                            Text(
                                text = "Select a Google Play Gift Card in the Redeem tab, enter your email address, and submit your request.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaMuted
                            )
                        }
                    }
                }
            } else {
                items(redemptions, key = { it.id }) { req ->
                    val statusBadgeBg = when {
                        req.isSuccessful -> PandaSoftMintSurface
                        req.isRejected -> PandaCoralContainer
                        else -> Color(0xFFFFF6DB)
                    }
                    val statusBadgeText = when {
                        req.isSuccessful -> PandaGreenDark
                        req.isRejected -> PandaCoral
                        else -> PandaGoldDark
                    }
                    val revealedCode = revealedCodesMap[req.id]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PandaCardBorder, RoundedCornerShape(20.dp))
                            .clickable(enabled = req.isSuccessful && revealedCode == null) {
                                viewModel.revealRedemptionCodeForUser(req.id) { code ->
                                    revealedCodesMap = revealedCodesMap + (req.id to code)
                                }
                            }
                            .testTag("redemption_order_card_${req.id}"),
                        shape = RoundedCornerShape(20.dp),
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
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.rewardName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PandaInk,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Request #${req.id.take(10)} • ${req.pointsReserved} Coins",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PandaMuted
                                    )
                                    if (req.userEmail.isNotBlank()) {
                                        Text(
                                            text = "Email: ${req.userEmail}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PandaInk,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Surface(
                                    color = statusBadgeBg,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, statusBadgeText.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = req.displayStatusLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = statusBadgeText,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            when {
                                req.isPending -> {
                                    Surface(
                                        color = Color(0xFFFFFBEB),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, PandaGold.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.HourglassBottom,
                                                contentDescription = null,
                                                tint = PandaGoldDark,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Redeem Pending — Awaiting staff verification. Redeem code stays hidden until approved.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PandaInk,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                req.isSuccessful -> {
                                    if (revealedCode == null) {
                                        Button(
                                            onClick = {
                                                viewModel.revealRedemptionCodeForUser(req.id) { code ->
                                                    revealedCodesMap = revealedCodesMap + (req.id to code)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PandaGreen,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("reveal_code_btn_${req.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Tap to Reveal Redeem Code", fontWeight = FontWeight.ExtraBold)
                                        }
                                    } else {
                                        Surface(
                                            color = PandaSoftMintSurface,
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.5.dp, PandaGreen),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("revealed_code_box_${req.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Your Approved Redeem Code",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = PandaGreenDark,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = revealedCode,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = PandaInk,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                                Button(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(ClipData.newPlainText("Athxo Booyah Redeem Code", revealedCode))
                                                        viewModel.postMessage("Copied redeem code $revealedCode")
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = PandaGreen,
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(50),
                                                    modifier = Modifier.testTag("copy_redeem_code_${req.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copy Code",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Copy Code", fontWeight = FontWeight.ExtraBold)
                                                }
                                            }
                                        }
                                    }
                                }
                                req.isRejected -> {
                                    Surface(
                                        color = PandaCoralContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, PandaCoral.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "Reason: ${req.rejectionReason?.ifBlank { "Verification criteria not met" } ?: "Verification criteria not met"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PandaCoral,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${req.pointsReserved} reserved coins have been refunded to your available balance.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PandaInk
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RedeemEmailAndConfirmDialog(
    reward: RewardCatalogItem,
    initialEmail: String,
    onConfirmSubmit: (RewardCatalogItem, String) -> Unit,
    onDismiss: () -> Unit
) {
    var emailInput by remember { mutableStateOf(initialEmail) }
    var isConfirmationStep by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val emailRegex = remember { Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PandaCardBorder, RoundedCornerShape(24.dp))
                .testTag("redeem_email_confirm_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AthxoBooyahLogoImage(size = 40.dp, cornerRadius = 10.dp)
                    Column {
                        Text(
                            text = if (isConfirmationStep) "Confirm Redemption Request" else "Redeem ${reward.rewardName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${reward.pointsCost} Coins • Athxo Booyah Rewards",
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaGreenDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!isConfirmationStep) {
                    Text(
                        text = "Enter your email address for verification and reward delivery:",
                        style = MaterialTheme.typography.bodySmall,
                        color = PandaMuted
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            validationError = null
                        },
                        label = { Text("Email Address") },
                        placeholder = { Text("you@example.com", color = PandaMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = PandaMintBg,
                            unfocusedContainerColor = PandaMintBg,
                            focusedTextColor = PandaInk,
                            unfocusedTextColor = PandaInk,
                            focusedBorderColor = PandaGreen,
                            unfocusedBorderColor = PandaCardBorder
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("redeem_email_input")
                    )

                    if (validationError != null) {
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaCoral,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = PandaMuted, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val trimmed = emailInput.trim()
                                if (!emailRegex.matches(trimmed)) {
                                    validationError = "Please enter a valid email address (e.g., player@gmail.com)."
                                } else if (initialEmail.isNotBlank() && !initialEmail.equals(trimmed, ignoreCase = true)) {
                                    validationError = "Email must match your signed-in account ($initialEmail)."
                                } else {
                                    validationError = null
                                    isConfirmationStep = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PandaGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("redeem_continue_to_confirm_btn")
                        ) {
                            Text("Continue", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    Surface(
                        color = PandaSoftMintSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PandaCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Reward: ${reward.rewardName}", fontWeight = FontWeight.ExtraBold, color = PandaInk)
                            Text("Coins to Reserve: ${reward.pointsCost} Coins", style = MaterialTheme.typography.bodySmall, color = PandaGreenDark, fontWeight = FontWeight.Bold)
                            Text("Verified Email: ${emailInput.trim()}", style = MaterialTheme.typography.bodySmall, color = PandaInk)
                            Text(
                                "Initial Status: Redeem Pending (Staff will review and assign your gift code. Once approved, tap the order to reveal and copy your code.)",
                                style = MaterialTheme.typography.labelSmall,
                                color = PandaMuted
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { isConfirmationStep = false }) {
                            Text("Back", color = PandaMuted, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onConfirmSubmit(reward, emailInput.trim()) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PandaGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("redeem_confirm_submit_btn")
                        ) {
                            Text("Confirm & Submit", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = PandaWhiteCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PandaCardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = PandaMuted)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = PandaInk,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * Matches `Screenshot_2026-10-27-18-15-34-230.jpg` & `18-15-30-509.jpg` ("Me" tab):
 * - Panda-ear profile avatar + display name + email + "Edit" button
 * - Refer & Earn card with copyable referral link & green "Invite Friends" button
 * - Clean white menu card with chevron rows (Help & Support, Sign out, and Admin Console only for non-user roles)
 */
@Composable
fun CommunityCodesAndSupportScreen(
    viewModel: VaultGoldViewModel,
    onSignOutRequested: () -> Unit,
    onOpenAdminConsole: () -> Unit = {},
    onNavigateToCreateEarn: () -> Unit = {}
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val tickets by viewModel.supportTickets.collectAsStateWithLifecycle()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }

    val isGuest = viewModel.isGuestUser || profile?.accountStatus == "guest"
    val refCode = profile?.referralCode?.ifBlank { "AB-2026" } ?: "AB-2026"
    val isAdminRole = profile?.role != null && profile?.role != "user"

    if (showEditNameDialog) {
        EditDisplayNameDialog(
            initialName = profile?.displayName ?: "Player",
            onSave = { newName ->
                viewModel.updateProfileDisplayName(newName)
                showEditNameDialog = false
            },
            onDismiss = { showEditNameDialog = false }
        )
    }

    if (showSupportDialog) {
        SupportTicketDialog(
            onSubmit = { subject, msg ->
                viewModel.createSupportTicket("Support", subject, msg)
                showSupportDialog = false
            },
            onDismiss = { showSupportDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        // 1. Profile Header Card (`18-15-34-230.jpg`)
        item(key = "me_profile_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(26.dp))
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Official Athxo Booyah Logo Avatar Badge
                    Box(contentAlignment = Alignment.Center) {
                        AthxoBooyahLogoImage(size = 84.dp, cornerRadius = 22.dp)
                        Surface(
                            color = PandaGreen,
                            shape = CircleShape,
                            border = BorderStroke(2.dp, Color.White),
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (profile?.displayName?.firstOrNull()?.uppercase() ?: "A"),
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = profile?.displayName ?: if (isGuest) "Guest Player" else "Player",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PandaInk,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = if (isGuest) "Guest account • Link Google to redeem" else (profile?.email ?: "Google account linked"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isGuest) PandaGoldDark else PandaMuted,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (isGuest) {
                            Button(
                                onClick = { viewModel.openGuestLinkModal() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PandaGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("me_link_google_button")
                            ) {
                                Text("Link Google account", fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        OutlinedButton(
                            onClick = { showEditNameDialog = true },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, PandaCardBorder),
                            modifier = Modifier.testTag("edit_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = PandaInk,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit name", color = PandaInk, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Refer & Earn Card (`18-15-30-509.jpg`)
        item(key = "me_invite_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(24.dp))
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Refer & Earn Coins!",
                                style = MaterialTheme.typography.titleLarge,
                                color = PandaInk,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Invite friends with your code & earn bonus rewards",
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaMuted
                            )
                        }
                        FoundwayCoinIcon(size = 38.dp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Copyable Referral Code Box
                    Surface(
                        color = PandaSoftMintSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PandaCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Your Referral Code",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PandaMuted
                                )
                                Text(
                                    text = refCode,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PandaInk,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Athxo Booyah Code", refCode))
                                    viewModel.postMessage("Copied code $refCode")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = PandaGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", color = PandaGreen, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Join me on Athxo Booyah (Play. Earn. Redeem.) with code $refCode!")
                            }
                            try {
                                context.startActivity(Intent.createChooser(sendIntent, "Share Athxo Booyah"))
                            } catch (_: Exception) {
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PandaGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("invite_friends_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Invite Friends", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // 3. Support & Account Actions (`18-15-34-230.jpg`)
        item(key = "me_actions_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(24.dp))
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    MeMenuRow(
                        title = "Create & Earn Video Studio",
                        icon = Icons.Default.VideoLibrary
                    ) {
                        viewModel.setAppMode(AppMode.CREATE_AND_EARN)
                        onNavigateToCreateEarn()
                    }
                    HorizontalDivider(color = PandaCardBorder)
                    MeMenuRow(
                        title = "My Creator Channel",
                        icon = Icons.Default.OndemandVideo
                    ) {
                        viewModel.setAppMode(AppMode.CREATE_AND_EARN)
                        onNavigateToCreateEarn()
                    }
                    HorizontalDivider(color = PandaCardBorder)
                    MeMenuRow(
                        title = "Help & Support (${tickets.size})",
                        icon = Icons.Default.SupportAgent
                    ) {
                        showSupportDialog = true
                    }
                    if (isAdminRole) {
                        HorizontalDivider(color = PandaCardBorder)
                        MeMenuRow(
                            title = "Admin Console",
                            icon = Icons.Default.Security
                        ) {
                            onOpenAdminConsole()
                        }
                    }
                    HorizontalDivider(color = PandaCardBorder)
                    MeMenuRow(
                        title = "Sign out",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        textColor = PandaCoral
                    ) {
                        onSignOutRequested()
                    }
                }
            }
        }
    }
}

@Composable
private fun MeMenuRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    textColor: Color = PandaInk,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = PandaSoftMintSurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (textColor == PandaCoral) PandaCoral else PandaGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = PandaMuted)
    }
}

@Composable
private fun EditDisplayNameDialog(
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf(initialName) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PandaCardBorder, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Edit name",
                    style = MaterialTheme.typography.titleLarge,
                    color = PandaInk,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PandaMintBg,
                        unfocusedContainerColor = PandaMintBg,
                        focusedTextColor = PandaInk,
                        unfocusedTextColor = PandaInk,
                        focusedBorderColor = PandaGreen,
                        unfocusedBorderColor = PandaCardBorder
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_name_input")
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = PandaMuted, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onSave(nameInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("save_profile_name_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportTicketDialog(
    onSubmit: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PandaCardBorder, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Help & Support",
                    style = MaterialTheme.typography.titleLarge,
                    color = PandaInk,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    placeholder = { Text("Subject", color = PandaMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PandaMintBg,
                        unfocusedContainerColor = PandaMintBg,
                        focusedTextColor = PandaInk,
                        unfocusedTextColor = PandaInk,
                        focusedBorderColor = PandaGreen,
                        unfocusedBorderColor = PandaCardBorder
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("ticket_subject_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text("Describe your issue", color = PandaMuted) },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PandaMintBg,
                        unfocusedContainerColor = PandaMintBg,
                        focusedTextColor = PandaInk,
                        unfocusedTextColor = PandaInk,
                        focusedBorderColor = PandaGreen,
                        unfocusedBorderColor = PandaCardBorder
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("ticket_message_input")
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { onSubmit(subject, message) },
                    colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().testTag("submit_ticket_button")
                ) {
                    Text("Send", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
