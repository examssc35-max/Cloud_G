package com.example.data.cloudflare

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ParsedObject(
    val key: String,
    val size: Long,
    val lastModified: Long
)

data class ListBucketResult(
    val name: String = "",
    val prefix: String = "",
    val commonPrefixes: List<String> = emptyList(),
    val contents: List<ParsedObject> = emptyList(),
    val isTruncated: Boolean = false,
    val nextContinuationToken: String? = null
)

object R2XmlParser {

    fun parseListBucketResult(xmlString: String): ListBucketResult {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlString))

        var eventType = parser.eventType
        var bucketName = ""
        var prefix = ""
        var isTruncated = false
        var nextContinuationToken: String? = null

        val commonPrefixes = mutableListOf<String>()
        val contents = mutableListOf<ParsedObject>()

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            if (eventType == XmlPullParser.START_TAG) {
                when (tagName) {
                    "Name" -> bucketName = parser.nextText()
                    "Prefix" -> {
                        // Only bucket prefix, not inside CommonPrefixes
                        prefix = parser.nextText()
                    }
                    "IsTruncated" -> isTruncated = parser.nextText().toBoolean()
                    "NextContinuationToken" -> nextContinuationToken = parser.nextText()
                    "CommonPrefixes" -> {
                        val folderPrefix = parseCommonPrefix(parser)
                        if (folderPrefix.isNotEmpty()) {
                            commonPrefixes.add(folderPrefix)
                        }
                    }
                    "Contents" -> {
                        val parsed = parseContents(parser, isoFormat)
                        if (parsed != null) {
                            contents.add(parsed)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return ListBucketResult(
            name = bucketName,
            prefix = prefix,
            commonPrefixes = commonPrefixes,
            contents = contents,
            isTruncated = isTruncated,
            nextContinuationToken = nextContinuationToken
        )
    }

    private fun parseCommonPrefix(parser: XmlPullParser): String {
        var prefix = ""
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name == "CommonPrefixes")) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "Prefix") {
                prefix = parser.nextText()
            }
            eventType = parser.next()
        }
        return prefix
    }

    private fun parseContents(parser: XmlPullParser, isoFormat: SimpleDateFormat): ParsedObject? {
        var key = ""
        var size = 0L
        var lastModified = System.currentTimeMillis()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name == "Contents")) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Key" -> key = parser.nextText()
                    "Size" -> size = parser.nextText().toLongOrNull() ?: 0L
                    "LastModified" -> {
                        val text = parser.nextText()
                        try {
                            // Extract base yyyy-MM-ddTHH:mm:ss
                            val cleanText = if (text.length >= 19) text.substring(0, 19) else text
                            lastModified = isoFormat.parse(cleanText)?.time ?: System.currentTimeMillis()
                        } catch (_: Exception) {
                            lastModified = System.currentTimeMillis()
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return if (key.isNotEmpty()) ParsedObject(key, size, lastModified) else null
    }
}
