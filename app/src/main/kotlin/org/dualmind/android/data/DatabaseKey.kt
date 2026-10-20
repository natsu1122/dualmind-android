// SPDX-License-Identifier: Apache-2.0
package org.dualmind.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object DatabaseKey {
    @Synchronized fun load(context: Context): ByteArray {
        val alias = "dualmind.database.v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val wrapped = AtomicFile(File(context.noBackupFilesDir, "database-key"))
        val exists = wrapped.baseFile.exists()
        check(!exists || store.containsAlias(alias)) { "La clave local no está disponible. Restaura una copia desde Configuración." }
        val key = if (store.containsAlias(alias)) store.getKey(alias, null) as SecretKey
            else KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (exists) {
            val bytes = wrapped.readFully()
            require(bytes.size >= 28)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        }
        check(!context.getDatabasePath("dualmind.db").exists()) { "Falta la clave de una base existente; no se sobrescribirá." }
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val output = wrapped.startWrite()
        try { output.write(cipher.iv + cipher.doFinal(passphrase)); wrapped.finishWrite(output) }
        catch (e: Exception) { wrapped.failWrite(output); throw e }
        return passphrase
    }
}
