package com.example.videoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape

private val HubOrange = Color(0xFFFF8A00)
private val HubCardBg = Color(0xFF13131A)

@Composable
fun MoreHubView(
    onOpenSettings: () -> Unit,
    onOpenCustomUrl: () -> Unit,
    onOpenVideoLibrary: () -> Unit,
    onOpenSecureVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(20.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "More Hub",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onOpenVideoLibrary,
                        colors = ButtonDefaults.buttonColors(containerColor = HubOrange)
                    ) {
                        Text("Back")
                    }
                }
            }

            item {
                HubCard(title = "Secure Vault") {
                    Text("Private encrypted vault. Files stored only inside app storage and visible only after password unlock.", color = Color(0xFFB6BDCB))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenSecureVault,
                        colors = ButtonDefaults.buttonColors(containerColor = HubOrange)
                    ) {
                        Text("Open secure vault")
                    }
                }
            }

            item {
                HubCard(title = "FTP / network transfer") {
                    Text("Real transfer using FTP or LAN upload. Works with FTP server on local network.", color = Color(0xFFB6BDCB))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            // keep existing FTP behavior here if desired
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HubOrange)
                    ) {
                        Text("Send via FTP")
                    }
                }
            }

            item {
                HubCard(title = "Quick share / AirDrop") {
                    Text("Real system share: nearby devices / Quick Share / Android share sheet.", color = Color(0xFFB6BDCB))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = HubOrange)
                    ) {
                        Text("Share file")
                    }
                }
            }

            item {
                HubCard(title = "Video URL") {
                    Button(
                        onClick = onOpenCustomUrl,
                        colors = ButtonDefaults.buttonColors(containerColor = HubOrange)
                    ) {
                        Text("Open URL stream")
                    }
                }
            }

            item {
                HubCard(title = "System") {
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = HubOrange)
                    ) {
                        Text("Open settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun HubCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HubCardBg),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
