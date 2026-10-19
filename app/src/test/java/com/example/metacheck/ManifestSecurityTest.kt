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

    @Test
    fun onlyInternetPermissionIsRequested() {
        val permissions = manifest.children("uses-permission").map { it.getAttributeNS(ns, "name") }
        assertEquals(listOf("android.permission.INTERNET"), permissions)
        assertTrue(manifest.children("uses-permission-sdk-23").isEmpty())
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
                assertEquals("Экспортируется лишнее: $name", ".ui.MainActivity", name)
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
