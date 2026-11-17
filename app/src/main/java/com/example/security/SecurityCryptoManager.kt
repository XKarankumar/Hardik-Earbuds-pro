package com.example.security

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class SaltedHash(
    val hashBase64: String,
    val saltBase64: String
)

data class OwnerRegistrationArtifact(
    val ownerKeyId: String,
    val rawOwnerKeyForDisplay: String,
    val encryptedOwnerSecretBase64: String,
    val boundDeviceModel: String,
    val boundDeviceFingerprintHash: String,
    val rawRecoveryKeyForDisplay: String,
    val recoveryHash: SaltedHash,
    val keystoreBacked: Boolean
)

object SecurityCryptoManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "hardik_buds_pro_owner_master_key_v1"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val secureRandom = SecureRandom()

    // Ephemeral fallback key used ONLY in local JVM unit tests where AndroidKeyStore provider does not exist.
    @Volatile
    private var jvmFallbackTestKey: SecretKey? = null

    fun isPinFormatValid(pin: String, expectedLength: Int): Boolean {
        if (expectedLength != 4 && expectedLength != 6) return false
        return pin.length == expectedLength && pin.all { it in '0'..'9' }
    }

    fun hashSecret(secret: String): SaltedHash {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        val derived = derivePbkdf2(secret.toCharArray(), salt)
        return SaltedHash(
            hashBase64 = Base64.encodeToString(derived, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
    }

    fun verifySecret(secret: String, storedHashBase64: String?, storedSaltBase64: String?): Boolean {
        if (secret.isBlank() || storedHashBase64.isNullOrBlank() || storedSaltBase64.isNullOrBlank()) {
            return false
        }
        return runCatching {
            val salt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)
            val expected = Base64.decode(storedHashBase64, Base64.NO_WRAP)
            val actual = derivePbkdf2(secret.toCharArray(), salt)
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    fun generateRecoveryKey(): String {
        val raw = CharArray(24) {
            RECOVERY_ALPHABET[secureRandom.nextInt(RECOVERY_ALPHABET.length)]
        }.concatToString()
        return raw.chunked(4).joinToString("-")
    }

    fun normalizeRecoveryKey(input: String): String {
        return input.uppercase()
            .filter { it in 'A'..'Z' || it in '0'..'9' }
            .chunked(4)
            .joinToString("-")
    }

    fun generateOwnerRegistration(context: Context): OwnerRegistrationArtifact {
        val ownerBytes = ByteArray(16)
        secureRandom.nextBytes(ownerBytes)
        val rawOwnerKey = "HBP-OWN-" + ownerBytes.joinToString("") { "%02X".format(it) }
            .chunked(4)
            .take(4)
            .joinToString("-")

        val ownerKeyId = sha256Hex(rawOwnerKey).take(16).uppercase()
        val deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android Device" }
        val deviceFingerprint = computeDeviceFingerprintHash(context)

        val recoveryKey = generateRecoveryKey()
        val recoveryHash = hashSecret(normalizeRecoveryKey(recoveryKey))

        val payload = "$rawOwnerKey|$deviceFingerprint|${System.currentTimeMillis()}"
        val (encryptedBlob, keystoreBacked) = encryptWithKeystore(payload)

        return OwnerRegistrationArtifact(
            ownerKeyId = ownerKeyId,
            rawOwnerKeyForDisplay = rawOwnerKey,
            encryptedOwnerSecretBase64 = encryptedBlob,
            boundDeviceModel = deviceModel,
            boundDeviceFingerprintHash = deviceFingerprint,
            rawRecoveryKeyForDisplay = recoveryKey,
            recoveryHash = recoveryHash,
            keystoreBacked = keystoreBacked
        )
    }

    fun decryptOwnerSecret(encryptedBase64: String?): String? {
        if (encryptedBase64.isNullOrBlank()) return null
        return runCatching {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_BYTES) return null
            val iv = combined.copyOfRange(0, GCM_IV_BYTES)
            val cipherText = combined.copyOfRange(GCM_IV_BYTES, combined.size)
            val key = getOrCreateMasterKey().first
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(cipherText).decodeToString()
        }.getOrNull()
    }

    @SuppressLint("HardwareIds")
    fun computeDeviceFingerprintHash(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val raw = listOf(
            androidId,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.DEVICE,
            Build.HARDWARE
        ).joinToString("|")
        return sha256Hex(raw)
    }

    fun isCurrentDeviceBoundOwner(context: Context, storedFingerprintHash: String?): Boolean {
        if (storedFingerprintHash.isNullOrBlank()) return false
        val current = computeDeviceFingerprintHash(context)
        return MessageDigest.isEqual(
            current.toByteArray(Charsets.UTF_8),
            storedFingerprintHash.toByteArray(Charsets.UTF_8)
        )
    }

    fun computeLockoutDurationMillis(failedAttempts: Int): Long {
        return when {
            failedAttempts < 5 -> 0L
            failedAttempts == 5 -> 30_000L
            failedAttempts == 6 -> 60_000L
            failedAttempts == 7 -> 120_000L
            else -> 300_000L
        }
    }

    private fun encryptWithKeystore(plainText: String): Pair<String, Boolean> {
        val (key, keystoreBacked) = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP) to keystoreBacked
    }

    private fun getOrCreateMasterKey(): Pair<SecretKey, Boolean> {
        val ksResult = runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                existing to true
            } else {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val spec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                generator.init(spec)
                generator.generateKey() to true
            }
        }
        return ksResult.getOrElse {
            val fallback = jvmFallbackTestKey ?: synchronized(this) {
                jvmFallbackTestKey ?: run {
                    val bytes = ByteArray(32)
                    secureRandom.nextBytes(bytes)
                    SecretKeySpec(bytes, "AES").also { jvmFallbackTestKey = it }
                }
            }
            fallback to false
        }
    }

    private fun derivePbkdf2(chars: CharArray, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return factory.generateSecret(spec).encoded
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
