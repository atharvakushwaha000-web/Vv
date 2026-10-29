package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.UnityAdsConfig
import com.example.model.RedeemCodeStatus
import com.example.ui.theme.*
import com.example.viewmodel.VaultGoldViewModel

data class AdminModuleItem(
    val number: Int,
    val title: String,
    val roleRequired: String,
    val summary: String
)

@Composable
fun AdminConsoleScreen(
    viewModel: VaultGoldViewModel
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val claims by viewModel.taskClaims.collectAsStateWithLifecycle()
    val rewards by viewModel.rewardsCatalog.collectAsStateWithLifecycle()
    val auditLogs by viewModel.adminAuditLogs.collectAsStateWithLifecycle()
    val adSessions by viewModel.adSessions.collectAsStateWithLifecycle()
    val tournamentArenas by viewModel.tournamentArenas.collectAsStateWithLifecycle()
    val videoWatchRecords by viewModel.videoWatchRecords.collectAsStateWithLifecycle()
    val promoCodes by viewModel.promoCodes.collectAsStateWithLifecycle()
    val monetizationApp by viewModel.monetizationApplication.collectAsStateWithLifecycle()
    val allRedemptions by viewModel.allRedemptionsForAdmin.collectAsStateWithLifecycle()

    var activeModule by remember { mutableIntStateOf(0) }
    var auditReasonInput by remember { mutableStateOf("Owner verified admin update") }

    // Module 9: Staff Redeem Approval & Rejection inputs
    var redeemCodeInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var redeemRejectReasonInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Module 12 & 20: RBAC Role Assignment & Manual Balance Adjustment inputs
    var targetUserUidInput by remember { mutableStateOf("") }
    var selectedRoleToAssign by remember { mutableStateOf("staff") }
    var manualBalanceDeltaInput by remember { mutableStateOf("50") }

    // Module 4: Add PlaySkill Mission
    var newAppName by remember { mutableStateOf("") }
    var newPackageName by remember { mutableStateOf("com.example.partnerapp") }
    var newRewardCoins by remember { mutableStateOf("150") }

    // Module 10: Promo Code Builder
    var newPromoCodeText by remember { mutableStateOf("") }
    var newPromoCoinsText by remember { mutableStateOf("50") }
    var newPromoStockText by remember { mutableStateOf("100") }

    // Module 18: Feature Flags (`settings/guestEnabled`, Panda 1s timer)
    var pandaMsInput by remember(settings.pandaShowMillis) { mutableStateOf(settings.pandaShowMillis.toString()) }
    var pandaCoinsInput by remember(settings.pandaRewardCoins) { mutableStateOf(settings.pandaRewardCoins.toString()) }

    val modules = remember {
        listOf(
            AdminModuleItem(1, "Economy & Telemetry Overview", "analyst", "Coin issuance, ad completions, stock burn & fraud checks"),
            AdminModuleItem(2, "User & Guest Account Manager", "support", "Inspect UIDs, guest-to-Google merges & account status"),
            AdminModuleItem(3, "PlaySkill Proof Review Queue", "campaignManager", "Review uploaded level screenshots & approve milestone coins"),
            AdminModuleItem(4, "PlaySkill App Campaign Builder", "campaignManager", "Create/edit Play Store missions & level milestones"),
            AdminModuleItem(5, "Videos Task Manager", "campaignManager", "Configure watch-only YouTube tasks & inspect watch verification"),
            AdminModuleItem(6, "Tournament & Mini-Games Manager", "campaignManager", "Enable/disable games & configure Ludo/Carrom entry fees & prizes"),
            AdminModuleItem(7, "Daily +5 & Quiz Manager", "campaignManager", "Configure daily check-in & quiz bank"),
            AdminModuleItem(8, "Community Bulletin Manager", "contentModerator", "Publish verified community announcements"),
            AdminModuleItem(9, "Reward Store Catalogue", "financeAdmin", "Manage ₹10/₹50/₹100 denominations & eligibility"),
            AdminModuleItem(10, "Gift-Code Stock Inventory", "financeAdmin", "Update real gift-code stock counts (masked & audited)"),
            AdminModuleItem(11, "YouTube Watch Verification Audit", "campaignManager", "Inspect YouTube watch-time verification & anti-duplicate checkpoints"),
            AdminModuleItem(12, "Immutable Coin Ledger", "financeAdmin", "Inspect idempotent ledger entries & adjustments"),
            AdminModuleItem(13, "Referral Review Queue", "support", "Inspect referral codes & device clusters"),
            AdminModuleItem(14, "Notifications & Quiet Hours", "contentModerator", "Configure category alerts"),
            AdminModuleItem(15, "Support Tickets & Disputes", "support", "Respond to user support tickets"),
            AdminModuleItem(16, "Duplicate Screenshot Hash Guard", "superAdmin", "Inspect SHA-256 proof hashes & velocity flags"),
            AdminModuleItem(17, "Campaign Schedule", "campaignManager", "View active & expiring PlaySkill missions"),
            AdminModuleItem(18, "Feature Flags & Unity Ads Config", "superAdmin", "Configure settings/guestEnabled & Panda game parameters"),
            AdminModuleItem(19, "Admin Audit Logs", "superAdmin", "Inspect append-only admin actions with actor & reason"),
            AdminModuleItem(20, "RBAC Role Manager", "superAdmin", "Assign superAdmin, financeAdmin, moderator & support roles"),
            AdminModuleItem(21, "Creator Monetization & Moderation", "contentModerator", "Review creator channel monetization & video copyright")
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item(key = "admin_header") {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AthxoBooyahLogoImage(size = 44.dp, cornerRadius = 12.dp)
                        Column {
                            Text(
                                text = "Athxo Booyah Staff Console",
                                style = MaterialTheme.typography.headlineSmall,
                                color = PandaInk,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Role: ${profile?.role ?: "owner"} • Redeem Approval, RBAC, SQL & Ads",
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = auditReasonInput,
                        onValueChange = { auditReasonInput = it },
                        label = { Text("Audit reason (required for staff actions)", color = PandaMuted) },
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
                            .testTag("admin_audit_reason_input")
                    )
                }
            }
        }

        // Quick Module Chips
        item(key = "admin_filter_chips") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeModule == 0,
                    onClick = { activeModule = 0 },
                    label = { Text("All Modules") }
                )
                FilterChip(
                    selected = activeModule == 9,
                    onClick = { activeModule = 9 },
                    label = { Text("M9: Redeem Queue (${allRedemptions.count { it.isPending }})") },
                    modifier = Modifier.testTag("admin_chip_m9_redeems")
                )
                FilterChip(
                    selected = activeModule == 20,
                    onClick = { activeModule = 20 },
                    label = { Text("M20: RBAC & SQL") },
                    modifier = Modifier.testTag("admin_chip_m20_rbac")
                )
                FilterChip(
                    selected = activeModule == 3,
                    onClick = { activeModule = 3 },
                    label = { Text("M3: Proof Queue (${claims.size})") }
                )
                FilterChip(
                    selected = activeModule == 4,
                    onClick = { activeModule = 4 },
                    label = { Text("M4: Add PlaySkill") }
                )
                FilterChip(
                    selected = activeModule == 6,
                    onClick = { activeModule = 6 },
                    label = { Text("M6: Tournaments & Games") },
                    modifier = Modifier.testTag("admin_chip_m6_tournaments")
                )
                FilterChip(
                    selected = activeModule == 10,
                    onClick = { activeModule = 10 },
                    label = { Text("M10: Codes & Stock") }
                )
                FilterChip(
                    selected = activeModule == 21,
                    onClick = { activeModule = 21 },
                    label = { Text("M21: Creator Review") }
                )
                FilterChip(
                    selected = activeModule == 18,
                    onClick = { activeModule = 18 },
                    label = { Text("M18: Flags & Unity") }
                )
                FilterChip(
                    selected = activeModule == 19,
                    onClick = { activeModule = 19 },
                    label = { Text("M19: Audit (${auditLogs.size})") }
                )
            }
        }

        // Module 9: Staff Redeem Approval & Rejection Queue (Requirement 5.2)
        if (activeModule == 9 || activeModule == 0) {
            item(key = "admin_m9_redeem_approval_queue") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp))
                        .testTag("admin_redeem_approval_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Module 9: Staff Redeem Approval Queue (${allRedemptions.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Verify user eligibility, assign a valid redeem code to approve, or reject with a reason (automatically refunds reserved coins).",
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaMuted
                        )

                        if (allRedemptions.isEmpty()) {
                            Text("No redemption requests submitted yet.", color = PandaMuted)
                        } else {
                            allRedemptions.forEach { req ->
                                val codeInput = redeemCodeInputs[req.id] ?: "GPLAY-AB-${req.id.takeLast(4).uppercase()}-2026"
                                val rejectInput = redeemRejectReasonInputs[req.id] ?: "Unverified activity or duplicate account"

                                Surface(
                                    color = PandaSoftMintSurface,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, PandaCardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${req.rewardName} • ${req.pointsReserved} Coins",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = PandaInk
                                                )
                                                Text(
                                                    text = "Req #${req.id.take(10)} • User: ${req.userId} • Email: ${req.userEmail.ifBlank { "N/A" }}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = PandaMuted
                                                )
                                            }
                                            Text(
                                                text = req.displayStatusLabel,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = when {
                                                    req.isSuccessful -> PandaGreenDark
                                                    req.isRejected -> PandaCoral
                                                    else -> PandaGoldDark
                                                },
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }

                                        if (req.isPending) {
                                            OutlinedTextField(
                                                value = codeInput,
                                                onValueChange = {
                                                    redeemCodeInputs = redeemCodeInputs + (req.id to it.uppercase())
                                                },
                                                label = { Text("Assign Redeem Code (min 6 chars)") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("staff_assign_code_input_${req.id}")
                                            )
                                            OutlinedTextField(
                                                value = rejectInput,
                                                onValueChange = {
                                                    redeemRejectReasonInputs = redeemRejectReasonInputs + (req.id to it)
                                                },
                                                label = { Text("Rejection Reason (if rejecting)") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("staff_reject_reason_input_${req.id}")
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        viewModel.adminApproveRedemption(
                                                            requestId = req.id,
                                                            assignedCode = codeInput,
                                                            auditReason = auditReasonInput
                                                        )
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = PandaGreen,
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(50),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("staff_approve_redeem_${req.id}")
                                                ) {
                                                    Text("Approve & Send Code", fontWeight = FontWeight.ExtraBold)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        viewModel.adminRejectRedemption(
                                                            requestId = req.id,
                                                            rejectionReason = rejectInput,
                                                            auditReason = auditReasonInput
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(50),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("staff_reject_redeem_${req.id}")
                                                ) {
                                                    Text("Reject & Refund", color = PandaCoral, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else if (req.isSuccessful) {
                                            Text(
                                                text = "Approved by ${req.reviewedByUid ?: "staff"} • Delivered (${req.deliveredCodeMasked ?: "Assigned"})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PandaGreenDark,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else if (req.isRejected) {
                                            Text(
                                                text = "Rejected: ${req.rejectionReason ?: "N/A"} (${req.pointsReserved} coins refunded)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PandaCoral,
                                                fontWeight = FontWeight.Bold
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

        // Module 20: RBAC Role Manager, Manual Balance Adjustment & SQL Relational Migration (Requirements 6 & 8)
        if (activeModule == 20) {
            item(key = "admin_m20_rbac_and_sql") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Module 20: RBAC, Balance Adjustment & SQL Engine",
                            style = MaterialTheme.typography.titleLarge,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold
                        )

                        OutlinedTextField(
                            value = targetUserUidInput,
                            onValueChange = { targetUserUidInput = it },
                            label = { Text("Target User ID") },
                            placeholder = { Text("e.g. user_123") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("user", "staff", "support", "financeAdmin", "superAdmin").forEach { roleOpt ->
                                FilterChip(
                                    selected = selectedRoleToAssign == roleOpt,
                                    onClick = { selectedRoleToAssign = roleOpt },
                                    label = { Text(roleOpt) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (targetUserUidInput.isNotBlank()) {
                                    viewModel.adminAssignUserRole(
                                        targetUserId = targetUserUidInput.trim(),
                                        newRole = selectedRoleToAssign,
                                        reason = auditReasonInput
                                    )
                                }
                            },
                            enabled = targetUserUidInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Assign Role '$selectedRoleToAssign' (Audited)", fontWeight = FontWeight.ExtraBold)
                        }

                        HorizontalDivider(color = PandaCardBorder)

                        Text(
                            text = "Manual Balance Adjustment (Requires Reason & Logs Before/After)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PandaInk
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manualBalanceDeltaInput,
                                onValueChange = { manualBalanceDeltaInput = it },
                                label = { Text("Delta Coins (+/-)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val delta = manualBalanceDeltaInput.toLongOrNull() ?: 0L
                                    if (targetUserUidInput.isNotBlank() && delta != 0L) {
                                        viewModel.adminAdjustUserBalance(
                                            targetUserId = targetUserUidInput.trim(),
                                            deltaCoins = delta,
                                            reason = auditReasonInput
                                        )
                                    }
                                },
                                enabled = targetUserUidInput.isNotBlank() && auditReasonInput.length >= 4,
                                colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text("Adjust Balance", fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        HorizontalDivider(color = PandaCardBorder)

                        OutlinedButton(
                            onClick = { viewModel.adminRunSqlMigration() },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, PandaGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_run_sql_migration_btn")
                        ) {
                            Text("Run SQL Backup, Migration & FK Integrity Check", color = PandaGreen, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // Module 22: Video Proof Review Queue (Real Verification & Atomic Coin Credit)
        if (activeModule == 22 || activeModule == 0) {
            item(key = "admin_m22_video_proof_queue") {
                val staffQueue by viewModel.staffVideoProofQueue.collectAsStateWithLifecycle()
                var rejectReasonInput by remember { mutableStateOf("") }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp))
                        .testTag("admin_video_proof_queue_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Module 22: Video Proof Verification Queue (${staffQueue.size})",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = PandaInk,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Review recorded gameplay videos. Approve to credit coins atomically; reject with a mandatory reason.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PandaMuted
                                )
                            }
                            IconButton(onClick = { viewModel.refreshStaffVideoProofQueue() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Queue", tint = PandaInk)
                            }
                        }

                        if (staffQueue.isEmpty()) {
                            Text("No video proof submissions found.", color = PandaMuted, style = MaterialTheme.typography.bodySmall)
                        } else {
                            staffQueue.forEach { sub ->
                                Surface(
                                    color = PandaMintBg,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, PandaCardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                sub.taskTitle,
                                                fontWeight = FontWeight.Bold,
                                                color = PandaInk,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Surface(
                                                color = when (sub.status) {
                                                    "approved" -> PandaGreen.copy(alpha = 0.2f)
                                                    "rejected" -> Color(0xFFFFEBEE)
                                                    else -> PandaGold.copy(alpha = 0.2f)
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    sub.status.uppercase(),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = when (sub.status) {
                                                        "approved" -> PandaGreen
                                                        "rejected" -> Color(0xFFD32F2F)
                                                        else -> PandaGoldDark
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            "User: ${sub.userDisplayName.ifBlank { sub.userId }} • Reward: +${sub.rewardCoins} Coins",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PandaInk
                                        )
                                        Text(
                                            "Video: ${sub.videoUri} (${sub.fileSizeBytes / 1024} KB, ${sub.durationSeconds}s, ${sub.mimeType})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PandaMuted
                                        )

                                        if (sub.status == "pending") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = {
                                                        viewModel.staffApproveVideoProof(sub.submissionId, auditReasonInput)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = PandaGreen),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Approve (+${sub.rewardCoins})", fontWeight = FontWeight.Bold)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        val r = if (rejectReasonInput.isNotBlank()) rejectReasonInput else "Video did not demonstrate task completion requirements"
                                                        viewModel.staffRejectVideoProof(sub.submissionId, r)
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Reject", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else if (sub.status == "rejected" && sub.rejectionReason.isNotBlank()) {
                                            Text(
                                                "Rejection Reason: ${sub.rejectionReason}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFD32F2F)
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

        // Module 3: PlaySkill Proof Review Queue
        if (activeModule == 3) {
            item(key = "admin_m3_proof_queue") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Module 3: PlaySkill Screenshot Review Queue",
                            style = MaterialTheme.typography.titleLarge,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (claims.isEmpty()) {
                            Text("No PlaySkill proofs submitted yet.", color = PandaMuted)
                        } else {
                            claims.forEach { claim ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(claim.title, color = PandaInk, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "Proof: ${claim.evidenceHash ?: "none"} • Status: ${claim.verificationStatus}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PandaMuted
                                        )
                                    }
                                    if (claim.verificationStatus != "approved") {
                                        Button(
                                            onClick = {
                                                viewModel.adminApprovePlaySkillProof(claim, auditReasonInput)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PandaGreen,
                                                contentColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.testTag("admin_approve_claim_${claim.id}")
                                        ) {
                                            Text("Approve +${claim.rewardCoins}", fontWeight = FontWeight.ExtraBold)
                                        }
                                    } else {
                                        Text("Approved", color = PandaGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Module 4: Add PlaySkill Campaign
        if (activeModule == 4) {
            item(key = "admin_m4_campaign_builder") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Module 4: Add PlaySkill App Mission",
                            style = MaterialTheme.typography.titleLarge,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newAppName,
                            onValueChange = { newAppName = it },
                            label = { Text("App Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPackageName,
                            onValueChange = { newPackageName = it },
                            label = { Text("Android Package ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newRewardCoins,
                            onValueChange = { newRewardCoins = it },
                            label = { Text("Total Up-To Coins") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (newAppName.isNotBlank()) {
                                    viewModel.adminAddCampaign(
                                        appName = newAppName,
                                        packageName = newPackageName,
                                        rewardCoins = newRewardCoins.toLongOrNull() ?: 150L,
                                        categoryLabel = "Featured",
                                        auditReason = auditReasonInput
                                    )
                                    newAppName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_publish_campaign_button")
                        ) {
                            Text("Publish PlaySkill Mission", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // Module 6: Tournament & Mini-Games Manager (Enable/Disable Games & Entry/Prize Config)
        if (activeModule == 6) {
            item(key = "admin_m6_tournaments_and_games") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Module 6: Tournament Entry/Prize & Game Toggles",
                            style = MaterialTheme.typography.titleLarge,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold
                        )

                        tournamentArenas.forEach { arena ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = PandaSoftMintSurface,
                                border = BorderStroke(1.dp, PandaCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(arena.title, fontWeight = FontWeight.ExtraBold, color = PandaInk)
                                            Text(
                                                "Entry: ${arena.entryCoins} Coins • Prize: ${arena.advertisedPrizeCoins} Coins",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PandaGold
                                            )
                                        }
                                        Switch(
                                            checked = arena.isEnabled,
                                            onCheckedChange = {
                                                viewModel.adminToggleGameEnabled(arena.gameId, it, auditReasonInput)
                                            },
                                            modifier = Modifier.testTag("admin_toggle_game_${arena.gameId}")
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.adminUpdateTournamentConfig(
                                                    tournamentId = arena.id,
                                                    entryCoins = 10L,
                                                    advertisedPrizeCoins = 30L,
                                                    activateRealPrizeArena = !arena.isRealPrizeActive,
                                                    auditReason = auditReasonInput
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                if (arena.isRealPrizeActive) "Arena: Live (10→30)" else "Enable 10→30 Arena",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.adminUpdateTournamentConfig(
                                                    tournamentId = arena.id,
                                                    entryCoins = 10L,
                                                    advertisedPrizeCoins = 35L,
                                                    activateRealPrizeArena = true,
                                                    auditReason = auditReasonInput
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Set Prize 35 Coins", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Module 10: Gift-Code Stock Inventory & Promo Codes Manager (Part 3)
        if (activeModule == 10) {
            item(key = "admin_m10_stock") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Module 10: Redeem Codes & Gift-Code Stock",
                            style = MaterialTheme.typography.titleLarge,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold
                        )

                        // 1. Promo Code Builder (Part 3.4)
                        Surface(
                            color = PandaSoftMintSurface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, PandaCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Create New Promo Code", fontWeight = FontWeight.Bold, color = PandaInk, style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newPromoCodeText,
                                        onValueChange = { newPromoCodeText = it.uppercase() },
                                        placeholder = { Text("Code (e.g. VIP2026)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = newPromoCoinsText,
                                        onValueChange = { newPromoCoinsText = it },
                                        placeholder = { Text("Coins") },
                                        singleLine = true,
                                        modifier = Modifier.width(90.dp)
                                    )
                                    OutlinedTextField(
                                        value = newPromoStockText,
                                        onValueChange = { newPromoStockText = it },
                                        placeholder = { Text("Stock") },
                                        singleLine = true,
                                        modifier = Modifier.width(90.dp)
                                    )
                                }
                                Button(
                                    onClick = {
                                        val coins = newPromoCoinsText.toLongOrNull() ?: 50L
                                        val stock = newPromoStockText.toLongOrNull() ?: 100L
                                        if (newPromoCodeText.isNotBlank()) {
                                            viewModel.adminCreatePromoCode(
                                                code = newPromoCodeText,
                                                title = "Admin Created Drop",
                                                rewardCoins = coins,
                                                maxStock = stock,
                                                category = "Admin",
                                                auditReason = auditReasonInput
                                            )
                                            newPromoCodeText = ""
                                        }
                                    },
                                    enabled = newPromoCodeText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Create & Publish Promo Code", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        // List of Active Promo Codes
                        Text("Active Promo Codes Inventory:", fontWeight = FontWeight.Bold, color = PandaInk, style = MaterialTheme.typography.titleSmall)
                        promoCodes.forEach { (codeItem, status) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${codeItem.code} (+${codeItem.rewardCoins} coins)", fontWeight = FontWeight.Bold, color = PandaInk)
                                    Text(
                                        text = "Stock: ${codeItem.remainingStock}/${codeItem.maxRedemptions} • Status: ${status.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (status == RedeemCodeStatus.AVAILABLE) PandaGreen else PandaMuted
                                    )
                                }
                                Switch(
                                    checked = codeItem.active,
                                    onCheckedChange = { active ->
                                        viewModel.adminTogglePromoCode(codeItem.id, active, auditReasonInput)
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = PandaCardBorder, modifier = Modifier.padding(vertical = 4.dp))

                        // 2. Google Play Gift-Card Catalog Stock
                        Text("Google Play Gift Cards Inventory:", fontWeight = FontWeight.Bold, color = PandaInk, style = MaterialTheme.typography.titleSmall)
                        rewards.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.rewardName, color = PandaInk, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Stock: ${item.stockCount} (${item.availability})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (item.stockCount > 0) PandaGreen else PandaCoral
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.adminUpdateRewardStock(item, item.stockCount + 10L, auditReasonInput) }
                                    ) {
                                        Text("+10", color = PandaInk)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.adminUpdateRewardStock(item, 0L, auditReasonInput) }
                                    ) {
                                        Text("0", color = PandaCoral)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Module 21: Creator Video & Monetization Review (Part 10 & 18)
        if (activeModule == 21) {
            item(key = "admin_m21_monetization") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Module 21: Creator Monetization & Moderation Review",
                            style = MaterialTheme.typography.titleLarge,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Review creator channel monetization requests and enforce video copyright policies.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaMuted
                        )

                        if (monetizationApp != null) {
                            Surface(
                                color = PandaSoftMintSurface,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, PandaCardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Application: ${monetizationApp!!.channelName}", fontWeight = FontWeight.Bold, color = PandaInk)
                                    Text("Status: ${monetizationApp!!.status} • Creator: ${monetizationApp!!.creatorId}", style = MaterialTheme.typography.bodySmall, color = PandaMuted)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.adminReviewMonetization(
                                                    creatorId = monetizationApp!!.creatorId,
                                                    approve = true,
                                                    notes = "Verified creator ownership and good standing",
                                                    auditReason = auditReasonInput
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White),
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Approve Monetization")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.adminReviewMonetization(
                                                    creatorId = monetizationApp!!.creatorId,
                                                    approve = false,
                                                    notes = "Missing eligibility criteria",
                                                    auditReason = auditReasonInput
                                                )
                                            },
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Reject", color = PandaCoral)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("No pending monetization applications.", color = PandaMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Module 11: YouTube Watch Verification Log
        if (activeModule == 11) {
            item(key = "admin_m11_youtube_watch_log") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Module 11: YouTube Watch Verification Log (${videoWatchRecords.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (videoWatchRecords.isEmpty()) {
                            Text("No YouTube watch verification sessions recorded yet.", style = MaterialTheme.typography.bodySmall, color = PandaMuted)
                        } else {
                            videoWatchRecords.values.forEach { rec ->
                                Text(
                                    "• ${rec.title} (${rec.youtubeVideoId}): ${rec.status} — Watched ${rec.watchedSeconds}s/${rec.minWatchSeconds}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PandaMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Module 18: Feature Flags (`settings/guestEnabled`, Panda 1s timer)
        if (activeModule == 18) {
            item(key = "admin_m18_flags") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Module 18: Remote Flags & Panda Config",
                            style = MaterialTheme.typography.titleLarge,
                            color = PandaInk,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("settings/guestEnabled (Try as guest)", color = PandaInk)
                            Switch(
                                checked = settings.guestEnabled,
                                onCheckedChange = {
                                    viewModel.adminUpdateSettings(settings.copy(guestEnabled = it, unityTestMode = false), auditReasonInput)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pandaMsInput,
                            onValueChange = { pandaMsInput = it },
                            label = { Text("Panda Memorize Duration (ms, default 1000)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pandaCoinsInput,
                            onValueChange = { pandaCoinsInput = it },
                            label = { Text("Panda Win Coins (default 5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.adminUpdateSettings(
                                    settings.copy(
                                        pandaShowMillis = pandaMsInput.toLongOrNull() ?: 1000L,
                                        pandaRewardCoins = pandaCoinsInput.toLongOrNull() ?: 5L,
                                        unityTestMode = false
                                    ),
                                    auditReasonInput
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Settings", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // Module 19: Audit Logs
        if (activeModule == 19) {
            items(auditLogs, key = { it.id }) { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCardBorder, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${log.action} → ${log.targetType}/${log.targetId}", color = PandaGreen, fontWeight = FontWeight.Bold)
                        Text("Reason: ${log.reason}", color = PandaInk)
                        if (!log.beforeValue.isNullOrBlank() || !log.afterValue.isNullOrBlank()) {
                            Text(
                                text = "Before: ${log.beforeValue?.takeIf { it.isNotBlank() } ?: "none"} → After: ${log.afterValue?.takeIf { it.isNotBlank() } ?: "none"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PandaInk,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text("Actor: ${log.adminUid} (${log.adminRole})", style = MaterialTheme.typography.bodySmall, color = PandaMuted)
                    }
                }
            }
        }

        // Unity Ads Production Configuration Summary
        item(key = "admin_unity_ads_summary") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Unity Ads Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = PandaGreen,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Provider: ${viewModel.unityAdManager.providerName}\n" +
                            "• Unity Project ID: ${UnityAdsConfig.unityProjectId}\n" +
                            "• Android Game ID: ${UnityAdsConfig.androidGameId}\n" +
                            "• Interstitial Placement ID: ${UnityAdsConfig.interstitialPlacementId} (${UnityAdsConfig.interstitialPlacementAlias})\n" +
                            "• Rewarded Placement ID: ${UnityAdsConfig.rewardedPlacementId}\n" +
                            "• Banner Placement ID: ${UnityAdsConfig.bannerPlacementId}\n" +
                            "• Test Mode: ${UnityAdsConfig.TEST_MODE}\n" +
                            "• Ad Sessions Logged: ${adSessions.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PandaInk
                    )
                }
            }
        }

        // All Modules Directory
        items(modules, key = { it.number }) { mod ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PandaCardBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        if (mod.number in listOf(3, 4, 6, 9, 10, 11, 18, 19, 20, 21)) activeModule = mod.number
                        else viewModel.postMessage("Module ${mod.number}: ${mod.title}")
                    },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PandaWhiteCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Module ${mod.number}: ${mod.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = PandaInk,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = mod.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaMuted
                        )
                    }
                    Surface(
                        color = PandaSoftMintSurface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = mod.roleRequired,
                            style = MaterialTheme.typography.labelSmall,
                            color = PandaGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
