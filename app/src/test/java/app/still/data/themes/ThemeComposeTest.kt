package app.still.data.themes

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemeComposeTest {
    private val source = javaClass.getResourceAsStream("/themes/consent.tc")!!.bufferedReader().use { it.readText() }
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: Exception) { } }

    @Test fun versionOneRequestsAndOptionalFieldColons() {
        val theme = ThemeCompose.parse(source)
        assertEquals("LightMode", theme.requests.first().id)
        assertEquals("1.0.0", theme.version)
        assertTrue(theme.updatable)
        ThemeCompose.parse(source.replace("id LightMode", "id: LightMode").replace("as string", "as: string").replace("reason \"Follow", "reason: \"Follow"))
        rejected { ThemeCompose.parse(source.replace("compose-version: 1", "compose-version: 2")) }
    }
    @Test fun deniedAndAllowedValuesResolveDeterministically() {
        val value = ThemeCompose.parse(source).light.getValue("primary")
        assertEquals("#286C43", value.resolve(emptyMap()))
        assertEquals("#286C43", value.resolve(mapOf("LightMode" to null)))
        assertEquals("#345D42", value.resolve(mapOf("LightMode" to "dark")))
    }
    @Test fun logicalPrecedenceStrictTypesAndShortCircuit() {
        fun evaluate(text: String, values: Map<String, Any?> = emptyMap()) = ThemeExpression.parse(text).evaluate(values)
        assertEquals(true, evaluate("true || false && false"))
        assertEquals(false, evaluate("false && Missing"))
        assertEquals(true, evaluate("(FontScale ?? 1) > 1.2 && !(ReducedMotion ?? false)", mapOf("FontScale" to 1.3)))
        assertEquals(false, evaluate("1 == \"1\""))
        assertEquals(false, evaluate("null ?? false ?? true"))
        assertEquals("b", evaluate("true ? false ? \"a\" : \"b\" : \"c\""))
        assertEquals(true, evaluate("trueColor == \"green\"", mapOf("trueColor" to "green")))
    }
    @Test fun nullableConditionsAndUnknownIdsRejected() {
        rejected { ThemeExpression.parse("ReducedMotion ? true : false").types(mapOf("ReducedMotion" to "boolean")) }
        rejected { ThemeExpression.parse("FontScale > 1").types(mapOf("FontScale" to "number")) }
        rejected { ThemeExpression.parse("Missing == true").types(emptyMap()) }
        assertEquals(setOf("boolean"), ThemeExpression.parse("(ReducedMotion ?? false) && true").types(mapOf("ReducedMotion" to "boolean")))
    }
    @Test fun allBranchesAndRequestDeclarationsValidated() {
        listOf(
            source.replace("sysLightMode:", "usageHistory:"), source.replace("as string", "as boolean"),
            source.replace("reason \"Follow system appearance\"", "reason \"\""),
            source.replace("id FontScale", "id LightMode"), source.replace("sysFontScale:", "sysLightMode:"),
            source.replace("\"#345D42\" : \"#286C43\"", "\"#345D42\" : \"url(https://evil.invalid)\""),
            source.replace("LightMode == \"dark\" ? \"#345D42\" : \"#286C43\"", "LightMode ?? \"#286C43\""),
            source.replace("LightMode == \"dark\"", "Missing == \"dark\""),
        ).forEach { rejected { ThemeCompose.parse(it) } }
    }
    @Test fun noScriptsAndBoundedParsing() {
        listOf("fetch()", "LightMode.constructor", "a[0]", "new Thing", "process.env", "(()=>true)()", "!".repeat(40) + "true", "(".repeat(40) + "true" + ")".repeat(40), "\"" + "a".repeat(2050) + "\"").forEach { rejected { ThemeExpression.parse(it) } }
        rejected { ThemeCompose.parse(source.replace("title: Example", "title: \"\\u202EExample\"")) }
        rejected { ThemeCompose.parse(source + "\n".repeat(10001)) }
        rejected { ThemeCompose.parse(source.replace("title: Example", "title: One\ntitle: Two")) }
    }
    @Test fun versionComparisonAndConsentIdentity() {
        assertTrue(ThemeCompose.newer("1.10.0", "1.9.99"))
        assertFalse(ThemeCompose.newer("1.0.0", "1.0.0"))
        assertFalse(ThemeCompose.newer("0.99.0", "1.0.0"))
        val request = ThemeCompose.parse(source).requests.first()
        assertNotEquals(CommunityThemeRepository.permissionKey(request), CommunityThemeRepository.permissionKey(request.copy(reason = "Another reason")))
    }
    @Test fun localAndReservedEndpointsAreBlockedWithoutDnsLookups() {
        listOf("http://example.com/theme.tc", "https://user:password@example.com/theme.tc", "https://example.com:8443/t", "https://localhost/t", "https://printer.local/t", "https://example.com/t#fragment").forEach { rejected { ThemeLinkClient.validateUrl(it) } }
        assertEquals("example.com", ThemeLinkClient.validateUrl("https://example.com/theme.tc").host)
        listOf("127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.0.1", "169.254.169.254", "100.64.0.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "240.0.0.1", "::1", "fc00::1", "fe80::1", "2001:db8::1", "2001:2::1", "2002:a00:1::1").forEach { assertFalse(it, ThemeLinkClient.publicAddress(InetAddress.getByName(it))) }
        assertTrue(ThemeLinkClient.publicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(ThemeLinkClient.publicAddress(InetAddress.getByName("2001:4860:4860::8888")))
    }
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out -> entries.forEach { (name, data) -> out.putNextEntry(ZipEntry(name)); out.write(data); out.closeEntry() } }
        return bytes.toByteArray()
    }
    @Test fun archivePathsEncodingAndExpansionAreBounded() {
        val bytes = source.toByteArray()
        assertEquals(source, ThemePackages.unpack(zip("theme.tc" to bytes)).first)
        assertEquals(source, ThemePackages.unpack(zip("theme.stc" to bytes)).first)
        listOf("../outside.png", "assets/../outside.png", "assets\\outside.png", "/assets/a.png", "scripts/a.js").forEach { rejected { ThemePackages.unpack(zip("theme.tc" to bytes, it to byteArrayOf(1))) } }
        rejected { ThemePackages.unpack(zip("theme.tc" to bytes, "theme.stc" to bytes)) }
        val portable = ThemePackages.load(zip("theme.tc" to (source + "\n@reader-app\ncover: assets/reader.svg").toByteArray(), "assets/reader.svg" to "<svg/>".toByteArray()))
        assertTrue(portable.assets.isEmpty())
        rejected { ThemeCompose.parse(source + "\n@still-app\ntoday:\n  background: assets/reader.svg") }
        rejected { ThemePackages.unpack(byteArrayOf(0xc3.toByte(), 0x28)) }
        rejected { ThemePackages.unpack(zip("theme.tc" to bytes, "assets/bomb.png" to ByteArray(ThemePackages.MAX_DOWNLOAD + 1))) }
    }
    @Test fun annualIntervalsCrossNewYearAndOnceDatesValidate() {
        val theme = ThemeCompose.parse(source.replace("@common", "available:\n  mode: annual\n  from: 12-01\n  to: 01-31\n@common"))
        assertTrue(theme.available(java.time.LocalDate.of(2026, 12, 25)))
        assertTrue(theme.available(java.time.LocalDate.of(2027, 1, 1)))
        assertFalse(theme.available(java.time.LocalDate.of(2027, 2, 1)))
        rejected { ThemeCompose.parse(source.replace("@common", "available:\n  mode: once\n  from: 2026-02-30\n  to: 2026-03-01\n@common")) }
    }
}
