package net.aieat.netswissknife.app.ui.navigation

import androidx.navigation.NavHostController

/** Returns the base route used by app-chrome selection when a destination declares query args. */
internal fun navigationRouteIdentity(route: String?): String? = route?.substringBefore('?')

/**
 * Pops a handoff restored above [route]'s tool, so the tool chosen from Home, More or the nav bar is shown.
 * A restored tool stack can carry another tool opened from it on top; this pops to the topmost entry of
 * the requested tool, and does nothing when that tool is already on top or is not on the stack.
 */
internal fun NavHostController.popToRequestedTool(route: String) {
    val requested = navigationRouteIdentity(route)
    if (navigationRouteIdentity(currentDestination?.route) == requested) return
    val tool = graph.firstOrNull { navigationRouteIdentity(it.route) == requested } ?: return
    popBackStack(tool.id, inclusive = false)
}
