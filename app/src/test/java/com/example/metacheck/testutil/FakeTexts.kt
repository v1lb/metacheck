package com.example.metacheck.testutil

import com.example.metacheck.domain.metadata.MetadataGroup
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import com.example.metacheck.domain.text.MetaTexts
import com.example.metacheck.domain.text.TextKey

/**
 * Предсказуемые «тексты» для unit-тестов домена: вместо ресурсов Android возвращают ключи.
 * [labels] подменяет подписи полей по ключу, например, чтобы проверить поиск при русском интерфейсе.
 * Настоящие строки проверяются тестами ресурсов (StringResourcesTest) и инструментальными тестами.
 */
class FakeTexts(private val labels: Map<String, String> = emptyMap()) : MetaTexts {
    override fun itemLabel(item: MetaItem): String =
        item.key?.let { labels[it] ?: it.substringAfter(':') } ?: item.label
    override fun itemValue(item: MetaItem): String =
        if (item.format == null) item.value else "${item.value} <${item.format}>"
    override fun sectionTitle(section: MetaSection): String = section.key?.name ?: section.title
    override fun groupTitle(group: MetadataGroup): String = "group:${group.name}"
    override fun signalTitle(signal: Signal): String = signal.kind?.name ?: signal.title
    override fun signalDetail(signal: Signal): String =
        if (signal.kind == null) signal.detail else signal.args.joinToString("|")
    override fun severityTag(severity: Severity): String = severity.name
    override fun formatName(format: ImageFormat): String = format.name
    override fun text(key: TextKey, vararg args: Any): String =
        if (args.isEmpty()) key.name else key.name + "(" + args.joinToString(", ") + ")"
}
