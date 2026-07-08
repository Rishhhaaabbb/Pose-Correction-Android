package com.posemonitor2.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.posemonitor2.component.ZoomableVideoPlayer
import com.posemonitor2.questionnaire.QBgGradient
import com.posemonitor2.questionnaire.QCardBg
import com.posemonitor2.questionnaire.QDivider
import com.posemonitor2.questionnaire.QPurple
import com.posemonitor2.questionnaire.QPurpleLight
import com.posemonitor2.questionnaire.QSubtext
import com.posemonitor2.questionnaire.QText
import com.posemonitor2.ui.theme.PoseMonitorAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun hasIntroVideoAsset(): Boolean = runCatching {
        assets.openFd(INTRO_VIDEO_ASSET).close(); true
    }.getOrElse { false }

    companion object {
        const val EXTRA_MODE = "extra_onboarding_mode"
        const val MODE_INTRO = "intro"
        const val MODE_FULL_TOUR = "full_tour"
        private const val INTRO_VIDEO_ASSET = "videos/onboarding_intro_30s.mp4"

        fun createIntent(context: Context, mode: String): Intent =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_MODE, mode)
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
        OnboardingIntroVideoScreen(onCompleted = onCompleted, onSkipped = onSkipped, onLearnMore = onLearnMore)
    } else {
        OnboardingCarouselScreen(onCompleted = onCompleted, onSkipped = onSkipped, onLearnMore = onLearnMore)
    }
}

@Composable
private fun OnboardingIntroVideoScreen(
    onCompleted: () -> Unit,
    onSkipped: () -> Unit,
    onLearnMore: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { delay(30_000); onCompleted() }
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            ZoomableVideoPlayer(
                videoAssetPath = "onboarding_intro_30s.mp4",
                assetBaseDir = "videos",
                useController = false,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onSkipped, colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)), shape = RoundedCornerShape(12.dp)) { Text("Skip") }
                Button(
                    onClick = {
                        context.startActivity(OnboardingActivity.createIntent(context, OnboardingActivity.MODE_INTRO))
                        (context as? android.app.Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QPurple.copy(alpha = 0.7f)), shape = RoundedCornerShape(12.dp),
                ) { Text("Learn More") }
            }
            Button(
                onClick = onLearnMore,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x99FFFFFF)), shape = RoundedCornerShape(12.dp),
            ) { Text("View Full App Tour", color = Color.Black) }
        }
    }
}

private data class IntroSlide(val emoji: String, val title: String, val subtitle: String, val bullets: List<String>)

