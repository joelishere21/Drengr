package com.drengr.launcher.launcher

import android.app.WallpaperManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.core.domain.PageId
import com.drengr.launcher.feature.applications.ApplicationsPage
import com.drengr.launcher.feature.commandcenter.CommandCenterOverlay
import com.drengr.launcher.feature.home.HomePage
import com.drengr.launcher.feature.media.MediaPage
import com.drengr.launcher.feature.productivity.ProductivityPage
import com.drengr.launcher.feature.settings.SettingsActivity
import com.drengr.launcher.feature.system.SystemPage
import com.drengr.launcher.launcher.components.Dock
import com.drengr.launcher.launcher.components.PageIndicator
import com.drengr.launcher.plugins.ActionResult
import com.drengr.launcher.plugins.LauncherAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Callbacks every page can use without knowing about the pager or overlay. */
class LauncherNavigator(
    val goTo: (PageId) -> Unit,
    val openCommandCenter: () -> Unit,
    val openSettings: () -> Unit,
    val runAction: (LauncherAction) -> Unit,
) {
    /** Runs a registered action by id, e.g. "calendar". Unknown ids are ignored. */
    var runActionById: (String) -> Unit = {}
        internal set
}

@Composable
fun LauncherScreen(
    state: LauncherUiState,
    viewModel: LauncherViewModel,
    homePresses: Flow<Unit>,
) {
    // Wait for persisted settings so the pager opens on the saved page without a jump.
    if (!state.settingsLoaded) {
        Box(Modifier.fillMaxSize())
        return
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val pages = state.pages
    val currentPages by rememberUpdatedState(pages)

    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(state.settings.lastPage).coerceAtLeast(0),
    ) { pages.size }

    var commandCenterOpen by rememberSaveable { mutableStateOf(false) }

    fun goTo(page: PageId) {
        val index = currentPages.indexOf(page)
        if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
    }

    val navigator = remember {
        LauncherNavigator(
            goTo = ::goTo,
            openCommandCenter = { commandCenterOpen = true },
            openSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
            runAction = { action ->
                viewModel.recordAction(action)
                when (val result = action.run(context)) {
                    is ActionResult.Navigate -> goTo(result.page)
                    is ActionResult.Message -> viewModel.showMessage(result.text)
                    ActionResult.Done -> Unit
                }
            },
        ).also { nav -> nav.runActionById = { id -> viewModel.actions[id]?.let(nav.runAction) } }
    }

    // Home button while already home: close overlays, then return to the Home page.
    LaunchedEffect(Unit) {
        homePresses.collect {
            focusManager.clearFocus()
            if (commandCenterOpen) commandCenterOpen = false else goTo(PageId.Home)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Persist the settled page so navigation survives restarts.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { index ->
            currentPages.getOrNull(index)?.let(viewModel::setLastPage)
            focusManager.clearFocus()
        }
    }

    // Let scrolling wallpapers follow the pager, as other launchers do.
    val view = LocalView.current
    LaunchedEffect(pagerState, pages.size) {
        val wallpaper = WallpaperManager.getInstance(context)
        val lastIndex = (pages.size - 1).coerceAtLeast(1)
        wallpaper.setWallpaperOffsetSteps(1f / lastIndex, 1f)
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }.collect { position ->
            val token = view.windowToken ?: return@collect
            wallpaper.setWallpaperOffsets(token, (position / lastIndex).coerceIn(0f, 1f), 0.5f)
        }
    }

    // Back never leaves the launcher; it returns to Home.
    BackHandler(enabled = !commandCenterOpen) { goTo(PageId.Home) }

    CompositionLocalProvider(
        LocalAppInteractions provides viewModel,
        LocalAppMembership provides AppMembership(state.favoriteKeys, state.dockKeys),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // Dimming layer over the system wallpaper (100 % = solid background).
                .background(DrengrTheme.colors.background.copy(alpha = state.settings.wallpaperDim / 100f)),
        ) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 1,
                    key = { pages[it] },
                ) { index ->
                    val isCurrent = pagerState.settledPage == index
                    when (pages[index]) {
                        PageId.Home -> HomePage(state, navigator)
                        PageId.Applications -> ApplicationsPage(
                            state = state,
                            isCurrent = isCurrent,
                            onLayoutChange = viewModel::setAppLayout,
                        )
                        PageId.Productivity -> ProductivityPage(navigator)
                        PageId.System -> SystemPage(navigator)
                        PageId.Media -> MediaPage(state, navigator)
                    }
                }
                PageIndicator(
                    pages = pages,
                    pagerState = pagerState,
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                )
                Dock(
                    apps = state.dock,
                    onOpenApplications = { goTo(PageId.Applications) },
                    onOpenCommandCenter = navigator.openCommandCenter,
                )
            }

            CommandCenterOverlay(
                visible = commandCenterOpen,
                onDismiss = {
                    focusManager.clearFocus()
                    commandCenterOpen = false
                },
                onLaunchApp = { app: AppInfo -> viewModel.launch(app) },
                onRunAction = navigator.runAction,
            )
        }
    }
}
