package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface BudsRoute {
    @Serializable data object Dashboard : BudsRoute
    @Serializable data object Devices : BudsRoute
    @Serializable data object Studio : BudsRoute
    @Serializable data object Battery : BudsRoute
    @Serializable data object Security : BudsRoute
    @Serializable data object Settings : BudsRoute
}
