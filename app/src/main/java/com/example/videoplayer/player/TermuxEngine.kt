package com.example.videoplayer.player

import android.content.Context
import java.io.File

object TermuxEngine {

    fun isProotAvailable(context: Context): Boolean {
        val prootFile = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        return prootFile.isFile && prootFile.canExecute()
    }

    fun isRootFsReady(context: Context): Boolean {
        val rootFs = File(context.filesDir, "rootfs")
        return rootFs.isDirectory && File(rootFs, "bin/sh").exists()
    }

    fun createTermuxSession(context: Context): ProcessBuilder {
        val rootFs = File(context.filesDir, "rootfs")
        val prootPath = File(context.applicationInfo.nativeLibraryDir, "libproot.so").absolutePath

        // Ищем системный бинарник su на телефоне (Magisk / Superuser)
        val hostSuPath = findHostSuBinary()

        val commandList = mutableListOf(
            prootPath,
            "--link2symlink",      // Эмуляция симлинков для работы apt/apk
            "--sysvipc",           // Поддержка разделяемой памяти
            "-r", rootFs.absolutePath, // Корневая папка Linux

            // Стандартные системные точки монтирования
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/sdcard",       // Доступ к внутренней памяти телефона
            "-b", "${context.filesDir}:/app_data" // Доступ к папке приложения
        )

        // Если хост рутован, пробрасываем системный su внутрь контейнера
        if (hostSuPath != null) {
            commandList.add("-b")
            commandList.add("$hostSuPath:/bin/su")
        }

        commandList.add("-w")
        commandList.add("/root")   // Рабочая директория по умолчанию
        commandList.add("/bin/sh") // Оболочка
        commandList.add("-l")

        val pb = ProcessBuilder(commandList)

        // Переменные окружения, как в Termux
        val env = pb.environment()
        env["HOME"] = "/root"
        env["TERM"] = "xterm-256color"
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/bin/su"
        env["USER"] = "root"

        pb.redirectErrorStream(true)
        return pb
    }

    // Автопоиск бинарника su в стандартных путях Android
    private fun findHostSuBinary(): String? {
        val possiblePaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/su/bin"
        )
        for (path in possiblePaths) {
            try {
                val file = File(path)
                if (file.exists() && file.isFile && file.canExecute()) {
                    return path
                }
            } catch (_: SecurityException) {
            }
        }
        return null
    }
}
