package top.ceclin.pslock.model

import okhttp3.HttpUrl
import okio.ByteString
import timber.log.Timber
import top.ceclin.pslock.security.KeyProvider
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class QRCode(val mac: String, val key: String) {
    companion object {
        fun parse(content: String?): QRCode? {
            if (content == null) {
                return null
            }

            val url = HttpUrl.parse(content)?.takeIf {
                it.host() == "pslock.ceclin.top"
            } ?: return null
            val mac = url.queryParameter("mac") ?: return null
            val encryptedKey = url.queryParameter("key")?.let {
                ByteString.decodeBase64(it)?.toByteArray()
            } ?: return null
            val ivHex = url.queryParameter("iv") ?: return null

            return try {
                val iv = ByteString.decodeHex(ivHex).toByteArray()
                val aesKey = SecretKeySpec(KeyProvider.AES_KEY, "AES")
                val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING").apply {
                    init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(iv))
                }
                val key = cipher.doFinal(encryptedKey).let {
                    ByteString.of(it, 0, it.size).base64Url()
                }
                QRCode(mac, key).also {
                    Timber.i("Parsed QRCode content: %s", it)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse QRCode content")
                null
            }
        }
    }
}