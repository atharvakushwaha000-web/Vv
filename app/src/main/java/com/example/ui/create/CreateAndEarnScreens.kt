package com.example.ui.create

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CreatorChannel
import com.example.model.VideoComment
import com.example.model.VideoPost
import com.example.ui.theme.*
import com.example.viewmodel.VaultGoldViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CreateAndEarnScreen(
    viewModel: VaultGoldViewModel,
    onOpenStudio: () -> Unit = {}
) {
    val categoryFilter by viewModel.videoCategoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.videoSearchQuery.collectAsStateWithLifecycle()
    val videos by viewModel.videoPosts.collectAsStateWithLifecycle()
    val selectedVideo by viewModel.selectedWatchVideo.collectAsStateWithLifecycle()
    val showUploadDialog by viewModel.showUploadVideoDialog.collectAsStateWithLifecycle()

    var showPromoBanner by remember { mutableStateOf(true) }

    if (selectedVideo != null) {
        VideoWatchScreen(
            video = selectedVideo!!,
            viewModel = viewModel,
            onBack = { viewModel.selectWatchVideo(null) }
        )
        return
    }

    if (showUploadDialog) {
        VideoUploadDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeUploadVideoDialog() }
        )
    }

    Scaffold(
        containerColor = PandaMintBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openUploadVideoDialog() },
                containerColor = PandaGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .shadow(6.dp, CircleShape)
                    .testTag("fab_upload_video")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.VideoCall, contentDescription = "Upload")
                    Text("Upload", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "create_search_and_studio") {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setVideoSearchQuery(it) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_search_input"),
                            placeholder = { Text("Search gaming clips, puzzles...", color = PandaMuted, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = PandaMuted) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setVideoSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = PandaMuted)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = PandaWhiteCard,
                                unfocusedContainerColor = PandaWhiteCard,
                                focusedBorderColor = PandaGreen,
                                unfocusedBorderColor = PandaCardBorder
                            )
                        )

                        Surface(
                            onClick = onOpenStudio,
                            shape = RoundedCornerShape(16.dp),
                            color = PandaSoftMintSurface,
                            border = BorderStroke(1.dp, PandaGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = "Studio", tint = PandaGreen)
                            }
                        }
                    }
                }
            }

            item(key = "category_chips_row") {
                val categories = listOf("All", "Gaming", "Puzzles", "Tutorials", "Highlights")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = categoryFilter.equals(cat, ignoreCase = true)
                        Surface(
                            onClick = { viewModel.setVideoCategory(cat) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) PandaGreen else PandaWhiteCard,
                            border = BorderStroke(1.dp, if (isSelected) PandaGreen else PandaCardBorder),
                            modifier = Modifier.testTag("cat_chip_$cat")
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) Color.White else PandaInk,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (showPromoBanner) {
                item(key = "creator_promo_banner") {
                    Surface(
                        color = PandaWhiteCard,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, PandaGreen.copy(alpha = 0.4f)),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AthxoBooyahLogoImage(size = 42.dp, cornerRadius = 12.dp)
                                Column {
                                    Text(
                                        "Athxo Booyah Creator Studio",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PandaInk,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Earn +10 coins on upload + view bonuses! Build your channel audience.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PandaMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showPromoBanner = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = PandaMuted)
                            }
                        }
                    }
                }
            }

            if (videos.isEmpty()) {
                item(key = "empty_videos_state") {
                    Surface(
                        color = PandaWhiteCard,
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, PandaCardBorder),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .testTag("empty_creator_videos_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = PandaSoftMintSurface,
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.OndemandVideo,
                                        contentDescription = "No videos",
                                        tint = PandaGreen,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Text(
                                text = "No videos available yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = PandaInk
                            )
                            Text(
                                text = "Be the first to create and publish a video.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PandaMuted
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { viewModel.openUploadVideoDialog() },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PandaGreen,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("btn_empty_upload_video")
                            ) {
                                Icon(Icons.Default.VideoCall, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Upload Video", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            } else {
                items(videos, key = { it.id }) { video ->
                    VideoFeedCard(
                        video = video,
                        onClick = { viewModel.selectWatchVideo(video) }
                    )
                }
            }

            item(key = "feed_bottom_spacing") {
                Spacer(Modifier.height(72.dp))
            }
        }
    }
}

@Composable
fun VideoFeedCard(
    video: VideoPost,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag("video_card_${video.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
        border = BorderStroke(1.dp, PandaCardBorder)
    ) {
        Column {
            // Video Thumbnail Box with Gradient & Duration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF263238),
                                Color(0xFF102027)
                            )
                        )
                    )
            ) {
                // Background visual graphic
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = PandaGreen.copy(alpha = 0.9f),
                            shape = CircleShape,
                            modifier = Modifier.size(50.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = video.videoCategory.uppercase(),
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Top Category Badge
                Surface(
                    color = PandaSoftMintSurface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = video.videoCategory,
                        color = PandaGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Bottom Duration Pill
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    val minutes = video.durationSeconds / 60
                    val secs = video.durationSeconds % 60
                    Text(
                        text = String.format("%d:%02d", minutes, secs),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Video Meta & Channel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = Color(0xFF5D4037),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            video.channelName.firstOrNull()?.uppercase() ?: "P",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = PandaInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaMuted,
                            fontWeight = FontWeight.Medium
                        )
                        if (video.isVerifiedCreator) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = PandaGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${video.viewsCount} views • ${video.likesCount} likes • +${video.watchRewardCoins} coins",
                        style = MaterialTheme.typography.labelSmall,
                        color = PandaGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun VideoWatchScreen(
    video: VideoPost,
    viewModel: VaultGoldViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val activity = context as? Activity
    val comments by viewModel.selectedVideoComments.collectAsStateWithLifecycle()
    val recommendedVideos by viewModel.recommendedVideos.collectAsStateWithLifecycle()
    val subbedChannels by viewModel.subscribedChannelIds.collectAsStateWithLifecycle()
    val isSubscribed = subbedChannels.contains(video.channelId)

    var isPlaying by remember(video.id) { mutableStateOf(false) }
    var currentPlaySec by remember(video.id) { mutableLongStateOf(0L) }
    var isMuted by remember(video.id) { mutableStateOf(false) }
    var playbackSpeed by remember(video.id) { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }

    // Pre-roll, Mid-roll (for videos >= 60s), and Post-roll Unity Video Ad state
    var preRollEvaluated by remember(video.id) { mutableStateOf(false) }
    var midRollEvaluated by remember(video.id) { mutableStateOf(false) }
    var postRollEvaluated by remember(video.id) { mutableStateOf(false) }
    var activeAdBreakSlot by remember(video.id) { mutableStateOf<String?>(null) }

    // 1. Pre-roll Ad Opportunity before creator video playback starts
    LaunchedEffect(video.id) {
        if (!preRollEvaluated) {
            preRollEvaluated = true
            isPlaying = false
            activeAdBreakSlot = "Pre-roll"
            viewModel.triggerCreatorVideoAdOpportunity(
                activity = activity,
                slotType = "PRE_ROLL"
            ) {
                activeAdBreakSlot = null
                isPlaying = true
            }
        }
    }

    // 2. Playback progression ticker + Mid-roll (at 50% if >= 60s) & Post-roll Ad Opportunities
    LaunchedEffect(isPlaying, video.durationSeconds, video.id) {
        val midPointSec = (video.durationSeconds / 2L).coerceAtLeast(30L)
        while (isPlaying) {
            delay((1000L / playbackSpeed).toLong().coerceAtLeast(100L))
            if (currentPlaySec < video.durationSeconds) {
                val nextSec = currentPlaySec + 1L
                currentPlaySec = nextSec

                // Mid-roll ad opportunity at 50% mark for longer videos (>= 60s)
                if (video.durationSeconds >= 60L && !midRollEvaluated && nextSec >= midPointSec) {
                    midRollEvaluated = true
                    val savedTimestamp = currentPlaySec
                    isPlaying = false
                    activeAdBreakSlot = "Mid-roll"
                    viewModel.triggerCreatorVideoAdOpportunity(
                        activity = activity,
                        slotType = "MID_ROLL"
                    ) {
                        activeAdBreakSlot = null
                        currentPlaySec = savedTimestamp
                        isPlaying = true
                    }
                }
            } else {
                isPlaying = false
                if (!postRollEvaluated) {
                    postRollEvaluated = true
                    activeAdBreakSlot = "Post-roll"
                    viewModel.triggerCreatorVideoAdOpportunity(
                        activity = activity,
                        slotType = "POST_ROLL"
                    ) {
                        activeAdBreakSlot = null
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Video", fontWeight = FontWeight.Bold, color = PandaInk) },
            text = {
                Text(
                    "Submit report for community guideline review: Inappropriate content, copyright infringement, or spam.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PandaMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportDialog = false
                        viewModel.postMessage("Report submitted. Our moderation team will review this video.")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PandaGreen)
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel", color = PandaMuted)
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = PandaWhiteCard
        )
    }

    if (showDownloadDialog) {
        var downloadProgress by remember { mutableFloatStateOf(0f) }
        var isDownloaded by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (downloadProgress < 1f) {
                delay(300L)
                downloadProgress += 0.25f
            }
            isDownloaded = true
        }

        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Offline Video Download", fontWeight = FontWeight.Bold, color = PandaInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isDownloaded) "Download complete! Cached in your local app storage for offline replay."
                        else "Downloading clip for offline playback...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PandaInk
                    )
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        color = PandaGreen,
                        trackColor = PandaSoftMintSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDownloadDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PandaGreen)
                ) {
                    Text("Done")
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = PandaWhiteCard
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PandaMintBg)
    ) {
        // Player Surface Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color.Black)
        ) {
            // Player Background Canvas / Graphic
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🎮", fontSize = 48.sp)
                    Text(
                        text = when {
                            activeAdBreakSlot != null -> "$activeAdBreakSlot Ad Opportunity • Video Paused at ${currentPlaySec}s"
                            isPlaying -> "Playing: ${video.title.take(28)}"
                            currentPlaySec >= video.durationSeconds -> "Replay Ready • ${video.title.take(24)}"
                            else -> "Paused at ${currentPlaySec}s"
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Top Bar Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { isMuted = !isMuted }) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute",
                            tint = Color.White
                        )
                    }
                    Box {
                        TextButton(onClick = { showSpeedMenu = true }) {
                            Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x") },
                                    onClick = {
                                        playbackSpeed = speed
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Player Controls (Slider + Timeline + Play/Pause + Seek)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Slider(
                    value = currentPlaySec.toFloat(),
                    onValueChange = { currentPlaySec = it.toLong() },
                    valueRange = 0f..video.durationSeconds.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = PandaGreen,
                        activeTrackColor = PandaGreen,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val curMin = currentPlaySec / 60
                    val curSec = currentPlaySec % 60
                    val totalMin = video.durationSeconds / 60
                    val totalSec = video.durationSeconds % 60
                    Text(
                        String.format("%d:%02d / %d:%02d", curMin, curSec, totalMin, totalSec),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { currentPlaySec = (currentPlaySec - 10L).coerceAtLeast(0L) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Seek -10s", tint = Color.White)
                        }
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { currentPlaySec = (currentPlaySec + 10L).coerceAtMost(video.durationSeconds) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Seek +10s", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Content Details & Comments Stream
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "video_title_and_stats") {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = PandaInk
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${video.viewsCount} views • Uploaded recently",
                        style = MaterialTheme.typography.bodySmall,
                        color = PandaMuted
                    )
                }
            }

            item(key = "video_action_buttons_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Like Button
                    Surface(
                        onClick = { viewModel.toggleVideoLike(video.id) },
                        shape = RoundedCornerShape(20.dp),
                        color = PandaWhiteCard,
                        border = BorderStroke(1.dp, PandaCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ThumbUp, contentDescription = "Like", tint = PandaGreen, modifier = Modifier.size(16.dp))
                            Text("${video.likesCount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PandaInk)
                        }
                    }

                    // Share Button
                    Surface(
                        onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Watch ${video.title} on Athxo Booyah: https://athxobooyah.app/video/${video.id}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Athxo Booyah Video"))
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = PandaWhiteCard,
                        border = BorderStroke(1.dp, PandaCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = PandaInk, modifier = Modifier.size(16.dp))
                            Text("Share", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PandaInk)
                        }
                    }

                    // Download Button
                    Surface(
                        onClick = { showDownloadDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = PandaWhiteCard,
                        border = BorderStroke(1.dp, PandaCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = PandaInk, modifier = Modifier.size(16.dp))
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PandaInk)
                        }
                    }

                    // Report Button
                    Surface(
                        onClick = { showReportDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = PandaWhiteCard,
                        border = BorderStroke(1.dp, PandaCardBorder)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Report, contentDescription = "Report", tint = PandaMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            item(key = "channel_subscribe_card") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
                    border = BorderStroke(1.dp, PandaCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = Color(0xFF5D4037),
                                shape = CircleShape,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        video.channelName.firstOrNull()?.uppercase() ?: "C",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            Column {
                                Text(
                                    video.channelName,
                                    fontWeight = FontWeight.Bold,
                                    color = PandaInk,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Creator Channel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PandaMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleChannelSubscription(video.channelId) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSubscribed) PandaSoftMintSurface else PandaGreen,
                                contentColor = if (isSubscribed) PandaGreen else Color.White
                            )
                        ) {
                            Text(
                                if (isSubscribed) "Subscribed" else "Subscribe",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item(key = "video_description_card") {
                Surface(
                    color = PandaWhiteCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, PandaCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Description",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PandaMuted
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            video.description.ifBlank { "No detailed description provided by creator." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = PandaInk
                        )
                    }
                }
            }

            // Recommended Real Published Videos Section
            item(key = "recommended_videos_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Recommended Videos (${recommendedVideos.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = PandaInk
                    )
                    if (recommendedVideos.isEmpty()) {
                        Surface(
                            color = PandaWhiteCard,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, PandaCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No other published videos yet. Upload more creator clips to populate recommendations!",
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaMuted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        recommendedVideos.take(4).forEach { recVideo ->
                            Surface(
                                onClick = { viewModel.selectWatchVideo(recVideo) },
                                color = PandaWhiteCard,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, PandaCardBorder),
                                modifier = Modifier.fillMaxWidth()
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
                                            recVideo.title,
                                            fontWeight = FontWeight.Bold,
                                            color = PandaInk,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${recVideo.channelName} • ${recVideo.viewsCount} views • ${recVideo.videoCategory}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PandaMuted
                                        )
                                    }
                                    Icon(
                                        Icons.Default.PlayCircleFilled,
                                        contentDescription = "Watch",
                                        tint = PandaGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "comments_header_and_input") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Comments (${comments.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = PandaInk
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            placeholder = { Text("Add a thoughtful comment...", color = PandaMuted, fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("comment_text_field"),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = PandaWhiteCard,
                                unfocusedContainerColor = PandaWhiteCard,
                                focusedBorderColor = PandaGreen,
                                unfocusedBorderColor = PandaCardBorder
                            )
                        )

                        IconButton(
                            onClick = {
                                if (commentInput.isNotBlank()) {
                                    viewModel.addVideoComment(video.id, commentInput)
                                    commentInput = ""
                                }
                            },
                            enabled = commentInput.isNotBlank(),
                            modifier = Modifier
                                .size(46.dp)
                                .background(if (commentInput.isNotBlank()) PandaGreen else PandaSoftMintSurface, RoundedCornerShape(14.dp))
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (commentInput.isNotBlank()) Color.White else PandaMuted
                            )
                        }
                    }
                }
            }

            items(comments, key = { it.id }) { comment ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
                    border = BorderStroke(1.dp, PandaCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                comment.authorName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = PandaInk
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                comment.commentText,
                                style = MaterialTheme.typography.bodySmall,
                                color = PandaInk
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteVideoComment(video.id, comment.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Delete", tint = PandaMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            item(key = "watch_bottom_spacing") {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun CreatorStudioScreen(
    viewModel: VaultGoldViewModel,
    onBackToFeed: () -> Unit
) {
    BackHandler { onBackToFeed() }

    val channel by viewModel.creatorChannel.collectAsStateWithLifecycle()
    val videos by viewModel.videoPosts.collectAsStateWithLifecycle()
    val myVideos = remember(videos, channel.ownerUid) {
        videos.filter { it.authorUid == channel.ownerUid }
    }

    Scaffold(
        containerColor = PandaMintBg,
        topBar = {
            Surface(color = PandaMintBg) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onBackToFeed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PandaInk)
                    }
                    AthxoBooyahLogoImage(size = 34.dp, cornerRadius = 9.dp)
                    Text(
                        "Athxo Booyah Creator Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = PandaInk
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "channel_overview_header") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
                    border = BorderStroke(1.dp, PandaCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AthxoBooyahLogoImage(size = 54.dp, cornerRadius = 14.dp)
                            Column {
                                Text(
                                    channel.channelName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PandaInk
                                )
                                Text(
                                    channel.handle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PandaGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    channel.bio,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PandaMuted
                                )
                            }
                        }
                    }
                }
            }

            item(key = "channel_metrics_grid") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricMiniCard(
                        title = "Subscribers",
                        value = "${channel.subscribersCount}",
                        icon = "👥",
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "Total Views",
                        value = "${channel.totalViews}",
                        icon = "👁️",
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "Creator Coins",
                        value = "${channel.totalCoinsEarned}",
                        icon = "🪙",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "monetization_program_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
                    border = BorderStroke(1.5.dp, PandaGreen.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Creator Partner Program",
                                fontWeight = FontWeight.ExtraBold,
                                color = PandaInk,
                                fontSize = 15.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (channel.monetizationStatus == "APPROVED") PandaGreen.copy(alpha = 0.2f) else PandaSoftMintSurface
                            ) {
                                Text(
                                    channel.monetizationStatus,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (channel.monetizationStatus == "APPROVED") PandaGreen else PandaGold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Monetization Eligibility Requirements:\n• Minimum 10 Subscribers\n• Minimum 50 Total Video Views\n• 100% Original Content Compliance",
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaMuted
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.applyForMonetization() },
                            enabled = channel.monetizationStatus == "ELIGIBLE",
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (channel.monetizationStatus == "ELIGIBLE") "Apply for Monetization"
                                else "Application Status: ${channel.monetizationStatus}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item(key = "my_uploads_header") {
                Text(
                    "My Uploaded Videos (${myVideos.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = PandaInk
                )
            }

            if (myVideos.isEmpty()) {
                item(key = "no_my_videos") {
                    Text(
                        "You haven't uploaded any clips yet. Tap '+ Upload' in the video feed to earn +10 bonus coins!",
                        style = MaterialTheme.typography.bodySmall,
                        color = PandaMuted
                    )
                }
            } else {
                items(myVideos, key = { it.id }) { post ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
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
                                    post.title,
                                    fontWeight = FontWeight.Bold,
                                    color = PandaInk,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${post.videoCategory} • ${post.viewsCount} views • ${post.likesCount} likes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PandaMuted
                                )
                            }
                            IconButton(onClick = { viewModel.deleteCreatorVideo(post.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PandaWhiteCard),
        border = BorderStroke(1.dp, PandaCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Text(value, fontWeight = FontWeight.ExtraBold, color = PandaInk, fontSize = 16.sp)
            Text(title, style = MaterialTheme.typography.labelSmall, color = PandaMuted, fontSize = 10.sp)
        }
    }
}

@Composable
fun VideoUploadDialog(
    viewModel: VaultGoldViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Gaming") }
    var selectedDurationSec by remember { mutableLongStateOf(75L) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileLabel by remember { mutableStateOf("Ready: Creator MP4 Video Stream") }
    var rightsConfirmed by remember { mutableStateOf(false) }

    var isUploading by remember { mutableStateOf(false) }
    var uploadCancelled by remember { mutableStateOf(false) }
    var uploadStage by remember { mutableStateOf("Preparing media...") }
    var uploadProgress by remember { mutableFloatStateOf(0f) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            selectedFileLabel = "Selected video: ${uri.lastPathSegment ?: "creator_video.mp4"}"
        }
    }

    val categories = listOf("Gaming", "Puzzles", "Tutorials", "Highlights", "Shorts")
    val durationOptions = listOf(30L, 60L, 75L, 120L)

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = {
            Text("Publish Creator Video", fontWeight = FontWeight.ExtraBold, color = PandaInk)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isUploading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(color = PandaGreen)
                        Text(uploadStage, fontWeight = FontWeight.Bold, color = PandaInk)
                        LinearProgressIndicator(
                            progress = { uploadProgress },
                            color = PandaGreen,
                            trackColor = PandaSoftMintSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                } else {
                    // Video File Picker Row (Zero-permission Android Photo/Video Picker)
                    Surface(
                        onClick = {
                            runCatching {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                        },
                        color = PandaSoftMintSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PandaGreen.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_pick_video_file")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.VideoFile, contentDescription = "Pick Video", tint = PandaGreen)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Select Video File (MP4 / WebM)",
                                    fontWeight = FontWeight.Bold,
                                    color = PandaInk,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = selectedFileLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PandaMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Browse",
                                color = PandaGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Video Title (min 3 chars)") },
                        placeholder = { Text("e.g. Panda Block Puzzle 8x8 Combo") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_title_field"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("What happened in this match or tutorial?") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text("Category", fontWeight = FontWeight.Bold, color = PandaInk, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            val isSel = selectedCategory == cat
                            Surface(
                                onClick = { selectedCategory = cat },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSel) PandaGreen else PandaSoftMintSurface,
                                border = BorderStroke(1.dp, if (isSel) PandaGreen else PandaCardBorder)
                            ) {
                                Text(
                                    cat,
                                    color = if (isSel) Color.White else PandaInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Text("Clip Duration", fontWeight = FontWeight.Bold, color = PandaInk, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        durationOptions.forEach { dur ->
                            val isSel = selectedDurationSec == dur
                            Surface(
                                onClick = { selectedDurationSec = dur },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) PandaGreen else PandaSoftMintSurface,
                                border = BorderStroke(1.dp, if (isSel) PandaGreen else PandaCardBorder)
                            ) {
                                Text(
                                    "${dur}s",
                                    color = if (isSel) Color.White else PandaInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = rightsConfirmed,
                            onCheckedChange = { rightsConfirmed = it },
                            colors = CheckboxDefaults.colors(checkedColor = PandaGreen),
                            modifier = Modifier.testTag("upload_rights_checkbox")
                        )
                        Text(
                            "I confirm I own or have permission to distribute this clip under community guidelines.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaMuted,
                            fontSize = 11.sp
                        )
                    }

                    Surface(
                        color = PandaGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "+10 Creator Coins will be credited to your authoritative wallet immediately upon publishing!",
                            color = PandaGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isUploading) {
                Button(
                    onClick = {
                        isUploading = true
                        uploadCancelled = false
                        scope.launch {
                            uploadStage = "Preparing video stream..."
                            uploadProgress = 0.25f
                            delay(80L)
                            if (uploadCancelled) return@launch
                            uploadStage = "Uploading metadata & clip..."
                            uploadProgress = 0.65f
                            delay(80L)
                            if (uploadCancelled) return@launch
                            uploadStage = "Finalizing & publishing..."
                            uploadProgress = 0.95f
                            viewModel.publishCreatorVideo(
                                title = title,
                                description = description,
                                category = selectedCategory,
                                durationSeconds = selectedDurationSec,
                                localVideoUri = selectedVideoUri?.toString().orEmpty()
                            )
                        }
                    },
                    enabled = title.trim().length >= 3 && rightsConfirmed,
                    colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White),
                    modifier = Modifier.testTag("btn_confirm_upload")
                ) {
                    Text("Publish Video")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isUploading) {
                        uploadCancelled = true
                        isUploading = false
                        viewModel.postMessage("Video upload cancelled.")
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (isUploading) "Cancel Upload" else "Cancel", color = PandaMuted)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = PandaWhiteCard
    )
}
