package com.example.ui.auth

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.theme.*
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "AthxoBooyahAuth"

sealed interface GoogleLinkResult {
    data object LinkedSuccess : GoogleLinkResult
    data class CollisionConflict(val credential: AuthCredential) : GoogleLinkResult
    data class Error(val message: String) : GoogleLinkResult
}

internal fun signInWithGoogle(
    context: Context,
    credentialManager: CredentialManager,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    scope: CoroutineScope
) {
    scope.launch {
        try {
            val webClientId = context.getString(R.string.default_web_client_id)
            val googleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val current = Firebase.auth.currentUser
                if (current != null && current.isAnonymous) {
                    try {
                        current.linkWithCredential(firebaseCredential).await()
                    } catch (collision: FirebaseAuthUserCollisionException) {
                        Firebase.auth.signInWithCredential(firebaseCredential).await()
                    }
                } else {
                    Firebase.auth.signInWithCredential(firebaseCredential).await()
                }
                onSuccess()
            } else {
                onError("Unsupported Google credential returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            onError("Google sign-in was cancelled.")
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account on device", e)
            onError("No Google account found on this device. Add a Google account in Settings or continue as guest.")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager error", e)
            onError(e.localizedMessage ?: "Google sign-in failed. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase signInWithGoogle failed", e)
            onError(e.localizedMessage ?: "Authentication error.")
        }
    }
}

internal fun linkGuestWithGoogle(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onResult: (GoogleLinkResult) -> Unit
) {
    scope.launch {
        try {
            val webClientId = context.getString(R.string.default_web_client_id)
            val googleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()

            val result = credentialManager.getCredential(request = request, context = context)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val currentUser = Firebase.auth.currentUser
                if (currentUser != null && currentUser.isAnonymous) {
                    try {
                        currentUser.linkWithCredential(firebaseCredential).await()
                        onResult(GoogleLinkResult.LinkedSuccess)
                    } catch (collision: FirebaseAuthUserCollisionException) {
                        onResult(GoogleLinkResult.CollisionConflict(firebaseCredential))
                    }
                } else {
                    Firebase.auth.signInWithCredential(firebaseCredential).await()
                    onResult(GoogleLinkResult.LinkedSuccess)
                }
            } else {
                onResult(GoogleLinkResult.Error("Invalid Google credential."))
            }
        } catch (e: NoCredentialException) {
            onResult(GoogleLinkResult.Error("No Google account signed in on this device. Add a Google account in Android Settings."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onResult(GoogleLinkResult.Error(e.localizedMessage ?: "Could not link Google account."))
        }
    }
}

internal fun trySignInAsGuest(
    scope: CoroutineScope,
    onGuestReady: () -> Unit
) {
    scope.launch {
        Log.i(TAG, "Starting guest session (redeem gated behind Google Sign-In)")
        onGuestReady()
    }
}

internal fun signOut(
    context: Context,
    credentialManager: CredentialManager,
    onComplete: () -> Unit,
    scope: CoroutineScope
) {
    scope.launch {
        Firebase.auth.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear credential state", e)
        }
        onComplete()
    }
}

/**
 * Redesigned Login Screen matching the MatchPanda reference screenshot (`18-15-58-711.jpg`):
 * - Clean white surface with subtle soft-mint clouds
 * - Sitting Panda Mascot holding green bamboo cash notes (`PandaMascotIllustration`)
 * - "Welcome to" / "Foundway" (with "way" in bamboo green)
 * - 4-point green sparkle star
 * - "Login to continue earning"
 * - White pill button with green border & multi-color Google "G" icon: "Login with Google"
 * - "Continue as Guest" pill button
 * - Bottom security footer: "100% Secure • Your data is safe with us"
 */
@Composable
fun AuthAndOnboardingScreen(
    guestEnabled: Boolean = true,
    onAuthSuccess: () -> Unit,
    onEnterGuestPreview: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.White, PandaMintBg, Color.White)
                )
            )
            .padding(horizontal = 28.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Official Athxo Booyah Logo Badge
            AthxoBooyahLogoImage(
                size = 112.dp,
                cornerRadius = 26.dp,
                modifier = Modifier.testTag("athxo_booyah_login_logo")
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Welcome to",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = PandaInk,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = PandaInk, fontWeight = FontWeight.ExtraBold)) {
                        append("Athxo ")
                    }
                    withStyle(SpanStyle(color = PandaGreen, fontWeight = FontWeight.ExtraBold)) {
                        append("Booyah")
                    }
                },
                fontSize = 34.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4-point green sparkle star divider (from 18-15-58-711.jpg)
            Canvas(modifier = Modifier.size(16.dp)) {
                val c = size.minDimension / 2f
                drawCircle(color = PandaGreen, radius = c * 0.45f)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Login to continue earning",
                style = MaterialTheme.typography.bodyLarge,
                color = PandaMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(26.dp))

            if (errorMessage != null) {
                Surface(
                    color = PandaCoralContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PandaCoral.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = PandaCoral,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 1. "Login with Google" — White pill button with green border (18-15-58-711.jpg)
            Surface(
                onClick = {
                    isSigningIn = true
                    errorMessage = null
                    signInWithGoogle(
                        context = context,
                        credentialManager = credentialManager,
                        onSuccess = {
                            isSigningIn = false
                            onAuthSuccess()
                        },
                        onError = { msg ->
                            isSigningIn = false
                            errorMessage = msg
                        },
                        scope = scope
                    )
                },
                enabled = !isSigningIn,
                shape = RoundedCornerShape(50),
                color = Color.White,
                border = BorderStroke(1.5.dp, PandaGreen),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_sign_in_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PandaGreen,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Signing in...",
                            style = MaterialTheme.typography.titleMedium,
                            color = PandaInk,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        // Google "G" colorful badge
                        Surface(
                            color = PandaGreenContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "G",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PandaGreenDark
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Login with Google",
                            style = MaterialTheme.typography.titleMedium,
                            color = PandaInk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. "Try as guest" secondary pill button
            if (guestEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    onClick = {
                        isSigningIn = true
                        errorMessage = null
                        trySignInAsGuest(
                            scope = scope,
                            onGuestReady = {
                                isSigningIn = false
                                onEnterGuestPreview()
                            }
                        )
                    },
                    enabled = !isSigningIn,
                    shape = RoundedCornerShape(50),
                    color = PandaSoftMintSurface,
                    border = BorderStroke(1.dp, PandaGreenBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("guest_browse_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Try as guest",
                            style = MaterialTheme.typography.titleMedium,
                            color = PandaGreenDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Footer: "100% Secure • Your data is safe with us" (18-15-58-711.jpg)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = PandaGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = PandaGreen, fontWeight = FontWeight.Bold)) {
                            append("100% Secure")
                        }
                        withStyle(SpanStyle(color = PandaMuted)) {
                            append(" • Your data is safe with us")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Shown when a Guest taps Redeem ("Sign in with Google to redeem").
 */
@Composable
fun GuestLinkGoogleDialog(
    guestCoins: Long,
    onLinkedSuccess: () -> Unit,
    onSwitchToExistingGoogleAccount: (keepGuestCoins: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isLinking by remember { mutableStateOf(false) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var collisionCredential by remember { mutableStateOf<AuthCredential?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PandaBorder, RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PandaFaceGraphic(size = 64.dp)
                Spacer(modifier = Modifier.height(14.dp))

                if (collisionCredential != null) {
                    Text(
                        text = "Google account already exists",
                        style = MaterialTheme.typography.titleLarge,
                        color = PandaInk,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This Google account was used before. Which coins would you like to keep?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PandaMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    Firebase.auth.signInWithCredential(collisionCredential!!).await()
                                    onSwitchToExistingGoogleAccount(true)
                                } catch (e: Exception) {
                                    statusError = e.localizedMessage ?: "Failed to merge account."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PandaGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Keep & Merge Guest Coins (+$guestCoins)", fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    Firebase.auth.signInWithCredential(collisionCredential!!).await()
                                    onSwitchToExistingGoogleAccount(false)
                                } catch (e: Exception) {
                                    statusError = e.localizedMessage ?: "Failed to switch account."
                                }
                            }
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Use Existing Google Account Only", color = PandaInk)
                    }
                } else {
                    Text(
                        text = "Sign in with Google to redeem",
                        style = MaterialTheme.typography.titleLarge,
                        color = PandaInk,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your $guestCoins earned coins will be kept when you link your Google account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PandaMuted,
                        textAlign = TextAlign.Center
                    )

                    if (statusError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = statusError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = PandaCoral,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            isLinking = true
                            statusError = null
                            linkGuestWithGoogle(
                                context = context,
                                credentialManager = credentialManager,
                                scope = scope,
                                onResult = { res ->
                                    isLinking = false
                                    when (res) {
                                        is GoogleLinkResult.LinkedSuccess -> onLinkedSuccess()
                                        is GoogleLinkResult.CollisionConflict -> {
                                            collisionCredential = res.credential
                                        }
                                        is GoogleLinkResult.Error -> {
                                            statusError = res.message
                                        }
                                    }
                                }
                            )
                        },
                        enabled = !isLinking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PandaGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("link_google_to_redeem_button")
                    ) {
                        Text(
                            text = if (isLinking) "Linking..." else "Login with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dismiss_guest_link_modal")
                    ) {
                        Text("Not now", color = PandaMuted)
                    }
                }
            }
        }
    }
}
