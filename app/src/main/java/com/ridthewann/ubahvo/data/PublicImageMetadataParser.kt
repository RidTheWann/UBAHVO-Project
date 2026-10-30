package com.ridthewann.ubahvo.data

import java.net.URI
import java.util.Locale

internal data class PublicImageMetadata(
    val title: String?,
    val imageUrls: List<String>
)

/** Parses publicly exposed Open Graph/Twitter image metadata without executing page JavaScript. */
internal object PublicImageMetadataParser {
    private val metaTagPattern = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attributePattern = Regex("""([a-zA-Z_:][a-zA-Z0-9_:.-]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
    private val titleTagPattern = Regex("""<title\b[^>]*>(.*?)</title\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val entityPattern = Regex("""&(#x[0-9a-fA-F]+|#\d+|amp|quot|apos|lt|gt);""", RegexOption.IGNORE_CASE)

    fun parse(html: String, pageUrl: String): PublicImageMetadata? {
        val tags = metaTagPattern.findAll(html).mapNotNull { match ->
            val attributes = attributePattern.findAll(match.value).associate { attribute ->
                val key = attribute.groupValues[1].lowercase(Locale.ROOT)
                val value = listOf(attribute.groupValues[2], attribute.groupValues[3], attribute.groupValues[4])
                    .firstOrNull { it.isNotEmpty() }.orEmpty()
                key to decodeHtml(value)
            }
            val key = (attributes["property"] ?: attributes["name"]).orEmpty().lowercase(Locale.ROOT)
            if (key.isBlank()) null else key to attributes["content"].orEmpty()
        }.toList()

        val title = tags.firstOrNull { it.first == "og:title" }?.second
            ?.trim()?.takeIf { it.isNotBlank() }
            ?: titleTagPattern.find(html)?.groupValues?.getOrNull(1)
                ?.let(::decodeHtml)?.replace(Regex("""<[^>]+>"""), "")
                ?.trim()?.takeIf { it.isNotBlank() }

        val candidates = ArrayList<String>()
        for (key in listOf("og:image:secure_url", "og:image", "twitter:image", "twitter:image:src")) {
            tags.asSequence().filter { it.first == key }.map { it.second.trim() }
                .filter { it.isNotBlank() }.forEach { raw ->
                    val resolved = runCatching { URI(pageUrl).resolve(raw).normalize() }.getOrNull()
                        ?: return@forEach
                    if (resolved.scheme.equals("https", ignoreCase = true) &&
                        resolved.host != null && resolved.rawUserInfo == null
                    ) {
                        val candidate = resolved.toASCIIString()
                        if (candidate !in candidates) candidates += candidate
                    }
                }
        }
        return if (candidates.isEmpty()) null else PublicImageMetadata(title, candidates)
    }

    private fun decodeHtml(value: String): String = entityPattern.replace(value) { match ->
        when (val entity = match.groupValues[1].lowercase(Locale.ROOT)) {
            "amp" -> "&"
            "quot" -> "\""
            "apos" -> "'"
            "lt" -> "<"
            "gt" -> ">"
            else -> {
                val codePoint = runCatching {
                    if (entity.startsWith("#x")) entity.substring(2).toInt(16) else entity.substring(1).toInt(10)
                }.getOrNull()
                if (codePoint != null && Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint))
                else match.value
            }
        }
    }
}
