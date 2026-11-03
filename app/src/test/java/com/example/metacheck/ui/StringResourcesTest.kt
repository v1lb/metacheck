package com.example.metacheck.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Проверки локализации без Android: читаем res/values*/strings.xml и исходники.
 * Строки генерируются tools/i18n/gen.py, но тест ловит и ручные правки XML, и забытые ключи в коде.
 */
class StringResourcesTest {

    private sealed interface Entry {
        data class Text(val value: String, val translatable: Boolean) : Entry
        data class Plural(val forms: Map<String, String>) : Entry
    }

    private fun moduleFile(path: String): File =
        listOf(File(path), File("app/$path")).firstOrNull { it.exists() } ?: error("not found: $path")

    private fun load(dir: String): Map<String, Entry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(moduleFile("src/main/res/$dir/strings.xml"))
        val out = linkedMapOf<String, Entry>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val name = e.getAttribute("name")
            assertTrue("дубликат $name в $dir", name !in out)
            out[name] = when (e.tagName) {
                "string" -> Entry.Text(e.textContent, e.getAttribute("translatable") != "false")
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    Entry.Plural((0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to item.textContent
                    })
                }
                else -> error("unexpected <${e.tagName}> in $dir")
            }
        }
        return out
    }

    private val en by lazy { load("values") }
    private val ru by lazy { load("values-ru") }

    private val placeholder = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdfxX]")
    private fun args(text: String) = placeholder.findAll(text).map { it.value }.toSortedSet()
    private val cyrillic = Regex("[А-Яа-яЁё]")

    @Test fun russianTranslatesExactlyTheTranslatableKeys() {
        val translatable = en.filterValues { it !is Entry.Text || it.translatable }.keys
        assertEquals(translatable, ru.keys)
        assertTrue("непереводимые строки не должны попадать в values-ru",
            ru.values.none { it is Entry.Text && !it.translatable })
    }

    @Test fun placeholdersMatchBetweenLanguages() {
        for ((key, ruEntry) in ru) {
            when (val enEntry = en.getValue(key)) {
                is Entry.Text -> assertEquals(key, args(enEntry.value), args((ruEntry as Entry.Text).value))
                is Entry.Plural -> {
                    val expected = args(enEntry.forms.getValue("other"))
                    (enEntry.forms.values + (ruEntry as Entry.Plural).forms.values).forEach {
                        assertEquals("$key: $it", expected, args(it))
                    }
                }
            }
        }
    }

    @Test fun severalArgumentsArePositional() {
        (en.values + ru.values).flatMap {
            when (it) { is Entry.Text -> listOf(it.value); is Entry.Plural -> it.forms.values.toList() }
        }.forEach { text ->
            val found = args(text)
            if (found.size > 1) assertTrue(text, found.all { it.matches(Regex("%\\d+\\$.*")) })
        }
    }

    @Test fun pluralsHaveTheFormsEachLanguageNeeds() {
        en.filterValues { it is Entry.Plural }.forEach { (key, e) ->
            assertTrue(key, (e as Entry.Plural).forms.keys.containsAll(setOf("one", "other")))
            val ruForms = (ru.getValue(key) as Entry.Plural).forms.keys
            assertTrue("$key: $ruForms", ruForms.containsAll(setOf("one", "few", "many", "other")))
        }
    }

    @Test fun englishDefaultHasNoCyrillic() {
        en.filter { (_, e) -> e is Entry.Text && e.translatable || e is Entry.Plural }.forEach { (key, e) ->
            val text = if (e is Entry.Text) e.value else (e as Entry.Plural).forms.values.joinToString()
            assertTrue("$key: $text", !cyrillic.containsMatchIn(text))
        }
    }

    @Test fun legalLinksOpenTheDocumentInTheUiLanguage() {
        val base = "https://v1lb.github.io/legal-metacheck/"
        fun url(map: Map<String, Entry>, key: String) = (map.getValue(key) as Entry.Text).value
        assertEquals(base + "PRIVACY_POLICY_EN.html", url(en, "legal_privacy_url"))
        assertEquals(base + "PRIVACY_POLICY_RU.html", url(ru, "legal_privacy_url"))
        assertEquals(base + "TERMS_EN.html", url(en, "legal_terms_url"))
        assertEquals(base + "TERMS_RU.html", url(ru, "legal_terms_url"))
        assertTrue(LegalLinks.SUPPORT.startsWith(base))
    }

    /** Каждый R.string / R.plurals из кода существует, и каждая строка где-то используется. */
    @Test fun codeAndResourcesReferToTheSameKeys() {
        val sources = moduleFile("src/main/java").walk().filter { it.extension == "kt" }.map { it.readText() }.toList()
        val xml = moduleFile("src/main").walk()
            .filter { it.extension == "xml" && !it.path.contains("values") }.map { it.readText() }.toList()
        val used = sources.flatMap { Regex("R\\.(?:string|plurals)\\.([a-z0-9_]+)").findAll(it).map { m -> m.groupValues[1] } }
            .toSet() + xml.flatMap { Regex("@string/([a-z0-9_]+)").findAll(it).map { m -> m.groupValues[1] } }
        val generatedByGradle = setOf("maptiler_key") // resValue в app/build.gradle.kts
        assertEquals("нет в strings.xml", emptySet<String>(), used - en.keys - generatedByGradle)
        assertEquals("не используются", emptySet<String>(), en.keys - used)
    }

    /**
     * Тексты интерфейса только в ресурсах: в строковых литералах Kotlin не должно быть кириллицы.
     * Исключение — LegacyLabels: это данные для чтения старых отчётов, а не тексты.
     */
    @Test fun noCyrillicStringLiteralsInKotlin() {
        val offenders = moduleFile("src/main/java").walk()
            .filter { it.extension == "kt" && it.name != "LegacyLabels.kt" }
            .flatMap { file -> stringLiterals(file.readText()).filter { cyrillic.containsMatchIn(it) }.map { "${file.name}: $it" } }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test fun licenseNoticesListEveryRuntimeLibraryVersion() {
        val toml = moduleFile("../gradle/libs.versions.toml").readText()
        val versions = Regex("^(\\w+) = \"([^\"]+)\"", RegexOption.MULTILINE).findAll(
            toml.substringAfter("[versions]").substringBefore("[libraries]"),
        ).associate { it.groupValues[1] to it.groupValues[2] }
        val buildOnly = setOf("agp", "kotlin", "junit", "androidxJunit", "orgJson")
        for ((name, version) in versions - buildOnly) {
            assertTrue("$name $version нет в LicenseNotices", LicenseNotices.LIBRARIES.contains(version))
        }
        val full = LicenseNotices.text("intro", "map data", "tiles")
        listOf("osmdroid", "OkHttp", "Billing", "AppCompat", LicenseNotices.OSM_COPYRIGHT_URL, "intro", "tiles")
            .forEach { assertTrue(it, full.contains(it)) }
    }

    /** Строковые литералы без комментариев (простой лексер: "…", """…""", символы '…'). */
    private fun stringLiterals(src: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < src.length) {
            when {
                src.startsWith("//", i) -> i = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                src.startsWith("/*", i) -> i = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                src.startsWith("\"\"\"", i) -> {
                    val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) src.length else it }
                    out += src.substring(i + 3, end); i = end + 3
                }
                src[i] == '"' || src[i] == '\'' -> {
                    val quote = src[i]
                    var j = i + 1
                    while (j < src.length && src[j] != quote) j += if (src[j] == '\\') 2 else 1
                    if (quote == '"') out += src.substring(i + 1, minOf(j, src.length))
                    i = j + 1
                }
                else -> i++
            }
        }
        return out
    }
}
