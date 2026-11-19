package com.enhikyy.girassol.plugins

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRulesTest {
    @Test
    fun acceptsOnlySafePluginNamesAndExtensions() {
        assertTrue(PluginRules.isValidName("research_agent"))
        assertTrue(PluginRules.isValidName("Plugin7"))
        assertFalse(PluginRules.isValidName("../escape"))
        assertFalse(PluginRules.isValidName("7startsWithNumber"))
        assertFalse(PluginRules.isValidName("name-with-dash"))
        assertTrue(PluginRules.isValidExtension("lua"))
        assertTrue(PluginRules.isValidExtension("js"))
        assertFalse(PluginRules.isValidExtension("kt"))
    }

    @Test
    fun enforcesSourceSizeAndNullByteBoundary() {
        assertTrue(PluginRules.isValidSource("x".repeat(PluginRules.MAX_SOURCE_CHARS)))
        assertFalse(PluginRules.isValidSource("x".repeat(PluginRules.MAX_SOURCE_CHARS + 1)))
        assertFalse(PluginRules.isValidSource("valid\u0000invalid"))
    }

    @Test
    fun acceptsOnlyCanonicalHistoryFileNames() {
        assertTrue(PluginRules.isValidVersionFileName("1700000000000-000001-abcdef123456.src"))
        assertFalse(PluginRules.isValidVersionFileName("../1700000000000-000001-abcdef123456.src"))
        assertFalse(PluginRules.isValidVersionFileName("latest.src"))
    }

    @Test
    fun buildsPluginIdFromValidatedParts() {
        assertTrue(PluginRules.pluginId("agent", "lua") == "agent.lua")
    }
}
