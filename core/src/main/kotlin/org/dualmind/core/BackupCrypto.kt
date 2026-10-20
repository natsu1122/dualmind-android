// SPDX-License-Identifier: Apache-2.0
package org.dualmind.core

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable encrypted export. Database keys and model paths are never exported. */
object BackupCrypto {
    const val MAX_BYTES = 20 * 1024 * 1024
    private val magic = "DUALMIND1".toByteArray()
    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        require(password.size in 12..512) { "La contraseña debe tener entre 12 y 512 caracteres." }
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try { Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)); updateAAD(magic)
        } } finally { key.fill(0) }
    }
    fun encrypt(data: ByteArray, password: CharArray): ByteArray {
        require(data.size <= MAX_BYTES)
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        return magic + salt + iv + cipher(Cipher.ENCRYPT_MODE, password, salt, iv).doFinal(data)
    }
    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        require(data.size in (magic.size + 44)..(MAX_BYTES + 128)) { "Tamaño de copia inválido." }
        val buffer = ByteBuffer.wrap(data)
        require(ByteArray(magic.size).also(buffer::get).contentEquals(magic)) { "Formato de copia desconocido." }
        val salt = ByteArray(16).also(buffer::get)
        val iv = ByteArray(12).also(buffer::get)
        return cipher(Cipher.DECRYPT_MODE, password, salt, iv).doFinal(ByteArray(buffer.remaining()).also(buffer::get))
    }
}
