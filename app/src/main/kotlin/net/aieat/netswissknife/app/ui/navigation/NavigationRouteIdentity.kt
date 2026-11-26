package net.aieat.netswissknife.app.ui.navigation

import androidx.navigation.NavHostController

/** Returns the base route used by app-chrome selection when a destination declares query args. */
internal fun navigationRouteIdentity(route: String?): String? = route?.substringBefore('?')

/**
 * Index in [backStackRoutes] of the entry to show once [requestedRoute] was chosen as a top-level tool:
 * the topmost entry of that tool. A restored tool stack can carry another tool opened from it on top,
 * and choosing the tool itself must show the tool, not that handoff. Null when the top entry is already
 * the requested tool or the tool is not on the stack.
 */
internal fun toolLandingIndex(
    backStackRoutes: List<String?>,
    requestedRoute: String,
): Int? {
    val requested = navigationRouteIdentity(requestedRoute)
    if (navigationRouteIdentity(backStackRoutes.lastOrNull()) == requested) return null
    return backStackRoutes.indexOfLast { navigationRouteIdentity(it) == requested }.takeIf { it >= 0 }
}

/** Pops a handoff restored above [route]'s tool, so the tool chosen from Home, More or the nav bar is shown. */
internal fun NavHostController.popToRequestedTool(route: String) {
    val stack = currentBackStack.value
    toolLandingIndex(stack.map { it.destination.route }, route)?.let { index ->
        popBackStack(stack[index].destination.id, inclusive = false)
    }
}
