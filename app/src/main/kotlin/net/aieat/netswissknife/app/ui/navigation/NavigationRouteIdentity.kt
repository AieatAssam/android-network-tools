package net.aieat.netswissknife.app.ui.navigation

/** Returns the base route used by app-chrome selection when a destination declares query args. */
internal fun navigationRouteIdentity(route: String?): String? = route?.substringBefore('?')
