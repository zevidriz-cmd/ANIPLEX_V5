package com.aniplex.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aniplex.app.data.update.UpdateInfo
import com.aniplex.app.theme.CrunchyrollOrange
import com.aniplex.app.theme.SurfaceDark

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Float,
    onUpgrade: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isDownloading) onCancel() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F0F14))
                .border(1.dp, Color(0xFF22222A), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "New Update Available",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "A new version of Aniplex (v${updateInfo.versionName}) is available for download.",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )

                if (updateInfo.changelog.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .background(Color(0xFF14141A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF1C1C24), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "What's New:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrunchyrollOrange
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = updateInfo.changelog,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val pct = (downloadProgress * 100).toInt()
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            color = CrunchyrollOrange,
                            trackColor = Color.DarkGray,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Text(
                            text = "Downloading update... $pct%",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel Button
                        var isCancelFocused by remember { mutableStateOf(false) }
                        val cancelScale by animateFloatAsState(if (isCancelFocused) 1.05f else 1.0f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(cancelScale)
                                .onFocusChanged { isCancelFocused = it.isFocused }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCancelFocused) Color(0xFF22222A) else Color.Transparent)
                                .border(1.dp, Color(0xFF2C2C35), RoundedCornerShape(12.dp))
                                .clickable { onCancel() }
                                .focusable()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // Upgrade Button
                        var isUpgradeFocused by remember { mutableStateOf(false) }
                        val upgradeScale by animateFloatAsState(if (isUpgradeFocused) 1.05f else 1.0f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(upgradeScale)
                                .onFocusChanged { isUpgradeFocused = it.isFocused }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isUpgradeFocused) CrunchyrollOrange else SurfaceDark)
                                .border(
                                    width = if (isUpgradeFocused) 2.dp else 1.dp,
                                    color = if (isUpgradeFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onUpgrade() }
                                .focusable()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Upgrade",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
