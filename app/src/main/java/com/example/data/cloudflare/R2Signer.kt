package com.example.data.cloudflare

import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object R2Signer {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"
    private const val REGION = "auto"
    private const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"

    fun signRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        payloadHash: String,
        accessKeyId: String,
        secretAccessKey: String,
        date: Date = Date()
    ): Map<String, String> {
        val uri = URI(url)
        val amzDate = getAmzDate(date)
        val dateStamp = getDateStamp(date)

        val host = if (uri.port != -1 && uri.port != 80 && uri.port != 443) {
            "${uri.host}:${uri.port}"
        } else {
            uri.host
        }

        val signedHeadersMap = mutableMapOf<String, String>()
        signedHeadersMap["host"] = host
        signedHeadersMap["x-amz-date"] = amzDate
        signedHeadersMap["x-amz-content-sha256"] = payloadHash

        headers.forEach { (k, v) ->
            signedHeadersMap[k.lowercase(Locale.ROOT)] = v.trim()
        }

        val sortedHeaderNames = signedHeadersMap.keys.sorted()
        val canonicalHeaders = sortedHeaderNames.joinToString("") { "$it:${signedHeadersMap[it]}\n" }
        val signedHeadersList = sortedHeaderNames.joinToString(";")

        val canonicalUri = encodePath(uri.path)
        val canonicalQueryString = buildCanonicalQueryString(uri.query)

        val canonicalRequest = listOf(
            method.uppercase(Locale.ROOT),
            canonicalUri,
            canonicalQueryString,
            canonicalHeaders,
            signedHeadersList,
            payloadHash
        ).joinToString("\n")

        val credentialScope = "$dateStamp/$REGION/$SERVICE/aws4_request"
        val stringToSign = listOf(
            ALGORITHM,
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))
        ).joinToString("\n")

        val signingKey = getSignatureKey(secretAccessKey, dateStamp, REGION, SERVICE)
        val signature = bytesToHex(hmacSha256(signingKey, stringToSign))

        val authHeader = "$ALGORITHM Credential=$accessKeyId/$credentialScope, SignedHeaders=$signedHeadersList, Signature=$signature"

        val resultHeaders = mutableMapOf<String, String>()
        resultHeaders["Host"] = host
        resultHeaders["x-amz-date"] = amzDate
        resultHeaders["x-amz-content-sha256"] = payloadHash
        resultHeaders["Authorization"] = authHeader
        headers.forEach { (k, v) ->
            resultHeaders[k] = v
        }
        return resultHeaders
    }

    fun generatePresignedUrl(
        endpoint: String,
        bucket: String,
        key: String,
        accessKeyId: String,
        secretAccessKey: String,
        expiresInSeconds: Long = 3600L,
        date: Date = Date()
    ): String {
        val cleanEndpoint = endpoint.trimEnd('/')
        val path = "/$bucket/${key.trimStart('/')}"
        val uri = URI("$cleanEndpoint$path")

        val host = if (uri.port != -1 && uri.port != 80 && uri.port != 443) {
            "${uri.host}:${uri.port}"
        } else {
            uri.host
        }

        val amzDate = getAmzDate(date)
        val dateStamp = getDateStamp(date)
        val credentialScope = "$dateStamp/$REGION/$SERVICE/aws4_request"

        val queryParams = mutableMapOf<String, String>()
        queryParams["X-Amz-Algorithm"] = ALGORITHM
        queryParams["X-Amz-Credential"] = "$accessKeyId/$credentialScope"
        queryParams["X-Amz-Date"] = amzDate
        queryParams["X-Amz-Expires"] = expiresInSeconds.toString()
        queryParams["X-Amz-SignedHeaders"] = "host"

        val canonicalQueryString = queryParams.entries
            .sortedBy { it.key }
            .joinToString("&") { "${urlEncode(it.key)}=${urlEncode(it.value)}" }

        val canonicalUri = encodePath(uri.path)
        val canonicalHeaders = "host:$host\n"
        val signedHeaders = "host"

        val canonicalRequest = listOf(
            "GET",
            canonicalUri,
            canonicalQueryString,
            canonicalHeaders,
            signedHeaders,
            UNSIGNED_PAYLOAD
        ).joinToString("\n")

        val stringToSign = listOf(
            ALGORITHM,
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))
        ).joinToString("\n")

        val signingKey = getSignatureKey(secretAccessKey, dateStamp, REGION, SERVICE)
        val signature = bytesToHex(hmacSha256(signingKey, stringToSign))

        return "$cleanEndpoint$path?$canonicalQueryString&X-Amz-Signature=$signature"
    }

    private fun getAmzDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    private fun getDateStamp(date: Date): String {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    private fun buildCanonicalQueryString(query: String?): String {
        if (query.isNullOrEmpty()) return ""
        val pairs = query.split("&").filter { it.isNotEmpty() }
        return pairs.map { pair ->
            val idx = pair.indexOf('=')
            if (idx >= 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                urlEncode(key) to urlEncode(value)
            } else {
                urlEncode(pair) to ""
            }
        }.sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("&") { "${it.first}=${it.second}" }
    }

    fun encodePath(path: String): String {
        if (path.isEmpty()) return "/"
        val segments = path.split("/").map { urlEncode(it) }
        val encoded = segments.joinToString("/")
        return if (encoded.startsWith("/")) encoded else "/$encoded"
    }

    fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    fun sha256Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return bytesToHex(md.digest(data))
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun getSignatureKey(key: String, dateStamp: String, regionName: String, serviceName: String): ByteArray {
        val kSecret = ("AWS4$key").toByteArray(Charsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, regionName)
        val kService = hmacSha256(kRegion, serviceName)
        return hmacSha256(kService, "aws4_request")
    }

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val digits = "0123456789abcdef"
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = digits[v ushr 4]
            hexChars[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(hexChars)
    }
}
