package net.aieat.netswissknife.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.theme.AppMotion

private const val PAGE_COUNT = 4

/**
 * First-run guided tour: what the app does, how to find a tool for your problem,
 * how the nav/pinning model works, and what permissions to expect and why.
 * Re-openable from Settings → Guide → "Reset welcome guide".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(AppMotion.enter(300)) + slideInVertically(AppMotion.enter(300)) { it / 4 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.onboarding_dont_show_again))
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 360.dp)
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (page) {
                            0 -> WelcomePage()
                            1 -> UseCasesPage()
                            2 -> OrganizationPage()
                            else -> PermissionsPage()
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                PageIndicator(pagerState)
                Spacer(Modifier.height(20.dp))
                NavigationRow(pagerState, scope, onDismiss)
            }
        }
    }
}

@Composable
private fun NavigationRow(
    pagerState: PagerState,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pagerState.currentPage > 0) {
            TextButton(onClick = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }) {
                Text(stringResource(R.string.onboarding_back))
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        if (pagerState.currentPage < PAGE_COUNT - 1) {
            Button(onClick = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }) {
                Text(stringResource(R.string.onboarding_next))
            }
        } else {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.onboarding_get_started))
            }
        }
    }
}

@Composable
private fun PageIndicator(pagerState: PagerState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(PAGE_COUNT) { index ->
            val selected = index == pagerState.currentPage
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(if (selected) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

// ── Page 1: Welcome ─────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage() {
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Hub,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(40.dp)
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onboarding_page1_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_page1_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
}

// ── Page 2: Use cases ────────────────────────────────────────────────────────────

private data class UseCase(val icon: ImageVector, val question: String, val answer: String)

@Composable
private fun UseCasesPage() {
    val useCases = listOf(
        UseCase(Icons.Default.NetworkCheck, stringResource(R.string.onboarding_usecase_diagnostics_q), stringResource(R.string.onboarding_usecase_diagnostics_a)),
        UseCase(Icons.Default.Wifi, stringResource(R.string.onboarding_usecase_wifi_q), stringResource(R.string.onboarding_usecase_wifi_a)),
        UseCase(Icons.Default.Lock, stringResource(R.string.onboarding_usecase_security_q), stringResource(R.string.onboarding_usecase_security_a)),
        UseCase(Icons.Default.Speed, stringResource(R.string.onboarding_usecase_speed_q), stringResource(R.string.onboarding_usecase_speed_a)),
        UseCase(Icons.Default.Language, stringResource(R.string.onboarding_usecase_dns_q), stringResource(R.string.onboarding_usecase_dns_a)),
    )

    Text(
        text = stringResource(R.string.onboarding_page2_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
        useCases.forEach { useCase ->
            UseCaseRow(useCase)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun UseCaseRow(useCase: UseCase) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = useCase.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = useCase.question,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = useCase.answer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Page 3: Organization ─────────────────────────────────────────────────────────

@Composable
private fun OrganizationPage() {
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(32.dp)
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onboarding_page3_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_page3_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
}

// ── Page 4: Permissions & privacy ────────────────────────────────────────────────

@Composable
private fun PermissionsPage() {
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PrivacyTip,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(32.dp)
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onboarding_page4_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.onboarding_page4_permission1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.onboarding_page4_permission2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_page4_privacy),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
}
