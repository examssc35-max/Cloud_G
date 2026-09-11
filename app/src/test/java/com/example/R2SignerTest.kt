package com.example

import com.example.data.cloudflare.R2Signer
import com.example.data.cloudflare.R2XmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class R2SignerTest {

    @Test
    fun `sha256Hex returns correct hash for empty input`() {
        val hash = R2Signer.sha256Hex(ByteArray(0))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
    }

    @Test
    fun `urlEncode properly encodes path characters`() {
        val encoded = R2Signer.urlEncode("photos/my dog 2026.jpg")
        assertTrue(encoded.contains("%20"))
    }

    @Test
    fun `presigned url contains necessary AWS SigV4 query params`() {
        val url = R2Signer.generatePresignedUrl(
            endpoint = "https://1234567890abcdef.r2.cloudflarestorage.com",
            bucket = "test-bucket",
            key = "photo.jpg",
            accessKeyId = "testAccessKey",
            secretAccessKey = "testSecretKey",
            expiresInSeconds = 3600,
            date = Date(1700000000000L)
        )

        assertTrue(url.startsWith("https://1234567890abcdef.r2.cloudflarestorage.com/test-bucket/photo.jpg?"))
        assertTrue(url.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"))
        assertTrue(url.contains("X-Amz-Credential="))
        assertTrue(url.contains("X-Amz-Signature="))
    }

    @Test
    fun `parseListBucketResult parses S3 XML correctly`() {
        val xml = """
            <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                <Name>test-bucket</Name>
                <Prefix></Prefix>
                <KeyCount>1</KeyCount>
                <MaxKeys>1000</MaxKeys>
                <IsTruncated>false</IsTruncated>
                <Contents>
                    <Key>vacation.jpg</Key>
                    <LastModified>2026-09-11T12:00:00.000Z</LastModified>
                    <Size>204800</Size>
                </Contents>
                <CommonPrefixes>
                    <Prefix>summer/</Prefix>
                </CommonPrefixes>
            </ListBucketResult>
        """.trimIndent()

        val parsed = R2XmlParser.parseListBucketResult(xml)
        assertEquals("test-bucket", parsed.name)
        assertEquals(1, parsed.commonPrefixes.size)
        assertEquals("summer/", parsed.commonPrefixes[0])
        assertEquals(1, parsed.contents.size)
        assertEquals("vacation.jpg", parsed.contents[0].key)
        assertEquals(204800L, parsed.contents[0].size)
    }
}
