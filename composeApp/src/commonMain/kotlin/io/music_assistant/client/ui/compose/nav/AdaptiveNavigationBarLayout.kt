package io.music_assistant.client.ui.compose.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import io.music_assistant.client.nav.MultiBackStack
import io.music_assistant.client.nav.ScreenState
import io.music_assistant.client.ui.fadingEdges
import io.music_assistant.client.utils.WindowClass
import kotlinx.coroutines.launch

/**
 * Shows a [NavigationBar] based on [navigationItems] on tall, narrow windows and a
 * [NavigationRail] instead on wide ones (see [WindowClass.isWide]).
 */
@Composable
fun AdaptiveNavigationBarLayout(
    navigationItems: List<NavigationItem>,
    trailingItem: NavigationItem? = null,
    showNavigation: Boolean = true,
    drawInitiallyOpen: Boolean = false,
    navigationBarHeight: Dp = 64.dp,
    maxNavigationBarItems: Int = 2,
    menuLabel: String,
    content: @Composable BoxScope.(contentPadding: PaddingValues, drawerOpen: Boolean) -> Unit,
    ) {
    if (WindowClass.isWide()) {
        Row(Modifier.fillMaxWidth()) {
            if (showNavigation) {
                WideNavigationRail(
                    state = rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Expanded),
                ) {
                    navigationItems.forEach {
                        WideNavigationRailItem(
                            railExpanded = true,
                            selected = it.selected,
                            onClick = it.onClick,
                            icon = {
                                Icon(
                                    it.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            label = {
                                Text(it.label)
                            },
                        )
                    }
                }
            }

            Box {
                content(PaddingValues(), false)
            }
        }
    } else {
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(
            initialValue = if (drawInitiallyOpen) {
                DrawerValue.Open
            } else {
                DrawerValue.Closed
            },
        )

        BackHandler(enabled = drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.testTag(NavigationDrawerSemantics.TAG)) {
                    navigationItems.drop(maxNavigationBarItems).forEach {
                        NavigationDrawerItem(
                            label = {
                                Text(it.label)
                            },
                            icon = {
                                Icon(
                                    it.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    it.onClick()
                                }
                            },
                        )
                    }
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Reserve the real system navigation-bar inset so the chrome reflows when it is
                // shown/hidden (edge-to-edge). Zero when hidden or on devices without a bottom bar.
                val bottomInset = WindowInsets.navigationBars
                    .only(WindowInsetsSides.Bottom)
                    .asPaddingValues()
                    .calculateBottomPadding()

                content(
                    if (showNavigation) {
                        PaddingValues(bottom = navigationBarHeight + bottomInset)
                    } else {
                        PaddingValues()
                    },
                    drawerState.isOpen,
                )

                if (showNavigation) {
                    // Two concerns are split so icon placement doesn't depend on M3's inset handling:
                    //  - the wrapper Box (navigationBarHeight + bottomInset tall) paints the bar background
                    //    down behind the system nav / home indicator, so no white bleeds through edge-to-edge;
                    //  - the transparent NavigationBar is pinned to the top at exactly navigationBarHeight
                    //    (M3's intrinsic min is ~80dp, so the explicit height is required to go smaller), with
                    //    zeroed windowInsets so its icons stay centered in that height instead of being pushed
                    //    up over a lopsided empty band (which showed as an iOS gap / Android 3-button crimp).
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(navigationBarHeight + bottomInset)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        NavigationBar(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .height(navigationBarHeight)
                                .testTag(NavigationBarSemantics.TAG),
                            containerColor = Color.Transparent,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        ) {
                            val menuItem = NavigationItem(
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.open() }
                                },
                                icon = Icons.Default.Menu,
                                label = menuLabel,
                            )

                            val barItems = buildList {
                                add(menuItem)
                                addAll(navigationItems.take(maxNavigationBarItems))
                                if (trailingItem != null) {
                                    add(trailingItem)
                                }
                            }

                            barItems.forEach {
                                NavigationBarItem(it)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavigationBarItem(item: NavigationItem) {
    NavigationBarItem(
        modifier = Modifier.clearAndSetSemantics {
            role = Role.Tab
            selected = item.selected
            contentDescription = item.contentDescription ?: item.label
        },
        selected = item.selected,
        onClick = item.onClick,
        icon = {
            Icon(item.icon, contentDescription = null)
        },
        label = {
            val modifier = if (item.marqueeLabel) {
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .fadingEdges(12.dp)
                    .basicMarquee()
            } else {
                Modifier
            }

            Text(
                item.label,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = modifier.fillMaxWidth(),
            )
        },
    )
}

data class NavigationItem(
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector,
    val label: String,
    val contentDescription: String? = null,
    val marqueeLabel: Boolean = false,
)

fun <T : NavKey> MultiBackStack<T>.createNavigationItem(
    backStack: Int,
    icon: ImageVector,
    label: String,
    screenState: ScreenState? = null,
): NavigationItem {
    return NavigationItem(
        selected = currentBackStack == backStack,
        onClick = {
            if (this.currentBackStack == backStack) {
                resetCurrentBackStack(screenState)
            } else {
                currentBackStack = backStack
            }
        },
        icon = icon,
        label = label,
    )
}

object NavigationBarSemantics {
    const val TAG = "NavBar"
}

object NavigationDrawerSemantics {
    const val TAG = "NavDrawer"
}

@Preview
@Composable
fun PreviewAdaptiveNavigationBarLayout(drawerOpen: Boolean = false) {
    AdaptiveNavigationBarLayout(
        navigationItems = listOf(
            NavigationItem(
                selected = true,
                onClick = {},
                icon = Icons.Default.Home,
                label = "Home",
            ),
            NavigationItem(
                selected = false,
                onClick = {},
                icon = Icons.Default.Settings,
                label = "Settings",
            ),
        ),
        drawInitiallyOpen = drawerOpen,
        menuLabel = "Menu",
        content = { contentPadding, drawerOpen ->
            Text(
                modifier = Modifier.padding(contentPadding),
                text = "Content",
            )
        },
    )
}

@Preview
@Composable
fun PreviewAdaptiveNavigationBarLayoutDrawerOpen() {
    PreviewAdaptiveNavigationBarLayout(drawerOpen = true)
}

@Preview(
    widthDp = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
    heightDp = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
)
@Composable
fun PreviewAdaptiveNavigationBarLayoutExpanded() {
    PreviewAdaptiveNavigationBarLayout()
}
