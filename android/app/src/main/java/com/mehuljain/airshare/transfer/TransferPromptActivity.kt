package com.mehuljain.airshare.transfer

import android.graphics.Bitmap
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mehuljain.airshare.R
import kotlinx.coroutines.delay

class TransferPromptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by TransferCoordinator.uiState.collectAsState()

            LaunchedEffect(state) {
                if (state is TransferUiState.Completed) {
                    delay(1600)
                    finish()
                } else if (state is TransferUiState.Idle) {
                    finish()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        TransferCoordinator.decline()
                        finish()
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* consume inside card */ },
                ) {
                    AirSharePromptCard(
                        state = state,
                        onAccept = { TransferCoordinator.accept() },
                        onDecline = {
                            TransferCoordinator.decline()
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun AirSharePromptCard(
    state: TransferUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val req = when (state) {
        is TransferUiState.Prompt -> state.request
        is TransferUiState.Receiving -> state.request
        is TransferUiState.Completed -> state.request
        TransferUiState.Idle -> null
    } ?: return

    val thumbnail: Bitmap? = when (state) {
        is TransferUiState.Prompt -> state.thumbnail
        is TransferUiState.Receiving -> state.thumbnail
        is TransferUiState.Completed -> state.thumbnail
        TransferUiState.Idle -> null
    }

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF161618))
            .border(1.2.dp, Color(0xFF2E2E32), RoundedCornerShape(26.dp))
            .padding(16.dp),
    ) {
        // Top Row: [Icon + Texts] + [Thumbnail]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_round),
                contentDescription = "AirShare",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AirShare",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = req.promptSubtitle,
                    color = Color(0xFFA1A1A6),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(12.dp))

            // Thumbnail container
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF26262A))
                    .border(1.dp, Color(0xFF38383E), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = if (req.parsedInfo.isPhoto) "📷" else "📄",
                        fontSize = 28.sp,
                    )
                }
            }
        }

        // Bottom Actions / Progress Section
        AnimatedVisibility(
            visible = state is TransferUiState.Prompt,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onDecline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF323236),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Text("Decline", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A84FF),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Text("Accept", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = state is TransferUiState.Receiving,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val rec = state as? TransferUiState.Receiving ?: return@AnimatedVisibility
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            ) {
                if (rec.progress >= 0f) {
                    val animatedProgress by animateFloatAsState(targetValue = rec.progress, label = "progress")
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF0A84FF),
                        trackColor = Color(0xFF2C2C30),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF0A84FF),
                        trackColor = Color(0xFF2C2C30),
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val percentStr = if (rec.progress >= 0f) "Receiving ${(rec.progress * 100).toInt()}%" else "Receiving…"
                    Text(
                        text = percentStr,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val readStr = Formatter.formatShortFileSize(context, rec.bytesRead)
                    val totalStr = if (rec.totalBytes > 0) " / " + Formatter.formatShortFileSize(context, rec.totalBytes) else ""
                    Text(
                        text = "$readStr$totalStr",
                        color = Color(0xFFA1A1A6),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state is TransferUiState.Completed,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val comp = state as? TransferUiState.Completed ?: return@AnimatedVisibility
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dest = if (req.parsedInfo.isPhoto) "Saved to Gallery" else "Saved to Documents"
                Text(
                    text = if (comp.success) "✓ ${req.parsedInfo.contentDisplayName}" else "✕ Transfer ${comp.message}",
                    color = if (comp.success) Color(0xFF30D158) else Color(0xFFFF453A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (comp.success) {
                    Text(
                        text = dest,
                        color = Color(0xFFA1A1A6),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
