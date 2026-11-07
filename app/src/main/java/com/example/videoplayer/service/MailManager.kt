package com.example.videoplayer.service

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MailDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "nexora_mail.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_id TEXT NOT NULL,
                recipient_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                text_enc BLOB,
                text_iv BLOB,
                file_id TEXT,
                file_name TEXT,
                file_size INTEGER,
                file_mime TEXT,
                file_dur INTEGER,
                timestamp INTEGER NOT NULL,
                is_read INTEGER NOT NULL DEFAULT 0,
                is_deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_pair ON messages(sender_id, recipient_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_id ON messages(id)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS profiles (
                device_id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

object MailManager {
    private const val TAG = "MailManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_MAIL = "nexora_mail"
    private const val CHUNK_SIZE = 64 * 1024 // 64 KB
    const val MAX_FILE_SIZE = 200L * 1024 * 1024 // 200 MB
    const val MAX_TOTAL_QUOTA = 2L * 1024 * 1024 * 1024 // 2 GB

    private val uploadRateLimitMap = ConcurrentHashMap<String, MutableList<Long>>()

    private var dbHelper: MailDatabaseHelper? = null

    @Synchronized
    private fun getDb(context: Context): SQLiteDatabase {
        if (dbHelper == null) {
            dbHelper = MailDatabaseHelper(context.applicationContext)
        }
        return dbHelper!!.writableDatabase
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        if (ks.containsAlias(KEY_ALIAS_MAIL)) {
            val entry = ks.getEntry(KEY_ALIAS_MAIL, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS_MAIL,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    private fun encryptText(plainText: String): Pair<ByteArray, ByteArray> {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val enc = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Pair(enc, iv)
    }

    private fun decryptText(enc: ByteArray?, iv: ByteArray?): String {
        if (enc == null || iv == null) return ""
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(enc), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun checkUploadRateLimit(identifier: String): Boolean {
        val now = System.currentTimeMillis()
        val oneMinAgo = now - 60000L
        val timestamps = uploadRateLimitMap.computeIfAbsent(identifier) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeAll { it < oneMinAgo }
            if (timestamps.size >= 60) return false
            timestamps.add(now)
            return true
        }
    }

    fun checkQuota(context: Context, newBytes: Long): Boolean {
        val mailDir = File(context.filesDir, "mail")
        if (!mailDir.exists()) return true
        val currentSize = mailDir.listFiles()?.sumOf { it.length() } ?: 0L
        return (currentSize + newBytes) <= MAX_TOTAL_QUOTA
    }

    fun saveProfile(context: Context, deviceId: String, name: String) {
        val db = getDb(context)
        val cv = ContentValues().apply {
            put("device_id", deviceId)
            put("display_name", name.trim())
        }
        db.insertWithOnConflict("profiles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getProfileName(context: Context, deviceId: String): String? {
        val db = getDb(context)
        db.rawQuery("SELECT display_name FROM profiles WHERE device_id = ?", arrayOf(deviceId)).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    fun sendMessage(
        context: Context,
        senderId: String,
        recipientId: String,
        kind: String,
        text: String,
        fileId: String? = null,
        fileName: String? = null,
        fileSize: Long? = null,
        fileMime: String? = null,
        fileDur: Int? = null
    ): Long {
        val db = getDb(context)
        val (enc, iv) = encryptText(text)
        val now = System.currentTimeMillis()

        val cv = ContentValues().apply {
            put("sender_id", senderId)
            put("recipient_id", recipientId)
            put("kind", kind)
            put("text_enc", enc)
            put("text_iv", iv)
            put("file_id", fileId)
            put("file_name", fileName)
            put("file_size", fileSize)
            put("file_mime", fileMime)
            put("file_dur", fileDur)
            put("timestamp", now)
            put("is_read", 0)
            put("is_deleted", 0)
        }
        return db.insert("messages", null, cv)
    }

    fun markRead(context: Context, myDeviceId: String, peerId: String) {
        val db = getDb(context)
        val cv = ContentValues().apply {
            put("is_read", 1)
        }
        db.update(
            "messages",
            cv,
            "sender_id = ? AND recipient_id = ? AND is_read = 0",
            arrayOf(peerId, myDeviceId)
        )
    }

    fun isSender(context: Context, myDeviceId: String, msgId: Long): Boolean {
        val db = getDb(context)
        return db.rawQuery(
            "SELECT sender_id FROM messages WHERE id = ?",
            arrayOf(msgId.toString())
        ).use {
            if (it.moveToFirst()) {
                it.getString(0) == myDeviceId
            } else {
                false
            }
        }
    }

    fun deleteMessage(context: Context, myDeviceId: String, msgId: Long, isAdmin: Boolean): Boolean {
        val db = getDb(context)
        var fileIdToDelete: String? = null

        val cursor = db.rawQuery(
            "SELECT sender_id, file_id FROM messages WHERE id = ?",
            arrayOf(msgId.toString())
        )
        val canDelete = cursor.use {
            if (it.moveToFirst()) {
                val sender = it.getString(0)
                fileIdToDelete = it.getString(1)
                isAdmin || sender == myDeviceId
            } else {
                false
            }
        }

        if (!canDelete) return false

        val cv = ContentValues().apply {
            put("is_deleted", 1)
        }
        db.update("messages", cv, "id = ?", arrayOf(msgId.toString()))

        // Delete attachment file from disk if present
        if (!fileIdToDelete.isNullOrBlank()) {
            val f = File(File(context.filesDir, "mail"), "$fileIdToDelete.bin")
            if (f.exists()) f.delete()
        }
        return true
    }

    fun getMessages(context: Context, myDeviceId: String, peerId: String, afterId: Long): JSONObject {
        val db = getDb(context)
        val messagesArr = JSONArray()
        val deletedArr = JSONArray()

        val sql = """
            SELECT id, sender_id, recipient_id, kind, text_enc, text_iv,
                   file_id, file_name, file_size, file_mime, file_dur,
                   timestamp, is_read, is_deleted
            FROM messages
            WHERE ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))
              AND id > ?
            ORDER BY id ASC
        """.trimIndent()

        db.rawQuery(sql, arrayOf(myDeviceId, peerId, peerId, myDeviceId, afterId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val senderId = cursor.getString(1)
                val isDeleted = cursor.getInt(13) == 1

                if (isDeleted) {
                    deletedArr.put(id)
                    continue
                }

                val kind = cursor.getString(3)
                val textEnc = cursor.getBlob(4)
                val textIv = cursor.getBlob(5)
                val fileId = cursor.getString(6)
                val fileName = cursor.getString(7)
                val fileSize = cursor.getLong(8)
                val fileDur = if (cursor.isNull(10)) null else cursor.getInt(10)
                val timestamp = cursor.getLong(11)
                val isRead = cursor.getInt(12) == 1

                val plainText = decryptText(textEnc, textIv)

                val msgObj = JSONObject()
                msgObj.put("id", id)
                msgObj.put("mine", senderId == myDeviceId)
                msgObj.put("kind", kind)
                msgObj.put("text", plainText)
                msgObj.put("t", timestamp)
                msgObj.put("read", isRead)

                if (!fileId.isNullOrBlank()) {
                    val fileObj = JSONObject()
                    fileObj.put("id", fileId)
                    fileObj.put("name", fileName ?: "file")
                    fileObj.put("size", fileSize)
                    if (fileDur != null && fileDur > 0) {
                        fileObj.put("dur", fileDur)
                    }
                    msgObj.put("file", fileObj)
                }

                messagesArr.put(msgObj)
            }
        }

        val res = JSONObject()
        res.put("messages", messagesArr)
        res.put("deleted", deletedArr)
        return res
    }

    fun getPeers(
        context: Context,
        myDeviceId: String,
        registeredDevices: Map<String, DeviceRecord>,
        blockedDeviceIds: Set<String>
    ): JSONObject {
        val db = getDb(context)
        val myName = getProfileName(context, myDeviceId) ?: registeredDevices[myDeviceId]?.name ?: "Вы"

        val meObj = JSONObject()
        meObj.put("id", myDeviceId)
        meObj.put("name", myName)

        val peersArr = JSONArray()

        // Combine all registered devices (excluding me) and any other device IDs in messages
        val peerIds = mutableSetOf<String>()
        for (devId in registeredDevices.keys) {
            if (devId != myDeviceId && !blockedDeviceIds.contains(devId)) {
                peerIds.add(devId)
            }
        }

        db.rawQuery(
            "SELECT DISTINCT sender_id FROM messages WHERE recipient_id = ? " +
            "UNION SELECT DISTINCT recipient_id FROM messages WHERE sender_id = ?",
            arrayOf(myDeviceId, myDeviceId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val pId = cursor.getString(0)
                if (pId != myDeviceId && !blockedDeviceIds.contains(pId)) {
                    peerIds.add(pId)
                }
            }
        }

        for (pId in peerIds) {
            val devSession = registeredDevices[pId]
            val customName = getProfileName(context, pId)
            val name = customName ?: devSession?.name ?: "Устройство $pId"
            val kind = devSession?.kind ?: "phone"
            val online = devSession?.isOnline() ?: false

            // Unread count
            var unread = 0
            db.rawQuery(
                "SELECT COUNT(*) FROM messages WHERE sender_id = ? AND recipient_id = ? AND is_read = 0 AND is_deleted = 0",
                arrayOf(pId, myDeviceId)
            ).use { cur ->
                if (cur.moveToFirst()) unread = cur.getInt(0)
            }

            // Last message
            var lastObj: JSONObject? = null
            db.rawQuery(
                """
                SELECT kind, text_enc, text_iv, timestamp
                FROM messages
                WHERE ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))
                  AND is_deleted = 0
                ORDER BY id DESC LIMIT 1
                """.trimIndent(),
                arrayOf(myDeviceId, pId, pId, myDeviceId)
            ).use { cur ->
                if (cur.moveToFirst()) {
                    val lKind = cur.getString(0)
                    val lEnc = cur.getBlob(1)
                    val lIv = cur.getBlob(2)
                    val lTime = cur.getLong(3)
                    val lText = decryptText(lEnc, lIv)

                    val lo = JSONObject()
                    lo.put("t", lTime)
                    lo.put("kind", lKind)
                    lo.put("preview", if (lKind == "text") lText else if (lKind == "voice") "Голосовое сообщение" else "Файл")
                    lastObj = lo
                }
            }

            val pObj = JSONObject()
            pObj.put("id", pId)
            pObj.put("name", name)
            pObj.put("kind", kind)
            pObj.put("online", online)
            pObj.put("unread", unread)
            pObj.put("last", lastObj ?: JSONObject.NULL)

            peersArr.put(pObj)
        }

        val res = JSONObject()
        res.put("me", meObj)
        res.put("peers", peersArr)
        return res
    }

    /**
     * Stores incoming file stream encrypted with AES-256-GCM in 64 KB chunks.
     */
    fun saveAttachmentStream(context: Context, inputStream: InputStream, expectedSize: Long): String {
        val fileId = UUID.randomUUID().toString().replace("-", "")
        val mailDir = File(context.filesDir, "mail")
        if (!mailDir.exists()) mailDir.mkdirs()

        val destFile = File(mailDir, "$fileId.bin")
        val key = getOrCreateKey()
        val random = SecureRandom()

        var totalRead = 0L
        var chunkIndex = 0

        FileOutputStream(destFile).use { fos ->
            val buffer = ByteArray(CHUNK_SIZE)
            var bytesInChunk: Int

            while (true) {
                // Read up to CHUNK_SIZE bytes
                var chunkOffset = 0
                while (chunkOffset < CHUNK_SIZE) {
                    val r = inputStream.read(buffer, chunkOffset, CHUNK_SIZE - chunkOffset)
                    if (r <= 0) break
                    chunkOffset += r
                }
                bytesInChunk = chunkOffset
                if (bytesInChunk == 0) break

                totalRead += bytesInChunk
                if (totalRead > MAX_FILE_SIZE) {
                    destFile.delete()
                    throw IllegalArgumentException("too_large")
                }

                // Encrypt chunk with AAD
                val iv = ByteArray(12)
                random.nextBytes(iv)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                val aad = "${fileId}_$chunkIndex".toByteArray(Charsets.UTF_8)
                cipher.updateAAD(aad)

                val cipherBytes = cipher.doFinal(buffer, 0, bytesInChunk)

                // Write [4-byte total cipher len][12-byte IV][cipherBytes]
                val lenBuf = ByteBuffer.allocate(4).putInt(cipherBytes.size).array()
                fos.write(lenBuf)
                fos.write(iv)
                fos.write(cipherBytes)

                chunkIndex++
            }
        }

        return fileId
    }

    fun getAttachmentFile(context: Context, fileId: String): File {
        return File(File(context.filesDir, "mail"), "$fileId.bin")
    }

    fun getAttachmentMeta(context: Context, fileId: String): Triple<String, Long, String>? {
        val db = getDb(context)
        db.rawQuery(
            "SELECT file_name, file_size, file_mime FROM messages WHERE file_id = ? AND is_deleted = 0 LIMIT 1",
            arrayOf(fileId)
        ).use { cur ->
            if (cur.moveToFirst()) {
                val name = cur.getString(0) ?: "file"
                val size = cur.getLong(1)
                val mime = cur.getString(2) ?: "application/octet-stream"
                return Triple(name, size, mime)
            }
        }
        return null
    }

    /**
     * Decrypts attachment streaming to output stream, supporting HTTP Range.
     */
    fun streamDecryptedAttachment(
        context: Context,
        fileId: String,
        outStream: OutputStream,
        rangeStart: Long = 0L,
        rangeEnd: Long = Long.MAX_VALUE
    ) {
        val encFile = getAttachmentFile(context, fileId)
        if (!encFile.exists()) return

        val key = getOrCreateKey()
        var currentPlainOffset = 0L
        var chunkIndex = 0

        FileInputStream(encFile).use { fis ->
            val lenBytes = ByteArray(4)
            while (fis.read(lenBytes) == 4) {
                val cipherLen = ByteBuffer.wrap(lenBytes).int
                val iv = ByteArray(12)
                if (fis.read(iv) != 12) break

                val cipherBytes = ByteArray(cipherLen)
                var readTotal = 0
                while (readTotal < cipherLen) {
                    val r = fis.read(cipherBytes, readTotal, cipherLen - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                if (readTotal != cipherLen) break

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                val aad = "${fileId}_$chunkIndex".toByteArray(Charsets.UTF_8)
                cipher.updateAAD(aad)

                val plainChunk = cipher.doFinal(cipherBytes)
                val chunkLen = plainChunk.size

                val chunkStart = currentPlainOffset
                val chunkEnd = currentPlainOffset + chunkLen - 1

                if (chunkEnd >= rangeStart && chunkStart <= rangeEnd) {
                    val sliceStart = (rangeStart - chunkStart).coerceAtLeast(0L).toInt()
                    val sliceEnd = (rangeEnd - chunkStart).coerceAtMost(chunkLen.toLong() - 1L).toInt()
                    val sliceLength = sliceEnd - sliceStart + 1
                    if (sliceLength > 0) {
                        outStream.write(plainChunk, sliceStart, sliceLength)
                    }
                }

                currentPlainOffset += chunkLen
                chunkIndex++

                if (currentPlainOffset > rangeEnd) break
            }
        }
    }
}
