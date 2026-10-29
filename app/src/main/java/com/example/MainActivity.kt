package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.VaultGoldRepository
import com.example.data.sql.AthxoSqlDatabase
import com.example.model.AppMode
import com.example.ui.admin.AdminConsoleScreen
import com.example.ui.auth.AuthAndOnboardingScreen
import com.example.ui.auth.GuestLinkGoogleDialog
import com.example.ui.auth.signOut
import com.example.ui.create.CreateAndEarnScreen
import com.example.ui.create.CreatorStudioScreen
import com.example.ui.missions.HomeAndMissionsScreen
import com.example.ui.puzzles.PuzzlesAndVideosScreen
import com.example.ui.theme.*
import com.example.ui.wallet.CommunityCodesAndSupportScreen
import com.example.ui.wallet.WalletAndStoreScreen
import com.example.viewmodel.VaultGoldViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                VaultGoldRootApp()
            }
        }
    }
}

@Composable
fun VaultGoldRootApp(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var isGuestSession by remember { mutableStateOf(false) }
    var guestSessionUid by remember { mutableStateOf("guest_${UUID.randomUUID().toString().take(8)}") }

    val user = currentUser
    if (user == null && !isGuestSession) {
        AuthAndOnboardingScreen(
            onAuthSuccess = {
                isGuestSession = false
            },
            onEnterGuestPreview = {
                isGuestSession = true
            }
        )
    } else {
        val activeUserId = user?.uid ?: guestSessionUid
        val isGuest = user == null || user.isAnonymous
        val displayName = when {
            user == null || user.isAnonymous -> "Guest Player"
            !user.displayName.isNullOrBlank() -> user.displayName!!
            else -> "Athxo Booyah Player"
        }

        AuthenticatedVaultGoldApp(
            currentUserId = activeUserId,
            displayName = displayName,
            email = user?.email,
            photoUrl = user?.photoUrl?.toString(),
            isGuestUser = isGuest,
            onExitGuestSession = {
                isGuestSession = false
                guestSessionUid = "guest_${UUID.randomUUID().toString().take(8)}"
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedVaultGoldApp(
    currentUserId: String,
    displayName: String,
    email: String?,
    photoUrl: String?,
    isGuestUser: Boolean,
    onExitGuestSession: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: VaultGoldViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val sqlDb = AthxoSqlDatabase.getInstance(app)
                VaultGoldViewModel(
                    repository = VaultGoldRepository(
                        db = db,
                        fallbackUserId = currentUserId,
                        sqlDatabase = sqlDb
                    ),
                    currentUserId = currentUserId,
                    userDisplayName = displayName,
                    userEmail = email,
                    userPhotoUrl = photoUrl,
                    isGuestUser = isGuestUser
                )
            }
        }
    )

    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val showGuestLinkModal by viewModel.showGuestLinkModal.collectAsStateWithLifecycle()
    val appMode by viewModel.activeAppMode.collectAsStateWithLifecycle()

    LaunchedEffect(currentUserId) {
        viewModel.initUnityAds(context)
    }

    // 0=Home/Feed, 1=Play/Studio, 2=Wallet, 3=Me, 4=Admin
    var currentNavTab by remember { mutableIntStateOf(0) }
    val isAdminRole = remember(profile) {
        val role = profile?.role ?: "user"
        role != "user"
    }

    if (currentNavTab != 0) {
        BackHandler { currentNavTab = 0 }
    }

    if (showGuestLinkModal) {
        GuestLinkGoogleDialog(
            guestCoins = wallet.available,
            onLinkedSuccess = {
                viewModel.dismissGuestLinkModal()
                viewModel.postMessage("Google account linked! Your ${wallet.available} coins are ready to redeem.")
            },
            onSwitchToExistingGoogleAccount = { keepGuestCoins ->
                viewModel.dismissGuestLinkModal()
                if (keepGuestCoins) {
                    viewModel.postMessage("Signed in with Google and kept your ${wallet.available} guest coins.")
                } else {
                    viewModel.postMessage("Signed in with your existing Google account.")
                }
            },
            onDismiss = { viewModel.dismissGuestLinkModal() }
        )
    }

    val initialChar = remember(displayName) {
        displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "F"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PandaMintBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // MatchPanda Top Bar (`Screenshot_2026-10-27-18-16-05-029.jpg`):
            // Left: Circular user avatar + "Hi {Name}" / "Welcome back"
            // Right: White rounded Balance card with green wallet icon + Coins + Streak badge
            // Below: Dual Section Mode Switcher (Task & Earn | Create & Earn)
            Surface(
                color = PandaMintBg
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            AthxoBooyahLogoImage(
                                size = 42.dp,
                                cornerRadius = 12.dp,
                                modifier = Modifier
                                    .clickable { currentNavTab = 3 }
                                    .testTag("top_bar_athxo_logo")
                            )
                            Column {
                                Text(
                                    text = "Athxo Booyah",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PandaInk,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                if (isGuestUser) {
                                    Text(
                                        text = "Hi ${displayName.split(" ").firstOrNull() ?: "Guest"} • Tap to link Google",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PandaGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { viewModel.openGuestLinkModal() }
                                    )
                                } else {
                                    Text(
                                        text = "Hi ${displayName.split(" ").firstOrNull() ?: "Player"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PandaMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // White Balance Pill Card matching `18-16-05-029.jpg`
                            Surface(
                                color = PandaWhiteCard,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, PandaCardBorder),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .clickable { currentNavTab = 2 }
                                    .testTag("top_coin_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = PandaSoftMintSurface,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalanceWallet,
                                                contentDescription = "Balance",
                                                tint = PandaGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Balance",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PandaMuted,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = "${wallet.available}",
                                            color = PandaInk,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }

                            // Streak Flame Icon Button matching `18-16-05-029.jpg`
                            Surface(
                                color = PandaWhiteCard,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, PandaCardBorder),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clickable { viewModel.prepareDailyCheckIn() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LocalFireDepartment,
                                        contentDescription = "Daily Streak",
                                        tint = Color(0xFFFF7A00),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Dual Main Section Switcher: Task & Earn vs Create & Earn
                    Surface(
                        color = PandaWhiteCard,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, PandaCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isTaskEarn = appMode == AppMode.TASK_AND_EARN
                            Surface(
                                onClick = {
                                    viewModel.setAppMode(AppMode.TASK_AND_EARN)
                                    if (currentNavTab in 0..1) currentNavTab = 0
                                },
                                color = if (isTaskEarn) PandaGreen else Color.Transparent,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mode_task_and_earn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SportsEsports,
                                        contentDescription = null,
                                        tint = if (isTaskEarn) Color.White else PandaMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Task & Earn",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isTaskEarn) Color.White else PandaMuted
                                    )
                                }
                            }

                            val isCreateEarn = appMode == AppMode.CREATE_AND_EARN
                            Surface(
                                onClick = {
                                    viewModel.setAppMode(AppMode.CREATE_AND_EARN)
                                    if (currentNavTab in 0..1) currentNavTab = 0
                                },
                                color = if (isCreateEarn) PandaGreen else Color.Transparent,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mode_create_and_earn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OndemandVideo,
                                        contentDescription = null,
                                        tint = if (isCreateEarn) Color.White else PandaMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Create & Earn",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isCreateEarn) Color.White else PandaMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // MatchPanda Bottom Bar (`18-16-05-029.jpg` & `18-16-07-823.jpg`):
            // Clean white elevated bar with green pill active state
            Surface(
                color = PandaWhiteCard,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, PandaCardBorder)
            ) {
                NavigationBar(
                    containerColor = PandaWhiteCard,
                    contentColor = PandaInk,
                    tonalElevation = 0.dp
                ) {
                    val tab0Title = if (appMode == AppMode.CREATE_AND_EARN) "Feed" else "Home"
                    val tab0Icon = if (appMode == AppMode.CREATE_AND_EARN) Icons.Default.OndemandVideo else Icons.Default.Home
                    NavigationBarItem(
                        selected = currentNavTab == 0,
                        onClick = { currentNavTab = 0 },
                        icon = { Icon(tab0Icon, contentDescription = tab0Title) },
                        label = {
                            Text(
                                tab0Title,
                                fontWeight = if (currentNavTab == 0) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = PandaGreen,
                            indicatorColor = PandaGreen,
                            unselectedIconColor = PandaMuted,
                            unselectedTextColor = PandaMuted
                        ),
                        modifier = Modifier.testTag("nav_missions")
                    )

                    val tab1Title = if (appMode == AppMode.CREATE_AND_EARN) "Studio" else "Play"
                    val tab1Icon = if (appMode == AppMode.CREATE_AND_EARN) Icons.Default.VideoLibrary else Icons.Default.SportsEsports
                    NavigationBarItem(
                        selected = currentNavTab == 1,
                        onClick = { currentNavTab = 1 },
                        icon = { Icon(tab1Icon, contentDescription = tab1Title) },
                        label = {
                            Text(
                                tab1Title,
                                fontWeight = if (currentNavTab == 1) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = PandaGreen,
                            indicatorColor = PandaGreen,
                            unselectedIconColor = PandaMuted,
                            unselectedTextColor = PandaMuted
                        ),
                        modifier = Modifier.testTag("nav_puzzles")
                    )

                    NavigationBarItem(
                        selected = currentNavTab == 2,
                        onClick = { currentNavTab = 2 },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet") },
                        label = {
                            Text(
                                "Wallet",
                                fontWeight = if (currentNavTab == 2) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = PandaGreen,
                            indicatorColor = PandaGreen,
                            unselectedIconColor = PandaMuted,
                            unselectedTextColor = PandaMuted
                        ),
                        modifier = Modifier.testTag("nav_store")
                    )
                    NavigationBarItem(
                        selected = currentNavTab == 3,
                        onClick = { currentNavTab = 3 },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Me") },
                        label = {
                            Text(
                                "Me",
                                fontWeight = if (currentNavTab == 3) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = PandaGreen,
                            indicatorColor = PandaGreen,
                            unselectedIconColor = PandaMuted,
                            unselectedTextColor = PandaMuted
                        ),
                        modifier = Modifier.testTag("nav_community")
                    )
                    if (isAdminRole) {
                        NavigationBarItem(
                            selected = currentNavTab == 4,
                            onClick = { currentNavTab = 4 },
                            icon = { Icon(Icons.Default.Security, contentDescription = "Admin") },
                            label = {
                                Text(
                                    "Admin",
                                    fontWeight = if (currentNavTab == 4) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = PandaGreen,
                                indicatorColor = PandaGreen,
                                unselectedIconColor = PandaMuted,
                                unselectedTextColor = PandaMuted
                            ),
                            modifier = Modifier.testTag("nav_admin")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PandaMintBg)
                .padding(innerPadding)
        ) {
            if (bannerMessage != null) {
                Surface(
                    color = PandaWhiteCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, PandaGreen.copy(alpha = 0.45f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            PandaFaceGraphic(size = 26.dp)
                            Text(
                                text = bannerMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PandaInk,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearBanner() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = PandaMuted
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentNavTab) {
                    0 -> {
                        if (appMode == AppMode.CREATE_AND_EARN) {
                            CreateAndEarnScreen(
                                viewModel = viewModel,
                                onOpenStudio = { currentNavTab = 1 }
                            )
                        } else {
                            HomeAndMissionsScreen(
                                viewModel = viewModel,
                                onNavigateToPuzzles = { currentNavTab = 1 },
                                onNavigateToStore = { currentNavTab = 2 },
                                onNavigateToAdmin = { currentNavTab = 4 }
                            )
                        }
                    }
                    1 -> {
                        if (appMode == AppMode.CREATE_AND_EARN) {
                            CreatorStudioScreen(
                                viewModel = viewModel,
                                onBackToFeed = { currentNavTab = 0 }
                            )
                        } else {
                            PuzzlesAndVideosScreen(viewModel = viewModel)
                        }
                    }
                    2 -> WalletAndStoreScreen(viewModel = viewModel)
                    3 -> CommunityCodesAndSupportScreen(
                        viewModel = viewModel,
                        onSignOutRequested = {
                            signOut(context, credentialManager, onExitGuestSession, scope)
                        },
                        onOpenAdminConsole = { currentNavTab = 4 },
                        onNavigateToCreateEarn = {
                            viewModel.setAppMode(AppMode.CREATE_AND_EARN)
                            currentNavTab = 0
                        }
                    )
                    4 -> AdminConsoleScreen(viewModel = viewModel)
                }
            }
        }
    }
}