@Composable
private fun OnboardingCarouselScreen(
    onCompleted: () -> Unit,
    onSkipped: () -> Unit,
    onLearnMore: () -> Unit,
) {
    val slides = remember {
        listOf(
            IntroSlide(
                emoji = "🎯", title = "Welcome to PoseMonitor",
                subtitle = "Choose an exercise and get live AI-powered form correction.",
                bullets = listOf("Works offline after install", "Front & back camera supported", "Review past sessions in Progress tab"),
            ),
            IntroSlide(
                emoji = "📱", title = "Phone Placement",
                subtitle = "Place your phone 2m away, 10–20cm from the floor.",
                bullets = listOf("Keep full body visible head to feet", "Portrait for front exercises", "Landscape for side exercises"),
            ),
            IntroSlide(
                emoji = "👗", title = "What to Wear",
                subtitle = "Fitted or stretchable clothes help the AI detect your joints clearly.",
                bullets = listOf("Avoid baggy clothing", "Use a non-slip mat", "Keep 2m of clear space around you"),
            ),
            IntroSlide(
                emoji = "🟢", title = "Skeleton Feedback",
                subtitle = "Green skeleton = correct form. Red = adjust your posture.",
                bullets = listOf("Hold pose until the timer completes", "Voice guidance tells you exactly what to adjust", "Auto-advance after a successful hold"),
            ),
            IntroSlide(
                emoji = "⚙️", title = "Session Controls",
                subtitle = "Customise each session from the top-right settings panel.",
                bullets = listOf("Flip camera front/back", "Mute voice feedback", "Adjust reps and hold duration"),
            ),
            IntroSlide(
                emoji = "🚀", title = "You're Ready!",
                subtitle = "Complete sessions and track your improvement over time.",
                bullets = listOf("Tap Help anytime to replay this guide", "Progress tab shows accuracy trends", "Stop immediately if you feel pain"),
            ),
        )
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == slides.lastIndex

    Box(modifier = Modifier.fillMaxSize().background(QBgGradient)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                CarouselSlideCard(slide = slides[page])
            }

            Spacer(Modifier.height(16.dp))

            // Dot indicator
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(slides.size) { index ->
                    val isActive = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (isActive) 20.dp else 8.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isActive) QPurple else QDivider),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLastPage) {
                TextButton(
                    onClick = onLearnMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View Full App Tour", color = QPurple, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(4.dp))
            }

            // Back / Skip / Next row — raised with navigationBarsPadding so it clears phone nav buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back button (hidden on first page)
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, QPurple),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = QPurple),
                    ) { Text("Back", fontWeight = FontWeight.SemiBold) }
                } else if (!isLastPage) {
                    // Skip on first page
                    TextButton(
                        onClick = onSkipped,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Skip", color = QSubtext)
                    }
                }

                Button(
                    onClick = {
                        if (isLastPage) onCompleted()
                        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.weight(if (!isLastPage && pagerState.currentPage == 0) 2f else 1f).height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QPurple),
                ) {
                    Text(
                        if (isLastPage) "GET STARTED" else "NEXT",
                        fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CarouselSlideCard(slide: IntroSlide) {
    Card(
        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = QCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(slide.emoji, fontSize = 56.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                text = slide.title,
                fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                color = QText, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = slide.subtitle,
                fontSize = 14.sp, color = QSubtext,
                lineHeight = 21.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            slide.bullets.forEach { bullet ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Box(
                        modifier = Modifier.size(26.dp).clip(CircleShape).background(QPurpleLight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = QPurple, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(bullet, fontSize = 14.sp, color = QText, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun FullTourScreen(onClose: () -> Unit) {
    data class TourSlide(val emoji: String, val title: String, val subtitle: String, val bullets: List<String>)

    val slides = remember {
        listOf(
            TourSlide(
                emoji = "🏠", title = "Home Screen",
                subtitle = "Where users pick exercises and start sessions.",
                bullets = listOf("Browse categories and recommended workouts", "Open Help to replay onboarding or open this full tour", "Use Profile/Progress to review your session history"),
            ),
            TourSlide(
                emoji = "📋", title = "Exercise Detail",
                subtitle = "Pre-session setup before camera monitoring starts.",
                bullets = listOf("Confirm phone placement and required orientation", "Set hold duration: Default, 8, 10, 21, or Custom", "Play tutorial preview and then start your rep target"),
            ),
            TourSlide(
                emoji = "📸", title = "Monitor Screen",
                subtitle = "Live correction and hold progression.",
                bullets = listOf("Skeleton overlay turns green when posture is correct", "Top target card shows the current expected state", "Hold timer and rep progress update in real time"),
            ),
            TourSlide(
                emoji = "⚙️", title = "Settings Panel",
                subtitle = "Adjust session behaviour while monitoring.",
                bullets = listOf("Flip camera and toggle voice feedback", "Use zoom controls for framing adjustments", "Open Help at any time without leaving the session"),
            ),
            TourSlide(
                emoji = "📈", title = "Progress Screen",
                subtitle = "Review consistency and quality over time.",
                bullets = listOf("Rep accuracy history is recorded per session", "Track streaks and completion trends", "Use this data to tune reps and hold duration"),
            ),
            TourSlide(
                emoji = "🛡️", title = "Safety & Quality",
                subtitle = "Best practices for reliable tracking.",
                bullets = listOf("Keep full body visible with ~2m distance", "Use fitted clothing for stable keypoint detection", "Stop if painful and treat this as guidance, not diagnosis"),
            ),
        )
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isFirst = pagerState.currentPage == 0
    val isLast  = pagerState.currentPage == slides.lastIndex

    Column(modifier = Modifier.fillMaxSize().background(QBgGradient)) {
        // Header bar
        Row(
            modifier = Modifier.fillMaxWidth().background(QPurple).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Full App Tour", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
        ) { page ->
            val slide = slides[page]
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = QCardBg),
                elevation = CardDefaults.cardElevation(3.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(slide.emoji, fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(slide.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = QText, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(slide.subtitle, fontSize = 14.sp, color = QSubtext, lineHeight = 21.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    slide.bullets.forEach { bullet ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(26.dp).clip(CircleShape).background(QPurpleLight),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = QPurple, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(bullet, fontSize = 14.sp, color = QText, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }

        // Dot indicator
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
            repeat(slides.size) { index ->
                val isActive = index == pagerState.currentPage
                Box(
                    modifier = Modifier.padding(horizontal = 3.dp)
                        .width(if (isActive) 20.dp else 8.dp).height(8.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (isActive) QPurple else QDivider),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Back / Next buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { if (!isFirst) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                enabled = !isFirst,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(25.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, QPurple),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = QPurple),
            ) { Text("Back", fontWeight = FontWeight.SemiBold) }

            Button(
                onClick = {
                    if (isLast) onClose()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QPurple),
            ) { Text(if (isLast) "FINISH" else "NEXT", fontWeight = FontWeight.Bold, color = Color.White) }
        }
    }
}
