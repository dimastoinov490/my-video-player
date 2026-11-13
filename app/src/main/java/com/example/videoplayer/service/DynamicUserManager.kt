package com.example.videoplayer.service

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class DynamicUser(
    val id: String,
    var name: String,
    var salt: String,
    var hash: String,
    var prevSalt: String? = null,
    var prevHash: String? = null,
    var status: String = "active", // "active", "pending", "rejected"
    val created: Long = System.currentTimeMillis(),
    var approvedAt: Long? = null,
    var ip: String = "",
    var deviceName: String = "",
    var limitMin: Int = 0,
    var schedule: UserSchedule = UserSchedule(),
    var secretQuestion: String? = null,
    var secretAnswerSalt: String? = null,
    var secretAnswerHash: String? = null
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("salt", salt)
        obj.put("hash", hash)
        if (prevSalt != null) obj.put("prevSalt", prevSalt)
        if (prevHash != null) obj.put("prevHash", prevHash)
        obj.put("status", status)
        obj.put("created", created)
        if (approvedAt != null) obj.put("approvedAt", approvedAt)
        obj.put("ip", ip)
        obj.put("deviceName", deviceName)
        obj.put("limitMin", limitMin)
        obj.put("schedule", schedule.toJson())
        if (!secretQuestion.isNullOrBlank()) obj.put("secretQuestion", secretQuestion)
        if (!secretAnswerSalt.isNullOrBlank()) obj.put("secretAnswerSalt", secretAnswerSalt)
        if (!secretAnswerHash.isNullOrBlank()) obj.put("secretAnswerHash", secretAnswerHash)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): DynamicUser? {
            val id = obj.optString("id", "").trim()
            val name = obj.optString("name", "").trim()
            val salt = obj.optString("salt", "").trim()
            val hash = obj.optString("hash", "").trim()
            if (id.isEmpty() || name.isEmpty() || salt.isEmpty() || hash.isEmpty()) {
                return null
            }
            val prevSalt = if (obj.has("prevSalt") && !obj.isNull("prevSalt")) obj.optString("prevSalt") else null
            val prevHash = if (obj.has("prevHash") && !obj.isNull("prevHash")) obj.optString("prevHash") else null
            val status = obj.optString("status", "active")
            val created = obj.optLong("created", System.currentTimeMillis())
            val approvedAt = if (obj.has("approvedAt") && !obj.isNull("approvedAt")) obj.optLong("approvedAt") else null
            val ip = obj.optString("ip", "")
            val deviceName = obj.optString("deviceName", "")
            val limitMin = obj.optInt("limitMin", 0)
            val scheduleObj = obj.optJSONObject("schedule")
            val schedule = UserSchedule.fromJson(scheduleObj)
            val secretQuestion = if (obj.has("secretQuestion") && !obj.isNull("secretQuestion")) obj.optString("secretQuestion").trim().ifEmpty { null } else null
            val secretAnswerSalt = if (obj.has("secretAnswerSalt") && !obj.isNull("secretAnswerSalt")) obj.optString("secretAnswerSalt").trim().ifEmpty { null } else null
            val secretAnswerHash = if (obj.has("secretAnswerHash") && !obj.isNull("secretAnswerHash")) obj.optString("secretAnswerHash").trim().ifEmpty { null } else null

            return DynamicUser(
                id = id,
                name = name,
                salt = salt,
                hash = hash,
                prevSalt = prevSalt,
                prevHash = prevHash,
                status = status,
                created = created,
                approvedAt = approvedAt,
                ip = ip,
                deviceName = deviceName,
                limitMin = limitMin,
                schedule = schedule,
                secretQuestion = secretQuestion,
                secretAnswerSalt = secretAnswerSalt,
                secretAnswerHash = secretAnswerHash
            )
        }
    }
}

object DynamicUserManager {
    private const val TAG = "DynamicUserManager"
    private const val PREFS_NAME = "http_users"
    private const val KEY_USERS_JSON = "users_list"
    private const val KEY_REGISTRATION_OPEN = "registration_open"
    private const val KEY_FIXED_USER_PREV_SALT = "fixed_user_prev_salt"
    private const val KEY_FIXED_USER_PREV_HASH = "fixed_user_prev_hash"
    private const val KEY_FIXED_USER_SECRET_Q = "fixed_user_secret_question"
    private const val KEY_FIXED_USER_SECRET_SALT = "fixed_user_secret_salt"
    private const val KEY_FIXED_USER_SECRET_HASH = "fixed_user_secret_hash"

