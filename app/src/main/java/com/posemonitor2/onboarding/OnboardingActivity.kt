package com.posemonitor2.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.posemonitor2.component.ZoomableVideoPlayer
import com.posemonitor2.ui.theme.PoseMonitor2Theme
import com.posemonitor2.ui.theme.PoseMonitorAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_INTRO

        setContent {
            PoseMonitorAppTheme {
                when (mode) {
                    MODE_FULL_TOUR -> FullTourScreen(onClose = { finish() })
                    else -> IntroOnboardingEntry(
                        hasVideoAsset = hasIntroVideoAsset(),
                        onCompleted = {
                            OnboardingManager(this).markOnboardingComplete()
                            finish()
                        },
                        onSkipped = {
                            OnboardingManager(this).markOnboardingComplete()
                            finish()
                        },
                        onLearnMore = {
                            startActivity(createIntent(this, MODE_FULL_TOUR))
                        },
                    )
                }
            }
        }
    }

    private fun hasIntroVideoAsset(): Boolean {
        return runCatching {
            assets.openFd(INTRO_VIDEO_ASSET).close()
            true
        }.getOrElse { false }
    }

    companion object {
        const val EXTRA_MODE = "extra_onboarding_mode"
        const val MODE_INTRO = "intro"
        const val MODE_FULL_TOUR = "full_tour"
        private const val INTRO_VIDEO_ASSET = "videos/onboarding_intro_30s.mp4"

        fun createIntent(context: Context, mode: String): Intent {
            return Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_MODE, mode)
        }
    }
}

@Composable
private fun IntroOnboardingEntry(
    hasVideoAsset: Boolean,
    onCompleted: () -> Unit,
    onSkipped: () -> Unit,
    onLearnMore: () -> Unit,
) {
    if (hasVideoAsset) {
        OnboardingIntroVideoScreen(
            onCompleted = onCompleted,
            onSkipped = onSkipped,
            onLearnMore = onLearnMore,
        )
    } else {
        OnboardingCarouselScreen(
            onCompleted = onCompleted,
            onSkipped = onSkipped,
            onLearnMore = onLearnMore,
        )
    }
}

@Composable
private fun OnboardingIntroVideoScreen(
    onCompleted: () -> Unit,
    onSkipped: () -> Unit,
    onLearnMore: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(30_000)
        onCompleted()
    }

    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            ZoomableVideoPlayer(
                videoAssetPath = "onboarding_intro_30s.mp4",
                assetBaseDir = "videos",
                useController = false,
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSkipped,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Skip")
                }

                Button(
                    onClick = {
                        // Replay intro from the beginning.
                        context.startActivity(
                            OnboardingActivity.createIntent(context, OnboardingActivity.MODE_INTRO),
                        )
                        (context as? android.app.Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x99E8622E)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Learn More")
                }
            }

            Button(
                onClick = onLearnMore,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x99FFFFFF)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("View Full App Tour", color = Color.Black)
            }
        }
    }
}

private data class IntroSlide(
    val title: String,
    val subtitle: String,
    val bullets: List<String>,
)

