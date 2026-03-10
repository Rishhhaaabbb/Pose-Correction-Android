// app/src/main/java/com/posemonitor2/HomeActivity.kt

package com.posemonitor2

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeScreen()
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "PoseMonitor",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "Real-time exercise pose analysis",
                fontSize = 15.sp,
                color = Color(0xFF9E9E9E),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Generate graph card
            HomeCard(
                title = "Generate Graph",
                description = "Upload an exercise video to create a pose graph",
                buttonLabel = "Choose Video",
                buttonColor = Color(0xFF2196F3),
                onClick = {
                    context.startActivity(
                        Intent(context, GraphGenerationActivity::class.java)
                    )
                }
            )

            // Monitor card
            HomeCard(
                title = "Start Monitoring",
                description = "Use the camera to monitor your exercise form in real time",
                buttonLabel = "Open Camera",
                buttonColor = Color(0xFF4CAF50),
                onClick = {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                    )
                }
            )
        }
    }
}

@Composable
private fun HomeCard(
    title: String,
    description: String,
    buttonLabel: String,
    buttonColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1A1A),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color(0xFF9E9E9E),
                lineHeight = 20.sp,
            )
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            ) {
                Text(text = buttonLabel, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}