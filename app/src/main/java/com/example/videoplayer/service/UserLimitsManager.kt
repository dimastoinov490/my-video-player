package com.example.videoplayer.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

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

    @Volatile private var cachedUsedTodaySec: Long = 0L
    @Volatile private var cachedLastDate: String = ""
    @Volatile private var cachedLimitMin: Int = 0
    @Volatile private var cachedSchedule: UserSchedule = UserSchedule()
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
        cachedUsedTodaySec = prefs.getLong(KEY_USED_TODAY_SEC, 0L)
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
            cachedUsedTodaySec = 0L
            saveToDisk(context, force = true)
        }
    }

    @Synchronized
    private fun saveToDisk(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastDiskSaveTime < 30_000L)) {
            return
        }
        lastDiskSaveTime = now
        val prefs = getPrefs(context)
        prefs.edit()
            .putInt(KEY_LIMIT_MIN, cachedLimitMin)
            .putLong(KEY_USED_TODAY_SEC, cachedUsedTodaySec)
            .putString(KEY_LAST_DATE, cachedLastDate)
            .putString(KEY_SCHEDULE_JSON, cachedSchedule.toJson().toString())
            .apply()
    }

    fun getUsedTodaySeconds(context: Context): Long {
        loadIfNeeded(context)
        checkDateResetLocked(context)
        return cachedUsedTodaySec
    }

    fun getLimitMin(context: Context): Int {
        loadIfNeeded(context)
        return cachedLimitMin
    }

    fun getLimitMinutes(context: Context): Int = getLimitMin(context)

    fun getRemainingSeconds(context: Context): Long? {
        loadIfNeeded(context)
        checkDateResetLocked(context)
        if (cachedLimitMin <= 0) return null
        val totalAllowed = cachedLimitMin * 60L
        return maxOf(0L, totalAllowed - cachedUsedTodaySec)
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
    fun addActiveSeconds(context: Context, seconds: Long) {
        if (seconds <= 0) return
        loadIfNeeded(context)
        checkDateResetLocked(context)
        cachedUsedTodaySec += seconds
        val isExceeded = cachedLimitMin > 0 && cachedUsedTodaySec >= cachedLimitMin * 60L
        saveToDisk(context, force = isExceeded)
    }

    @Synchronized
    fun setLimits(context: Context, limitMin: Int, schedule: UserSchedule) {
        loadIfNeeded(context)
        checkDateResetLocked(context)
        cachedLimitMin = limitMin.coerceIn(0, 1440)
        cachedSchedule = schedule
        saveToDisk(context, force = true)
    }

    fun isTimeLimitExceeded(context: Context): Boolean {
        loadIfNeeded(context)
        checkDateResetLocked(context)
        if (cachedLimitMin <= 0) return false
        return cachedUsedTodaySec >= (cachedLimitMin * 60L)
    }

    fun isScheduleAllowed(context: Context): Boolean {
        loadIfNeeded(context)
        val schedule = cachedSchedule
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
                // Part 1: current day started today
                (w.days.contains(dayOfWeek) && currentMinute >= fMin) ||
                // Part 2: current day is day after start
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
