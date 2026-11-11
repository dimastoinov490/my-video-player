package com.example.videoplayer.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class ScheduleWindow(
    val days: List<Int>, // 0..6 (0 = Sunday)
    val from: String,    // "HH:MM"
    val to: String       // "HH:MM"
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        val daysArr = JSONArray()
        days.forEach { daysArr.put(it) }
        obj.put("days", daysArr)
        obj.put("from", from)
        obj.put("to", to)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): ScheduleWindow? {
            val from = obj.optString("from", "").trim()
            val to = obj.optString("to", "").trim()
            val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
            if (!from.matches(timeRegex) || !to.matches(timeRegex) || from == to) {
                return null
            }
            val daysArr = obj.optJSONArray("days")
            val daysList = mutableListOf<Int>()
            if (daysArr != null && daysArr.length() > 0) {
                for (i in 0 until daysArr.length()) {
                    val d = daysArr.optInt(i, -1)
                    if (d in 0..6 && !daysList.contains(d)) {
                        daysList.add(d)
                    }
                }
            }
            val effectiveDays = if (daysList.isEmpty()) listOf(0, 1, 2, 3, 4, 5, 6) else daysList
            return ScheduleWindow(effectiveDays, from, to)
        }
    }
}

data class UserSchedule(
    val mode: String = "off", // "off", "allow", "deny"
    val windows: List<ScheduleWindow> = emptyList()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("mode", mode)
        val arr = JSONArray()
        windows.forEach { arr.put(it.toJson()) }
        obj.put("windows", arr)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject?): UserSchedule {
            if (obj == null) return UserSchedule()
            val mode = when (obj.optString("mode", "off")) {
                "allow" -> "allow"
                "deny" -> "deny"
                else -> "off"
            }
            if (mode == "off") {
                return UserSchedule(mode = "off", windows = emptyList())
            }
            val arr = obj.optJSONArray("windows") ?: JSONArray()
            val list = mutableListOf<ScheduleWindow>()
            for (i in 0 until arr.length()) {
                val wObj = arr.optJSONObject(i) ?: continue
                val window = ScheduleWindow.fromJson(wObj) ?: return UserSchedule(mode = "off", windows = emptyList())
                list.add(window)
            }
            return UserSchedule(mode = mode, windows = list)
        }
    }
}

object UserLimitsManager {
    private const val TAG = "UserLimitsManager"
    private const val PREFS_NAME = "http_user_limits"

    private const val KEY_LIMIT_MIN = "limit_min"
    private const val KEY_USED_TODAY_SEC = "used_today_sec"
    private const val KEY_LAST_DATE = "last_date"
    private const val KEY_SCHEDULE_JSON = "schedule_json"
    private const val KEY_ACCOUNTS_USAGE_JSON = "accounts_usage_json"

    // Multi-account usage map: accountId -> usedSeconds
    private val accountUsedSec = ConcurrentHashMap<String, Long>()