@Composable
private fun OnboardingCarouselScreen(
    onCompleted: () -> Unit,
    onSkipped: () -> Unit,
    onLearnMore: () -> Unit,
) {
    // ... slides ...
    val slides = remember {
        listOf(
            IntroSlide(
                title = "Welcome",
                subtitle = "Choose your exercise from the library and start guided correction.",
                bullets = listOf(
                    "Works offline after install",
                    "Front and back camera supported",
                    "Past sessions in Profile -> Progress",
                ),
            ),
            IntroSlide(
                title = "Phone Placement",
                subtitle = "Place phone 2m away and 10-20cm from the floor.",
                bullets = listOf(
                    "Keep full body visible head to feet",
                    "Too close cuts feet, too far lowers accuracy",
                    "Use portrait for front exercises and landscape for side exercises",
                ),
            ),
            IntroSlide(
                title = "Clothing and Safety",
                subtitle = "Wear fitted or stretchable clothes for better detection.",
                bullets = listOf(
                    "Avoid baggy clothes",
                    "Keep 2m clear space and use a non-slip mat",
                    "Stop if you feel pain and consult a doctor if injured",
                ),
            ),
            IntroSlide(
                title = "Skeleton Feedback",
                subtitle = "Green means correct form. Red means adjust posture.",
                bullets = listOf(
                    "Hold pose until timer completes",
                    "Auto-advance after successful hold",
                    "Watch correction text and voice guidance",
                ),
            ),
            IntroSlide(
                title = "Session Controls",
                subtitle = "Use monitoring controls to personalize each session.",
                bullets = listOf(
                    "Settings gear on top-right",
                    "Flip camera, mute feedback, and open tutorial",
                    "Adjust reps and hold duration before starting",
                ),
            ),
            IntroSlide(
                title = "Get Started",
                subtitle = "Complete sessions and review progress trends after every workout.",
                bullets = listOf(
                    "Skip anytime and continue later",
                    "Use Help to replay this walkthrough",
                    "Always stop if you feel pain",
                ),
            ),
        )
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == slides.lastIndex
    var dontShowAgain by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onSkipped,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Skip", color = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                IntroSlideCard(slide = slides[page])
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(slides.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(if (index == pagerState.currentPage) 22.dp else 8.dp)
                            .height(8.dp)
                            .background(
                                if (index == pagerState.currentPage) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(100.dp),
                            ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLastPage) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                    Text(text = "Don't show again", color = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.height(8.dp))
                
                // Full App Tour button on last page
                OutlinedButton(
                    onClick = onLearnMore,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Help, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.width(8.dp))
                    Text("View Full App Tour", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    if (isLastPage) {
                        onCompleted()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E)),
            ) {
                Text(if (isLastPage) "Get Started" else "Next", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun IntroSlideCard(slide: IntroSlide) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text(slide.title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            Text(slide.subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
            Spacer(Modifier.height(18.dp))
            slide.bullets.forEach { bullet ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 10.dp)) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(text = bullet, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun FullTourScreen(onClose: () -> Unit) {
    data class TourSlide(
        val title: String,
        val subtitle: String,
        val bullets: List<String>,
    )

    val slides = remember {
        listOf(
            TourSlide(
                title = "Home Screen",
                subtitle = "Where users pick exercises and start sessions.",
                bullets = listOf(
                    "Browse categories and recommended workouts",
                    "Open Help to replay onboarding or open this full tour",
                    "Use Profile/Progress to review your session history",
                ),
            ),
            TourSlide(
                title = "Exercise Detail",
                subtitle = "Pre-session setup before camera monitoring starts.",
                bullets = listOf(
                    "Confirm phone placement and required orientation",
                    "Set hold duration: Default, 8, 10, 21, or Custom",
                    "Play tutorial preview and then start your rep target",
                ),
            ),
            TourSlide(
                title = "Monitor Screen",
                subtitle = "Live correction and hold progression.",
                bullets = listOf(
                    "Skeleton overlay turns green when posture is correct",
                    "Top target card shows the current expected state",
                    "Hold timer and rep progress update in real time",
                ),
            ),
            TourSlide(
                title = "Settings Controls",
                subtitle = "Adjust session behavior while monitoring.",
                bullets = listOf(
                    "Flip camera and toggle voice feedback",
                    "Use zoom controls for framing adjustments",
                    "Open Help at any time without leaving the session",
                ),
            ),
            TourSlide(
                title = "Progress Tracking",
                subtitle = "Review consistency and quality over time.",
                bullets = listOf(
                    "Rep accuracy history is recorded per session",
                    "Track streaks and completion trends",
                    "Use this data to tune reps and hold duration",
                ),
            ),
            TourSlide(
                title = "Safety & Quality",
                subtitle = "Best practices for reliable tracking.",
                bullets = listOf(
                    "Keep full body visible with ~2m distance",
                    "Use fitted clothing for stable keypoint detection",
                    "Stop if painful and treat this as guidance, not diagnosis",
                ),
            ),
        )
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isFirst = pagerState.currentPage == 0
    val isLast = pagerState.currentPage == slides.lastIndex

    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A3D3F))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Full App Tour", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onClose,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E))
                ) { Text("Close", color = Color.White) }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { page ->
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                    ) {
                        Text(
                            text = slides[page].title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = slides[page].subtitle,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 21.sp,
                        )

                        Spacer(Modifier.height(16.dp))

                        slides[page].bullets.forEach { bullet ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(bottom = 12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = bullet,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(slides.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(if (index == pagerState.currentPage) 22.dp else 8.dp)
                            .height(8.dp)
                            .background(
                                if (index == pagerState.currentPage) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(100.dp),
                            ),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (!isFirst) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    enabled = !isFirst,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Back")
                }

                Button(
                    onClick = {
                        if (isLast) onClose() else scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E)),
                ) {
                    Text(if (isLast) "Finish" else "Next")
                }
            }
        }
    }
}
