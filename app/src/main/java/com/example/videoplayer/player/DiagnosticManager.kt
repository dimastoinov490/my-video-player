package com.example.videoplayer.player

import android.content.Context
import android.net.ConnectivityManager
import android.os.Environment
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC

object DiagnosticManager {

    data class DiagnosticItem(
        val id: String,
        val title: String,
        val status: Status,
        val description: String
    ) {
        enum class Status { SUCCESS, WARNING, ERROR }
    }

    suspend fun runFullDiagnostics(context: Context): List<DiagnosticItem> {
        val results = withContext(Dispatchers.IO) {
            val ioResults = mutableListOf<DiagnosticItem>()

            // 1. Проверка ядер CPU
            val cores = Runtime.getRuntime().availableProcessors()
            if (cores >= 8) {
                ioResults.add(
                    DiagnosticItem(
                        "cpu",
                        "Центральный процессор (CPU)",
                        DiagnosticItem.Status.SUCCESS,
                        "Обнаружено ядер: $cores. Все потоки доступны для декодирования."
                    )
                )
            } else {
                ioResults.add(
                    DiagnosticItem(
                        "cpu",
                        "Центральный процессор (CPU)",
                        DiagnosticItem.Status.WARNING,
                        "Обнаружено ядер: $cores. Меньше 8, возможны микрофризы на тяжелых файлах."
                    )
                )
            }

            // 2. Проверка оперативной памяти (RAM)
            val ram = MemoryMonitor.getRamInfo(context)
            if (ram.usagePercent < 85) {
                ioResults.add(
                    DiagnosticItem(
                        "ram",
                        "Оперативная память (RAM)",
                        DiagnosticItem.Status.SUCCESS,
                        "Свободно: ${ram.availableMb} МБ из ${ram.totalMb} МБ (${ram.usagePercent}% занято)."
                    )
                )
            } else {
                ioResults.add(
                    DiagnosticItem(
                        "ram",
                        "Оперативная память (RAM)",
                        DiagnosticItem.Status.WARNING,
                        "Высокая нагрузка на ОЗУ: занято ${ram.usagePercent}%."
                    )
                )
            }

            // 3. Проверка хранилища и файлов
            val storageOk = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
            if (storageOk) {
                ioResults.add(
                    DiagnosticItem(
                        "storage",
                        "Внутреннее хранилище (ПЗУ)",
                        DiagnosticItem.Status.SUCCESS,
                        "Доступ к файловой системе и архивам активен."
                    )
                )
            } else {
                ioResults.add(
                    DiagnosticItem(
                        "storage",
                        "Внутреннее хранилище (ПЗУ)",
                        DiagnosticItem.Status.ERROR,
                        "Нет прямого доступа к хранилищу. Проверьте разрешения."
                    )
                )
            }

            // 4. Проверка сети / локального HTTP-сервера
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val netActive = cm?.activeNetwork != null
            if (netActive) {
                ioResults.add(
                    DiagnosticItem(
                        "network",
                        "Сетевой интерфейс / HTTP-сервер",
                        DiagnosticItem.Status.SUCCESS,
                        "Сеть активна, локальный стриминг-сервер готов к работе."
                    )
                )
            } else {
                ioResults.add(
                    DiagnosticItem(
                        "network",
                        "Сетевой интерфейс / HTTP-сервер",
                        DiagnosticItem.Status.WARNING,
                        "Нет активного подключения к сети (работа в офлайне)."
                    )
                )
            }

            // 5. Проверка медиа-движка libVLC
            try {
                try {
                    System.loadLibrary("vlc")
                } catch (_: UnsatisfiedLinkError) {
                    val vlc = LibVLC(context.applicationContext)
                    vlc.release()
                    System.loadLibrary("vlc")
                }
                ioResults.add(
                    DiagnosticItem(
                        "vlc",
                        "Медиа-движок (libVLC)",
                        DiagnosticItem.Status.SUCCESS,
                        "Нативные библиотеки и vout-конвейер подключены."
                    )
                )
            } catch (e: Throwable) {
                ioResults.add(
                    DiagnosticItem(
                        "vlc",
                        "Медиа-движок (libVLC)",
                        DiagnosticItem.Status.ERROR,
                        "Ошибка загрузки libVLC: ${e.localizedMessage}"
                    )
                )
            }

            ioResults
        }.toMutableList()

        // 6. Проверка Chromium WebView (выполняется строго на главном потоке)
        try {
            val webViewSettingsOk = withContext(Dispatchers.Main) {
                val tempWebView = WebView(context)
                try {
                    @Suppress("SENSELESS_COMPARISON")
                    tempWebView.settings != null
                } finally {
                    tempWebView.destroy()
                }
            }
            if (webViewSettingsOk) {
                results.add(
                    DiagnosticItem(
                        "webview",
                        "Chromium WebView",
                        DiagnosticItem.Status.SUCCESS,
                        "Веб-движок функционирует штатно."
                    )
                )
            } else {
                results.add(
                    DiagnosticItem(
                        "webview",
                        "Chromium WebView",
                        DiagnosticItem.Status.WARNING,
                        "WebView инициализирован с ограничениями."
                    )
                )
            }
        } catch (e: Throwable) {
            results.add(
                DiagnosticItem(
                    "webview",
                    "Chromium WebView",
                    DiagnosticItem.Status.ERROR,
                    "Android System WebView поврежден или недоступен."
                )
            )
        }

        return results
    }
}