    @Volatile private var cachedLastDate: String = ""
    @Volatile private var cachedLimitMin: Int = 0 // for fixed "user"
    @Volatile private var cachedSchedule: UserSchedule = UserSchedule() // for fixed "user"
    @Volatile private var lastDiskSaveTime: Long = 0L
    @Volatile private var isLoaded = false

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    private fun loadIfNeeded(context: Context) {
        if (isLoaded) return
        val prefs = getPrefs(context)
        cachedLimitMin = prefs.getInt(KEY_LIMIT_MIN, 0)
        val legacyUsed = prefs.getLong(KEY_USED_TODAY_SEC, 0L)
        cachedLastDate = prefs.getString(KEY_LAST_DATE, "") ?: ""
        val schedStr = prefs.getString(KEY_SCHEDULE_JSON, null)
        cachedSchedule = if (!schedStr.isNullOrEmpty()) {
            try {
                UserSchedule.fromJson(JSONObject(schedStr))
            } catch (_: Exception) {
                UserSchedule()
            }
        } else {
            UserSchedule()
        }

        accountUsedSec.clear()
        if (legacyUsed > 0L) {
            accountUsedSec["user"] = legacyUsed
        }
        val usageStr = prefs.getString(KEY_ACCOUNTS_USAGE_JSON, null)
        if (!usageStr.isNullOrEmpty()) {
            try {
                val usageObj = JSONObject(usageStr)
                val keys = usageObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    accountUsedSec[k] = usageObj.optLong(k, 0L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing usage json: ${e.message}")
            }
        }

        isLoaded = true
        checkDateResetLocked(context)
    }

    private fun getCurrentDateString(): String {
        return timeFormat.format(Date())
    }

    @Synchronized
    private fun checkDateResetLocked(context: Context) {
        val today = getCurrentDateString()
        if (cachedLastDate != today) {
            cachedLastDate = today
            accountUsedSec.clear()
            saveToDisk(context, force = true)
        }
    }

    @Synchronized
    private fun saveToDisk(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastDiskSaveTime < 15_000L)) {
            return
        }
        lastDiskSaveTime = now
        val prefs = getPrefs(context)
        val usageObj = JSONObject()
        accountUsedSec.forEach { (accId, sec) -> usageObj.put(accId, sec) }

