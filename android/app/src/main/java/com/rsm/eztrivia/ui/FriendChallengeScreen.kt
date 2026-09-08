package com.rsm.eztrivia.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rsm.eztrivia.data.FriendChallengeResult
import com.rsm.eztrivia.data.PlayerState
import com.rsm.eztrivia.model.FriendChallengeCode
import com.rsm.eztrivia.model.FriendChallengeLink
import com.rsm.eztrivia.model.RoundSummary
import com.rsm.eztrivia.share.ScoreCardContent
import com.rsm.eztrivia.share.ScoreCardShare
import java.text.NumberFormat

private val friendChallengeTint = Color(0xFF4F46E5)

@Composable
fun FriendChallengeHomeCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                color = friendChallengeTint.copy(alpha = 0.14f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("2", color = friendChallengeTint, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Friend Challenge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Play a random set, then share it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun FriendChallengeLobbyScreen(
    playerState: PlayerState,
    initialInput: String?,
    onBack: () -> Unit,
    onCreateChallenge: () -> Unit,
    onOpenChallenge: (FriendChallengeCode) -> Unit,
) {
    var codeText by remember(initialInput) { mutableStateOf(initialInput.orEmpty()) }
    var triedInvalidCode by remember(initialInput) { mutableStateOf(!initialInput.isNullOrBlank()) }
    val parsedCode = remember(codeText) { FriendChallengeLink.codeFrom(codeText) }
    val existingResult = parsedCode?.let(playerState::friendChallengeResult)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Categories") }
            Spacer(modifier = Modifier.weight(1f))
            Text("Friend Challenge", fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Same questions. One attempt.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Each challenge draws ten categories with the same Easy-to-Hard progression for both players.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCreateChallenge),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = friendChallengeTint.copy(alpha = 0.14f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text("✦", color = friendChallengeTint, fontWeight = FontWeight.Bold)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Create a challenge", fontWeight = FontWeight.Bold)
                    Text(
                        "Finish the round to get a tap-to-open link and fallback code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Have a challenge link or code?", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = codeText,
                    onValueChange = {
                        codeText = it
                        triedInvalidCode = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("EZ3-XXXX-XXXX-XXXX-XXXX-XXX") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )

                if (parsedCode != null) {
                    Text(
                        "Target: ${parsedCode.targetScore}/10 · ${formatPoints(parsedCode.targetPoints)} points" +
                            if (existingResult == null) "" else " · already played",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = friendChallengeTint,
                    )
                } else if (triedInvalidCode && codeText.isNotBlank()) {
                    val message = when (FriendChallengeLink.rejectionReason(codeText)) {
                        is FriendChallengeCode.RejectionReason.UnsupportedVersion ->
                            "That code came from an older version of EZ Trivia. Ask your friend for a new one."
                        else -> "That challenge link or code is incomplete or has a typo."
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = {
                        val code = parsedCode
                        if (code == null) {
                            triedInvalidCode = true
                        } else {
                            codeText = code.displayString
                            onOpenChallenge(code)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = codeText.isNotBlank(),
                ) {
                    Text(if (existingResult == null) "Play this challenge" else "View your result")
                }
            }
        }
    }
}

@Composable
fun FriendChallengeResultScreen(
    result: FriendChallengeResult,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(result.code.attemptId) { mutableStateOf(false) }
    val beatTarget = result.points > result.code.targetPoints
    val tiedTarget = result.points == result.code.targetPoints
    val resultTitle = when {
        result.createdChallenge -> "Challenge ready"
        beatTarget -> "You beat the challenge!"
        tiedTarget -> "It’s a tie!"
        else -> "Challenge complete"
    }
    val shareMessage = if (result.createdChallenge) {
        listOf(
            "I challenge you to EZ Trivia!",
            "Beat ${result.score}/${result.total} — ${formatPoints(result.points)} points — on the exact same questions.",
            "Tap to play: ${FriendChallengeLink.webUrl(result.code)}",
            "Challenge code: ${result.code.displayString}",
        ).joinToString("\n")
    } else {
        listOf(
            "EZ Trivia Friend Challenge — ${result.score}/${result.total}",
            RoundSummary.grid(result.outcomes),
            "${formatPoints(result.points)} points · target ${formatPoints(result.code.targetPoints)}",
            "Play this challenge: ${FriendChallengeLink.webUrl(result.code)}",
            "Challenge code: ${result.code.displayString}",
        ).joinToString("\n")
    }
    val shareHeadline = if (result.createdChallenge) {
        "Can you beat ${formatPoints(result.points)} points?"
    } else {
        "I scored ${result.score}/${result.total} on an EZ Trivia challenge"
    }
    val shareCard = ScoreCardContent(
        title = "Friend Challenge",
        subtitle = if (result.createdChallenge) "Can you beat me?" else "Challenge result",
        headline = "${result.score}/${result.total}",
        outcomes = result.outcomes,
        footnote = if (result.createdChallenge) {
            "${formatPoints(result.points)} point target"
        } else {
            "Target: ${formatPoints(result.code.targetPoints)} points"
        },
        tintArgb = friendChallengeTint.toArgb(),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.size(92.dp),
            shape = CircleShape,
            color = friendChallengeTint.copy(alpha = 0.14f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(
                    if (result.createdChallenge) "↗" else if (beatTarget) "★" else "✓",
                    style = MaterialTheme.typography.headlineLarge,
                    color = friendChallengeTint,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Text(resultTitle, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            "${result.score} / ${result.total}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = friendChallengeTint,
        )
        Text(RoundSummary.grid(result.outcomes), style = MaterialTheme.typography.headlineSmall, maxLines = 1)

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            ResultStat(formatPoints(result.points), "your points")
            if (!result.createdChallenge) {
                ResultStat(formatPoints(result.code.targetPoints), "target")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (result.createdChallenge) "Share this challenge" else "Challenge code",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result.createdChallenge) {
                    Text(
                        "The shared link opens this exact challenge. The code is a fallback.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    result.code.displayString,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("EZ Trivia challenge code", result.code.displayString))
                        copied = true
                    },
                ) {
                    Text(if (copied) "Copied" else "Copy code")
                }
            }
        }

        OutlinedButton(
            onClick = {
                ScoreCardShare.share(
                    context = context,
                    message = shareMessage,
                    headline = shareHeadline,
                    card = shareCard,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (result.createdChallenge) "Challenge a friend" else "Share result")
        }

        Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
            Text("Back to categories")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ResultStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatPoints(points: Int): String = NumberFormat.getIntegerInstance().format(points)
