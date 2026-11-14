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
        return rootFs.isDirectory && File(rootFs, "bin/sh").isFile
    }

    fun createTermuxSession(context: Context): ProcessBuilder {
        val rootFs = File(context.filesDir, "rootfs")
        require(isProotAvailable(context)) { "PRoot runtime is missing from the APK" }
        require(isRootFsReady(context)) { "Alpine root filesystem is not installed" }

        val prootPath = File(context.applicationInfo.nativeLibraryDir, "libproot.so").absolutePath
        val commandList = mutableListOf(
            prootPath,
            "--link2symlink",
            "--sysvipc",
            "-0", // Fake root inside PRoot; does not grant Android/system root access.
            "-r", rootFs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/sdcard",
            "-b", "${context.filesDir}:/app_data",
            "-w", "/root",
            "/bin/sh",
            "-l"
        )

        val pb = ProcessBuilder(commandList)
        val env = pb.environment()
        env["HOME"] = "/root"
        env["TERM"] = "xterm-256color"
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["USER"] = "root"
        pb.redirectErrorStream(true)
        return pb
    }
}
