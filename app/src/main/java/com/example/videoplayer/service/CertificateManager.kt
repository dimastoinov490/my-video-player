package com.example.videoplayer.service

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.NetworkInterface
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

object CertificateManager {
    private const val CERT_FILE_NAME = "nexora_cert.p12"
    private const val CERT_ALIAS = "nexora_tls"
    private const val KEYSTORE_ALIAS = "nexora_ssl_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "nexora_ssl_prefs"
    private const val PREF_ENC_PASS = "enc_pass"
    private const val PREF_PASS_IV = "pass_iv"

    @Volatile
    private var cachedCert: X509Certificate? = null
    @Volatile
    private var cachedSslContext: SSLContext? = null

    @Synchronized
    fun getOrCreateSslContext(context: Context, targetIp: String? = null): SSLContext {
        val existingCert = getCertificate(context)
        if (existingCert != null && isCertValid(existingCert, targetIp)) {
            val ctx = cachedSslContext
            if (ctx != null) return ctx
            val loadedCtx = buildSslContext(context)
            cachedSslContext = loadedCtx
            return loadedCtx
        }

        // Regenerate cert & keystore
        generateAndSaveCert(context)
        val loadedCtx = buildSslContext(context)
        cachedSslContext = loadedCtx
        return loadedCtx
    }

    @Synchronized
    fun getCertificate(context: Context): X509Certificate? {
        val cached = cachedCert
        if (cached != null) return cached

        val file = File(context.filesDir, CERT_FILE_NAME)
        if (!file.exists()) return null

        return try {
            val password = getOrGenerateKeystorePassword(context)
            val ks = KeyStore.getInstance("PKCS12")
            FileInputStream(file).use { fis ->
                ks.load(fis, password)
            }
            val cert = ks.getCertificate(CERT_ALIAS) as? X509Certificate
            cachedCert = cert
            cert
        } catch (_: Exception) {
            null
        }
    }

    fun getCertificateDer(context: Context): ByteArray? {
        val cert = getCertificate(context) ?: run {
            getOrCreateSslContext(context)
            getCertificate(context)
        }
        return cert?.encoded
    }

    fun getFingerprint(context: Context): String {
        val cert = getCertificate(context) ?: run {
            getOrCreateSslContext(context)
            getCertificate(context)
        } ?: return ""

        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(cert.encoded)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    fun getExpirationDate(context: Context): Long {
        val cert = getCertificate(context) ?: return 0L
        return cert.notAfter.time
    }

    fun getSanEntries(context: Context): List<String> {
        val cert = getCertificate(context) ?: return emptyList()
        val list = mutableListOf<String>()
        try {
            val altNames = cert.subjectAlternativeNames ?: return emptyList()
            for (item in altNames) {
                if (item.size >= 2) {
                    list.add(item[1].toString())
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun isCertValid(cert: X509Certificate, targetIp: String?): Boolean {
        return try {
            // Must have at least 30 days remaining
            val thirtyDaysMs = 30L * 24 * 3600 * 1000L
            val testDate = Date(System.currentTimeMillis() + thirtyDaysMs)
            cert.checkValidity(testDate)

            if (!targetIp.isNullOrBlank() && targetIp != "auto") {
                val san = cert.subjectAlternativeNames ?: return false
                var found = false
                for (item in san) {
                    if (item.size >= 2 && item[1].toString().equals(targetIp, ignoreCase = true)) {
                        found = true
                        break
                    }
                }
                if (!found) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildSslContext(context: Context): SSLContext {
        val password = getOrGenerateKeystorePassword(context)
        val file = File(context.filesDir, CERT_FILE_NAME)
        val ks = KeyStore.getInstance("PKCS12")
        FileInputStream(file).use { fis ->
            ks.load(fis, password)
        }

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, password)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())
        return sslContext
    }

    @Synchronized
    private fun generateAndSaveCert(context: Context) {
        val keyPairGen = KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGen.generateKeyPair()

        val subject = X500Name("CN=Nexora Local Server, O=Nexora, C=RU")
        val serial = BigInteger(64, SecureRandom())
        val notBefore = Date(System.currentTimeMillis() - 24 * 3600 * 1000L)
        val notAfter = Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000L)

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )

        // SAN extension: all device IPv4 addresses + 127.0.0.1 + nexora.local + localhost
        val generalNames = mutableListOf<GeneralName>()
        generalNames.add(GeneralName(GeneralName.dNSName, "nexora.local"))
        generalNames.add(GeneralName(GeneralName.dNSName, "localhost"))
        generalNames.add(GeneralName(GeneralName.iPAddress, "127.0.0.1"))

        val ips = collectAllDeviceIps()
        for (ip in ips) {
            if (ip != "127.0.0.1") {
                generalNames.add(GeneralName(GeneralName.iPAddress, ip))
            }
        }

        certBuilder.addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(generalNames.toTypedArray())
        )

        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyAgreement)
        )

        certBuilder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)
        )

        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        val cert = JcaX509CertificateConverter().getCertificate(certHolder)

        // Save into PKCS12 file
        val password = getOrGenerateKeystorePassword(context)
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(CERT_ALIAS, keyPair.private, password, arrayOf(cert))

        val file = File(context.filesDir, CERT_FILE_NAME)
        FileOutputStream(file).use { fos ->
            ks.store(fos, password)
        }

        cachedCert = cert
        cachedSslContext = null
    }

    private fun collectAllDeviceIps(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !result.contains(host)) {
                            result.add(host)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun getOrGenerateKeystorePassword(context: Context): CharArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encPassB64 = prefs.getString(PREF_ENC_PASS, null)
        val ivB64 = prefs.getString(PREF_PASS_IV, null)

        val secretKey = getOrCreateKeystoreMasterKey()

        if (!encPassB64.isNullOrEmpty() && !ivB64.isNullOrEmpty()) {
            try {
                val encBytes = Base64.decode(encPassB64, Base64.NO_WRAP)
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                val decrypted = cipher.doFinal(encBytes)
                return String(decrypted, Charsets.UTF_8).toCharArray()
            } catch (_: Exception) {
                // If decryption failed, generate fresh password
            }
        }

        // Generate 32 random alphanumeric chars
        val random = SecureRandom()
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val pwStr = (1..32).map { chars[random.nextInt(chars.length)] }.joinToString("")
        val pwBytes = pwStr.toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encBytes = cipher.doFinal(pwBytes)

        prefs.edit()
            .putString(PREF_ENC_PASS, Base64.encodeToString(encBytes, Base64.NO_WRAP))
            .putString(PREF_PASS_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()

        return pwStr.toCharArray()
    }

    private fun getOrCreateKeystoreMasterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        if (ks.containsAlias(KEYSTORE_ALIAS)) {
            val entry = ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }
}
