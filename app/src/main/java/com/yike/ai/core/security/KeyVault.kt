package com.yike.ai.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyVault(context: Context) {
    private val directory = File(context.noBackupFilesDir, "credentials").apply { mkdirs() }
    private val alias = "yike.byok.aes.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
                generateKey()
            }
    }

    private fun file(id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9-]+")))
        return File(directory, id)
    }

    fun put(id: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray())
        val f = file(id)
        android.util.AtomicFile(f).run {
            val out = startWrite()
            try {
                out.write(cipher.iv.size)
                out.write(cipher.iv)
                out.write(encrypted)
                finishWrite(out)
            } catch (e: Exception) {
                failWrite(out)
                throw e
            }
        }
    }

    fun get(id: String): String {
        val f = file(id)
        if (!f.exists()) return ""
        val bytes = f.readBytes()
        require(bytes.isNotEmpty())
        val n = bytes[0].toInt()
        require(n == 12 && bytes.size > n + 1)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + n)))
        return cipher.doFinal(bytes.copyOfRange(1 + n, bytes.size)).toString(Charsets.UTF_8)
    }

    fun remove(id: String) {
        val f = file(id)
        if (f.exists()) check(f.delete()) { "密钥删除失败" }
    }
}
