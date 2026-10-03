package io.music_assistant.client.ui.theme

import home.theme.HomeThemes
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeThemeTest {
    @Test
    fun noClaimNoChoiceIsTheDefaultFollowingTheDevice() {
        assertEquals(
            HomeEffective(HomeThemes.DEFAULT, HomeMode.Automatic, HomeSource.Default),
            resolveHomeTheme(HomeThemeState()),
        )
    }

    @Test
    fun theClaimAppliesWithoutAnInAppChoice() {
        val state = HomeThemeState(claim = HomeThemeClaim("dusk", "dark"))
        assertEquals(HomeEffective("dusk", HomeMode.Dark, HomeSource.Home), resolveHomeTheme(state))
    }

    @Test
    fun anInAppChoiceWinsWhileItsBasisIsTheCurrentOverride() {
        val state = HomeThemeState(
            claim = HomeThemeClaim("dusk", "dark", override = "follow"),
            choice = HomeThemeChoice("mint", "light", basis = "follow"),
        )
        assertEquals(HomeEffective("mint", HomeMode.Light, HomeSource.App), resolveHomeTheme(state))
    }

    @Test
    fun aNewPerAppOverrideInAuthentikBeatsAnOlderInAppChoice() {
        val state = HomeThemeState(
            claim = HomeThemeClaim("ochre", "dark", override = "ochre/dark"),
            choice = HomeThemeChoice("mint", "light", basis = "follow"),
        )
        assertEquals(HomeEffective("ochre", HomeMode.Dark, HomeSource.Home), resolveHomeTheme(state))
    }

    @Test
    fun unknownThemesAndModesFallBackToTheDefaultAndAutomatic() {
        val state = HomeThemeState(claim = HomeThemeClaim("not-a-theme", "sepia"))
        assertEquals(
            HomeEffective(HomeThemes.DEFAULT, HomeMode.Automatic, HomeSource.Home),
            resolveHomeTheme(state),
        )
    }

    @Test
    fun theServerPayloadDecodes() {
        val json = """{"user_id":"u1","claim":{"theme":"klein","mode":"automatic","override":"follow"},"choice":null}"""
        val state = myJson.decodeFromString(HomeThemeState.serializer(), json)
        assertEquals(HomeEffective("klein", HomeMode.Automatic, HomeSource.Home), resolveHomeTheme(state))
    }

    @Test
    fun everyThemeHasBothModesAndSlateKeepsTheHw48Palette() {
        assertEquals(22, HomeThemes.all.size)
        val slate = HomeThemes.byId("slate")
        assertEquals(0xFF466A77, slate.light.primary)
        assertEquals(0xFFEAF0F0, slate.light.background)
        assertEquals(0xFF8DB0BD, slate.dark.primary)
        assertEquals(slate.light.primary, slate.light.tertiary) // favourite heart = primary
    }
}