    val PRESET_SECRET_QUESTIONS = listOf(
        "Девичья фамилия матери",
        "Кличка первого питомца",
        "Город рождения",
        "Любимая книга"
    )

    private val usersMap = ConcurrentHashMap<String, DynamicUser>()
    @Volatile private var isLoaded = false
    private val random = SecureRandom()

    // Captcha store: id -> (code, timestamp)
    private data class CaptchaEntry(val code: String, val timestamp: Long)
    private val captchaStore = ConcurrentHashMap<String, CaptchaEntry>()
    private val captchaIpTimestamps = ConcurrentHashMap<String, MutableList<Long>>()

    // Registration IP timestamps for rate limiting: max 5 req / hour per IP
    private val registerIpTimestamps = ConcurrentHashMap<String, MutableList<Long>>()

    // Forgot password failure tracker: key (ip or "login:ip") -> list of failure timestamps
    private val forgotFailures = ConcurrentHashMap<String, MutableList<Long>>()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun loadIfNeeded(context: Context) {
        if (isLoaded) return
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_USERS_JSON, null)
        usersMap.clear()
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val arr = JSONArray(jsonStr)
                val now = System.currentTimeMillis()
                val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val user = DynamicUser.fromJson(obj) ?: continue
                    // Clean up rejected users older than 7 days
                    if (user.status == "rejected") {
                        val baseTime = user.approvedAt ?: user.created
                        if (now - baseTime > sevenDaysMs) {
                            continue
                        }
                    }
                    usersMap[user.id] = user
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse http_users JSON", e)
            }
        }
        isLoaded = true
    }

    @Synchronized
    private fun saveToDisk(context: Context) {
        val prefs = getPrefs(context)
        val arr = JSONArray()
        usersMap.values.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_USERS_JSON, arr.toString()).apply()
    }

    fun isRegistrationOpen(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REGISTRATION_OPEN, true)
    }

    fun setRegistrationOpen(context: Context, open: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REGISTRATION_OPEN, open).apply()
    }

    fun getFixedUserPrevSalt(context: Context): String? {
        return getPrefs(context).getString(KEY_FIXED_USER_PREV_SALT, null)
    }

    fun getFixedUserPrevHash(context: Context): String? {
        return getPrefs(context).getString(KEY_FIXED_USER_PREV_HASH, null)
    }

    fun setFixedUserPrevPassword(context: Context, saltHex: String, hashHex: String) {
        getPrefs(context).edit()
            .putString(KEY_FIXED_USER_PREV_SALT, saltHex)
            .putString(KEY_FIXED_USER_PREV_HASH, hashHex)
            .apply()
    }

    fun normalizeSecretAnswer(answer: String): String {
        return answer.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    fun hashSecretAnswer(answer: String): Pair<String, String> {
        val normalized = normalizeSecretAnswer(answer)
        val saltBytes = HttpServerConfig.generateSalt()
        val hashBytes = HttpServerConfig.hashPassword(normalized.toCharArray(), saltBytes)
        return Pair(HttpServerConfig.bytesToHex(saltBytes), HttpServerConfig.bytesToHex(hashBytes))
    }

    fun verifySecretAnswer(answer: String, saltHex: String?, hashHex: String?): Boolean {
        val normalized = normalizeSecretAnswer(answer)
        if (normalized.length < 3) return false
        return verifyPasswordWithSaltHash(normalized, saltHex, hashHex)
    }

    fun getFixedUserSecretQuestion(context: Context): String? {
        return getPrefs(context).getString(KEY_FIXED_USER_SECRET_Q, null)?.trim()?.ifEmpty { null }
    }

    fun getFixedUserSecretSalt(context: Context): String? {
        return getPrefs(context).getString(KEY_FIXED_USER_SECRET_SALT, null)
    }

    fun getFixedUserSecretHash(context: Context): String? {
        return getPrefs(context).getString(KEY_FIXED_USER_SECRET_HASH, null)
    }

    fun setFixedUserSecret(context: Context, question: String, answer: String) {
        val (saltHex, hashHex) = hashSecretAnswer(answer)
        getPrefs(context).edit()
            .putString(KEY_FIXED_USER_SECRET_Q, question.trim())
            .putString(KEY_FIXED_USER_SECRET_SALT, saltHex)
            .putString(KEY_FIXED_USER_SECRET_HASH, hashHex)
            .apply()
    }

    fun dummyQuestionForLogin(login: String): String {
        val idx = kotlin.math.abs(login.trim().lowercase().hashCode()) % PRESET_SECRET_QUESTIONS.size
        return PRESET_SECRET_QUESTIONS[idx]
    }

    fun getAllUsers(context: Context): List<DynamicUser> {
        loadIfNeeded(context)
        return usersMap.values.toList()
    }

    fun getActiveUsers(context: Context): List<DynamicUser> {
        loadIfNeeded(context)
        return usersMap.values.filter { it.status == "active" }
    }

    fun getPendingUsers(context: Context): List<DynamicUser> {
        loadIfNeeded(context)
        return usersMap.values.filter { it.status == "pending" }
    }

    fun getPendingCount(context: Context): Int {
        loadIfNeeded(context)
        return usersMap.values.count { it.status == "pending" }
    }

    fun findById(context: Context, id: String): DynamicUser? {
        loadIfNeeded(context)
        return usersMap[id]
    }

    fun findByName(context: Context, name: String): DynamicUser? {
        loadIfNeeded(context)
        return usersMap.values.find { it.name.equals(name, ignoreCase = true) }
    }

    fun isLoginTaken(context: Context, login: String, excludeId: String? = null): Boolean {
        loadIfNeeded(context)
        val adminName = HttpServerConfig.getAdminUsername(context)
        val userName = HttpServerConfig.getUsername(context)
        if (login.equals("admin", ignoreCase = true) || login.equals(adminName, ignoreCase = true)) {
            if (excludeId != "admin") return true
        }
        if (login.equals("user", ignoreCase = true) || login.equals(userName, ignoreCase = true)) {
            if (excludeId != "user") return true
        }
        val match = usersMap.values.find { it.name.equals(login, ignoreCase = true) }
        return match != null && match.id != excludeId
    }

    @Synchronized
    fun createUser(
        context: Context,
        name: String,
        password: String,
        secretQuestion: String? = null,
        secretAnswer: String? = null
    ): DynamicUser {
        loadIfNeeded(context)
        val saltBytes = HttpServerConfig.generateSalt()
        val hashBytes = HttpServerConfig.hashPassword(password.toCharArray(), saltBytes)
        val id = "u_" + UUID.randomUUID().toString().replace("-", "").take(10)
        val secretPair = if (!secretQuestion.isNullOrBlank() && !secretAnswer.isNullOrBlank()) {
            hashSecretAnswer(secretAnswer)
        } else null
        val user = DynamicUser(
            id = id,
            name = name.trim(),
            salt = HttpServerConfig.bytesToHex(saltBytes),
            hash = HttpServerConfig.bytesToHex(hashBytes),
            prevSalt = null,
            prevHash = null,
            status = "active",
            created = System.currentTimeMillis(),
            approvedAt = System.currentTimeMillis(),
            secretQuestion = secretQuestion?.trim()?.ifEmpty { null },
            secretAnswerSalt = secretPair?.first,
            secretAnswerHash = secretPair?.second
        )
        usersMap[id] = user
        saveToDisk(context)
        return user
    }

    @Synchronized
    fun registerPendingUser(
        context: Context,
        login: String,
        password: String,
        ip: String,
        deviceName: String,
        secretQuestion: String,
        secretAnswer: String
    ): DynamicUser {
        loadIfNeeded(context)
        val saltBytes = HttpServerConfig.generateSalt()
        val hashBytes = HttpServerConfig.hashPassword(password.toCharArray(), saltBytes)
        val (secSalt, secHash) = hashSecretAnswer(secretAnswer)
        val id = "u_" + UUID.randomUUID().toString().replace("-", "").take(10)
        val user = DynamicUser(
            id = id,
            name = login.trim(),
            salt = HttpServerConfig.bytesToHex(saltBytes),
            hash = HttpServerConfig.bytesToHex(hashBytes),
            prevSalt = null,
            prevHash = null,
            status = "pending",
            created = System.currentTimeMillis(),
            approvedAt = null,
            ip = ip,
            deviceName = deviceName,
            secretQuestion = secretQuestion.trim(),
            secretAnswerSalt = secSalt,
            secretAnswerHash = secHash
        )
        usersMap[id] = user
        saveToDisk(context)
        return user
    }

    @Synchronized
    fun decideRegistration(context: Context, id: String, approve: Boolean): Boolean {
        loadIfNeeded(context)
        val user = usersMap[id] ?: return false
        if (approve) {
            user.status = "active"
            user.approvedAt = System.currentTimeMillis()
        } else {
            user.status = "rejected"
            user.approvedAt = System.currentTimeMillis()
        }
        // Cleanup old rejected
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L
        val iterator = usersMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.status == "rejected") {
                val t = entry.value.approvedAt ?: entry.value.created
                if (now - t > sevenDaysMs) {
                    iterator.remove()
                }
            }
        }
        saveToDisk(context)
        return true
    }

    @Synchronized
    fun deleteUser(context: Context, id: String): Boolean {
        loadIfNeeded(context)
        val removed = usersMap.remove(id) != null
        if (removed) {
            saveToDisk(context)
            UserLimitsManager.clearAccountData(context, id)
        }
        return removed
    }

    @Synchronized
    fun updateCredentials(context: Context, id: String, newName: String?, newPassword: String?): Boolean {
        loadIfNeeded(context)
        val user = usersMap[id] ?: return false
        if (!newName.isNullOrBlank()) {
            user.name = newName.trim()
        }
        if (!newPassword.isNullOrEmpty()) {
            user.prevSalt = user.salt
            user.prevHash = user.hash
            val saltBytes = HttpServerConfig.generateSalt()
            val hashBytes = HttpServerConfig.hashPassword(newPassword.toCharArray(), saltBytes)
            user.salt = HttpServerConfig.bytesToHex(saltBytes)
            user.hash = HttpServerConfig.bytesToHex(hashBytes)
        }
        saveToDisk(context)
        return true
    }

    @Synchronized
    fun updateSecretQuestion(context: Context, id: String, question: String, answer: String): Boolean {
        loadIfNeeded(context)
        val user = usersMap[id] ?: return false
        val (secSalt, secHash) = hashSecretAnswer(answer)
        user.secretQuestion = question.trim()
        user.secretAnswerSalt = secSalt
        user.secretAnswerHash = secHash
        saveToDisk(context)
        return true
    }

    @Synchronized
    fun setLimits(context: Context, id: String, limitMin: Int, schedule: UserSchedule): Boolean {
        loadIfNeeded(context)
        val user = usersMap[id] ?: return false
        user.limitMin = limitMin
        user.schedule = schedule
        saveToDisk(context)
        return true
    }

    fun verifyUserPassword(user: DynamicUser, password: String): Boolean {
        return try {
            val salt = HttpServerConfig.hexToBytes(user.salt)
            val expectedHash = HttpServerConfig.hexToBytes(user.hash)
            val computedHash = HttpServerConfig.hashPassword(password.toCharArray(), salt)
            MessageDigest.isEqual(computedHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    fun verifyPasswordWithSaltHash(password: String, saltHex: String?, hashHex: String?): Boolean {
        if (saltHex.isNullOrEmpty() || hashHex.isNullOrEmpty()) return false
        return try {
            val salt = HttpServerConfig.hexToBytes(saltHex)
            val expectedHash = HttpServerConfig.hexToBytes(hashHex)
            val computedHash = HttpServerConfig.hashPassword(password.toCharArray(), salt)
            MessageDigest.isEqual(computedHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    // --- Captcha ---

    fun isCaptchaRateLimited(clientIp: String): Boolean {
        val now = System.currentTimeMillis()
        val list = captchaIpTimestamps.computeIfAbsent(clientIp) { mutableListOf() }
        synchronized(list) {
            list.removeAll { now - it > 60_000L }
            if (list.size >= 30) {
                return true
            }
            list.add(now)
            return false
        }
    }

    fun generateCaptcha(): Pair<String, String> {
        val now = System.currentTimeMillis()
        // Evict expired or overflow
        if (captchaStore.size > 200) {
            val iter = captchaStore.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (now - entry.value.timestamp > 300_000L) {
                    iter.remove()
                }
            }
            if (captchaStore.size > 200) {
                val oldest = captchaStore.entries.minByOrNull { it.value.timestamp }
                if (oldest != null) captchaStore.remove(oldest.key)
            }
        }

        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val codeBuilder = StringBuilder()
        for (i in 0 until 5) {
            codeBuilder.append(chars[random.nextInt(chars.length)])
        }
        val code = codeBuilder.toString()
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        captchaStore[id] = CaptchaEntry(code, now)

        // Draw Bitmap 300x104
        val bitmap = Bitmap.createBitmap(300, 104, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Noise lines
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 60, 60, 60)
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }
        val numLines = 5 + random.nextInt(3)
        for (k in 0 until numLines) {
            canvas.drawLine(
                random.nextFloat() * 300f,
                random.nextFloat() * 104f,
                random.nextFloat() * 300f,
                random.nextFloat() * 104f,
                linePaint
            )
        }

        // Noise dots
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 80, 80, 80)
            style = Paint.Style.FILL
        }
        for (d in 0 until 40) {
            canvas.drawCircle(random.nextFloat() * 300f, random.nextFloat() * 104f, 1.5f + random.nextFloat() * 2f, dotPaint)
        }

        // Draw 5 characters
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 34, 34)
            textSize = 54f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        for (j in 0 until 5) {
            val charStr = code[j].toString()
            val cx = 35f + j * 54f + (random.nextFloat() * 10f - 5f)
            val cy = 66f + (random.nextFloat() * 12f - 6f)
            val rotDeg = (random.nextFloat() * 40f) - 20f // +/- 20 degrees

            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(rotDeg)
            canvas.drawText(charStr, 0f, 0f, textPaint)
            canvas.restore()
        }

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val base64Data = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        val dataUri = "data:image/png;base64,$base64Data"

        return Pair(id, dataUri)
    }

    fun verifyCaptcha(id: String?, inputCode: String?): Boolean {
        if (id.isNullOrBlank() || inputCode.isNullOrBlank()) return false
        val entry = captchaStore.remove(id) ?: return false
        val now = System.currentTimeMillis()
        if (now - entry.timestamp > 300_000L) { // 5 minutes TTL
            return false
        }
        return entry.code.equals(inputCode.trim(), ignoreCase = true)
    }

    // --- Registration Rate Limiting ---

    fun canRegister(clientIp: String): Boolean {
        val now = System.currentTimeMillis()
        val list = registerIpTimestamps.computeIfAbsent(clientIp) { mutableListOf() }
        synchronized(list) {
            list.removeAll { now - it > 3600_000L } // 1 hour window
            if (list.size >= 5) {
                return false
            }
            return true
        }
    }

    fun recordRegistrationAttempt(clientIp: String) {
        val now = System.currentTimeMillis()
        val list = registerIpTimestamps.computeIfAbsent(clientIp) { mutableListOf() }
        synchronized(list) {
            list.add(now)
        }
    }

    // --- Forgot Password Rate Limiting ---

    fun isForgotLocked(clientIp: String, login: String): Boolean {
        val now = System.currentTimeMillis()
        val ipList = forgotFailures[clientIp]
        if (ipList != null) {
            synchronized(ipList) {
                ipList.removeAll { now - it > 900_000L } // 15 minutes window
                if (ipList.size >= 5) return true
            }
        }
        val comboKey = "${login.lowercase()}:$clientIp"
        val comboList = forgotFailures[comboKey]
        if (comboList != null) {
            synchronized(comboList) {
                comboList.removeAll { now - it > 900_000L }
                if (comboList.size >= 5) return true
            }
        }
        return false
    }

    fun recordForgotFailure(clientIp: String, login: String) {
        val now = System.currentTimeMillis()
        val ipList = forgotFailures.computeIfAbsent(clientIp) { mutableListOf() }
        synchronized(ipList) {
            ipList.add(now)
        }
        val comboKey = "${login.lowercase()}:$clientIp"
        val comboList = forgotFailures.computeIfAbsent(comboKey) { mutableListOf() }
        synchronized(comboList) {
            comboList.add(now)
        }
    }
}