        prefs.edit()
            .putInt(KEY_LIMIT_MIN, cachedLimitMin)
            .putLong(KEY_USED_TODAY_SEC, accountUsedSec["user"] ?: 0L)
            .putString(KEY_LAST_DATE, cachedLastDate)
            .putString(KEY_SCHEDULE_JSON, cachedSchedule.toJson().toString())
            .putString(KEY_ACCOUNTS_USAGE_JSON, usageObj.toString())
            .apply()
    }

    fun getUsedTodaySeconds(context: Context, accountId: String = "user"): Long {
        loadIfNeeded(context)
        checkDateResetLocked(context)
        return accountUsedSec[accountId] ?: 0L
    }

    fun getLimitMin(context: Context): Int {
        loadIfNeeded(context)
        return cachedLimitMin
    }

    fun getLimitMinutes(context: Context): Int = getLimitMin(context)

    fun getRemainingSeconds(context: Context, accountId: String, limitMin: Int): Long? {
        loadIfNeeded(context)
        checkDateResetLocked(context)
        if (limitMin <= 0) return null
        val used = accountUsedSec[accountId] ?: 0L
        val totalAllowed = limitMin * 60L
        return maxOf(0L, totalAllowed - used)
    }

    fun getRemainingSeconds(context: Context): Long? {
        return getRemainingSeconds(context, "user", getLimitMin(context))
    }

    fun getSchedule(context: Context): UserSchedule {
        loadIfNeeded(context)
        return cachedSchedule
    }

    fun getScheduleJson(context: Context): JSONObject? {
        loadIfNeeded(context)
        return if (cachedSchedule.mode == "off" && cachedSchedule.windows.isEmpty()) {
            null
        } else {
            cachedSchedule.toJson()
        }
    }

    fun getScheduleJsonObject(context: Context): JSONObject? = getScheduleJson(context)

    @Synchronized
    fun addActiveSeconds(context: Context, accountId: String, seconds: Long) {
        if (seconds <= 0) return
        loadIfNeeded(context)
        checkDateResetLocked(context)
        val current = accountUsedSec[accountId] ?: 0L
        accountUsedSec[accountId] = current + seconds
        saveToDisk(context, force = false)
    }

    fun addActiveSeconds(context: Context, seconds: Long) {
        addActiveSeconds(context, "user", seconds)
    }

    @Synchronized
    fun setLimits(context: Context, limitMin: Int, schedule: UserSchedule) {
        loadIfNeeded(context)
        checkDateResetLocked(context)
        cachedLimitMin = limitMin.coerceIn(0, 1440)
        cachedSchedule = schedule
        saveToDisk(context, force = true)
    }

    fun clearAccountData(context: Context, accountId: String) {
        loadIfNeeded(context)
        accountUsedSec.remove(accountId)
        saveToDisk(context, force = true)
    }

    fun isTimeLimitExceeded(context: Context, accountId: String, limitMin: Int): Boolean {
        loadIfNeeded(context)
        checkDateResetLocked(context)
        if (limitMin <= 0) return false
        val used = accountUsedSec[accountId] ?: 0L
        return used >= (limitMin * 60L)
    }

    fun isTimeLimitExceeded(context: Context): Boolean {
        return isTimeLimitExceeded(context, "user", getLimitMin(context))
    }

    fun isScheduleAllowed(schedule: UserSchedule): Boolean {
        if (schedule.mode == "off" || schedule.windows.isEmpty()) {
            return true
        }

        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday, 1 = Monday, ..., 6 = Saturday
        val currentMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        var matchedAnyWindow = false
        for (w in schedule.windows) {
            val fParts = w.from.split(":")
            val tParts = w.to.split(":")
            if (fParts.size != 2 || tParts.size != 2) continue
            val fMin = fParts[0].toInt() * 60 + fParts[1].toInt()
            val tMin = tParts[0].toInt() * 60 + tParts[1].toInt()

            val inside = if (fMin < tMin) {
                w.days.contains(dayOfWeek) && currentMinute in fMin until tMin
            } else {
                // Crossing midnight: from day of start until next day's to
                (w.days.contains(dayOfWeek) && currentMinute >= fMin) ||
                (w.days.contains((dayOfWeek + 6) % 7) && currentMinute < tMin)
            }

            if (inside) {
                matchedAnyWindow = true
                break
            }
        }

        return when (schedule.mode) {
            "allow" -> matchedAnyWindow
            "deny" -> !matchedAnyWindow
            else -> true
        }
    }

    fun isScheduleAllowed(context: Context): Boolean {
        loadIfNeeded(context)
        return isScheduleAllowed(cachedSchedule)
    }

    fun validateLimits(limitMin: Int, scheduleObj: JSONObject?): Pair<Boolean, UserSchedule?> {
        if (limitMin !in 0..1440) return Pair(false, null)
        if (scheduleObj == null) {
            return Pair(true, UserSchedule(mode = "off", windows = emptyList()))
        }
        val mode = scheduleObj.optString("mode", "off")
        if (mode !in listOf("off", "allow", "deny")) return Pair(false, null)
        if (mode == "off") {
            return Pair(true, UserSchedule(mode = "off", windows = emptyList()))
        }
        val windowsArr = scheduleObj.optJSONArray("windows") ?: return Pair(false, null)
        if (windowsArr.length() > 8) return Pair(false, null)
        if (windowsArr.length() == 0) {
            return Pair(true, UserSchedule(mode = "off", windows = emptyList()))
        }

        val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        val parsedWindows = mutableListOf<ScheduleWindow>()

        for (i in 0 until windowsArr.length()) {
            val wObj = windowsArr.optJSONObject(i) ?: return Pair(false, null)
            val from = wObj.optString("from", "").trim()
            val to = wObj.optString("to", "").trim()
            if (!from.matches(timeRegex) || !to.matches(timeRegex) || from == to) {
                return Pair(false, null)
            }
            val daysArr = wObj.optJSONArray("days")
            val daysList = mutableListOf<Int>()
            if (daysArr != null && daysArr.length() > 0) {
                for (dIdx in 0 until daysArr.length()) {
                    val d = daysArr.optInt(dIdx, -1)
                    if (d !in 0..6) return Pair(false, null)
                    if (!daysList.contains(d)) daysList.add(d)
                }
            }
            val effectiveDays = if (daysList.isEmpty()) listOf(0, 1, 2, 3, 4, 5, 6) else daysList
            parsedWindows.add(ScheduleWindow(effectiveDays, from, to))
        }

        return Pair(true, UserSchedule(mode = mode, windows = parsedWindows))
    }
}
