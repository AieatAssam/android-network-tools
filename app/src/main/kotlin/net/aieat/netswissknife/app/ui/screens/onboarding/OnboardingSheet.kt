package net.aieat.netswissknife.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.app.ui.components.AppModalBottomSheet
import net.aieat.netswissknife.app.ui.navigation.AppNavigationViewModel.Companion.MAX_PINNED
import net.aieat.netswissknife.app.ui.navigation.MORE_TOOL_SECTIONS
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
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

    AppModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(AppMotion.enter(300)) + slideInVertically(AppMotion.enter(300)) { it / 4 },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.onboarding_dont_show_again))
                    }
                }

                // Pages differ in height; the sheet eases to each page's height instead of jumping.
                HorizontalPager(
                    state = pagerState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize(AppMotion.enter())
                            .heightIn(min = 360.dp),
                ) { page ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
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
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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
    val pagePosition = stringResource(R.string.onboarding_page_position, pagerState.currentPage + 1, PAGE_COUNT)
    Row(
        modifier =
            Modifier.fillMaxWidth().semantics {
                contentDescription = pagePosition
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(PAGE_COUNT) { index ->
            val selected = index == pagerState.currentPage
            Box(
                modifier =
                    Modifier
                        .padding(4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
            )
        }
    }
}

// ── Page 1: Welcome ─────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage() {
    Spacer(Modifier.height(8.dp))
    // Gradient ring around a solid disc, so the white icon never sits on the gradient's light end.
    Box(
        modifier =
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                    ),
                ).padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Hub,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onboarding_page1_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_page1_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
}

// ── Page 2: Use cases ────────────────────────────────────────────────────────────

@Composable
private fun UseCasesPage() {
    val toolsByRoute = NavRoutes.allTools.associateBy { it.route }

    Text(
        text = stringResource(R.string.onboarding_page2_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
        MORE_TOOL_SECTIONS.forEach { section ->
            val toolNames = section.routes.mapNotNull { toolsByRoute[it] }.map { stringResource(it.labelRes) }
            SectionCard(title = stringResource(section.labelRes), tools = toolNames)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    tools: List<String>,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = tools.joinToString(separator = ", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Page 3: Organization ─────────────────────────────────────────────────────────

@Composable
private fun OrganizationPage() {
    Spacer(Modifier.height(8.dp))
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(32.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onboarding_page3_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = pluralStringResource(R.plurals.onboarding_page3_body, MAX_PINNED, MAX_PINNED),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
}

// ── Page 4: Permissions & privacy ────────────────────────────────────────────────

@Composable
private fun PermissionsPage() {
    Spacer(Modifier.height(8.dp))
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PrivacyTip,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(32.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onboarding_page4_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.onboarding_page4_permission1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.onboarding_page4_permission2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_page4_privacy),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
}
