package com.example.metacheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Регрессионные проверки безопасности манифеста и сетевого конфига.
 * Тест упадёт, если кто-то случайно добавит экспортируемый компонент, INTERNET или HTTP.
 */
class ManifestSecurityTest {

    private val ns = "http://schemas.android.com/apk/res/android"

    private fun parse(path: String): Element {
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        return factory.newDocumentBuilder().parse(file).documentElement
    }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE && it.nodeName == tag }
            .map { it as Element }

    private val manifest get() = parse("src/main/AndroidManifest.xml")
    private val application get() = manifest.children("application").single()

    private val tools = "http://schemas.android.com/tools"

    private fun permissions(removed: Boolean): Set<String> = manifest.children("uses-permission")
        .filter { (it.getAttributeNS(tools, "node") == "remove") == removed }
        .map { it.getAttributeNS(ns, "name") }
        .toSet()

    @Test
    fun onlyInternetPermissionIsRequested() {
        assertEquals(setOf("android.permission.INTERNET", "android.permission.ACCESS_MEDIA_LOCATION"), permissions(removed = false))
        assertTrue(manifest.children("uses-permission-sdk-23").isEmpty())
    }

    /** Аудит, п. 20: разрешения, которые могут прийти из библиотек, вырезаются из итогового манифеста. */
    @Test
    fun libraryStoragePermissionsAreRemoved() {
        val removed = permissions(removed = true)
        for (p in listOf("WRITE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE", "ACCESS_WIFI_STATE")) {
            assertTrue("$p должен быть убран через tools:node=\"remove\"", "android.permission.$p" in removed)
        }
        // Нельзя случайно вырезать то, без чего не работают карта или покупки.
        assertFalse("android.permission.ACCESS_NETWORK_STATE" in removed)
        assertFalse("android.permission.INTERNET" in removed)
        assertFalse("com.android.vending.BILLING" in removed)
    }

    @Test
    fun backupAndCleartextAreDisabled() {
        assertEquals("false", application.getAttributeNS(ns, "allowBackup"))
        assertEquals("false", application.getAttributeNS(ns, "usesCleartextTraffic"))
    }

    @Test
    fun onlyLauncherActivityIsExported() {
        val components = listOf("activity", "activity-alias", "service", "receiver", "provider")
            .flatMap { application.children(it) }
        for (c in components) {
            val name = c.getAttributeNS(ns, "name")
            val exported = c.getAttributeNS(ns, "exported")
            // Начиная с Android 12 атрибут обязателен для компонентов с intent-filter.
            assertTrue("$name: exported должен быть задан явно", exported.isNotEmpty())
            if (exported == "true") {
                // Лаунчер и второй пункт «Поделиться» («Отправить без метаданных») — оба ведут в MainActivity.
                assertTrue("Экспортируется лишнее: $name", name in setOf(".ui.MainActivity", ".ui.CleanShareAlias"))
            }
        }
    }

    @Test
    fun networkSecurityConfigForbidsCleartext() {
        val root = parse("src/main/res/xml/network_security_config.xml")
        val base = root.children("base-config").single()
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        val anchors = base.children("trust-anchors").single().children("certificates")
        assertEquals(listOf("system"), anchors.map { it.getAttribute("src") })
    }

    @Test
    fun networkConfigIsReferencedFromManifest() {
        assertEquals("@xml/network_security_config", application.getAttributeNS(ns, "networkSecurityConfig"))
        assertFalse(application.getAttributeNS(ns, "debuggable") == "true")
    }
}
