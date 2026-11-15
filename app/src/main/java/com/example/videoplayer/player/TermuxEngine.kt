package com.example.videoplayer.player

import android.content.Context
import java.io.File

object TermuxEngine {

    private fun nativeFile(context: Context, name: String): File =
        File(context.applicationInfo.nativeLibraryDir, name)

    fun isProotAvailable(context: Context): Boolean {
        // Android extracts jniLibs into nativeLibraryDir. PRoot itself is an
        // executable ELF despite the .so suffix used for APK extraction.
        return listOf(
            nativeFile(context, "libproot.so"),
            nativeFile(context, "libproot_loader.so"),
            nativeFile(context, "libtalloc.so"),
            nativeFile(context, "libandroid-shmem.so")
        ).all { it.isFile && it.canRead() } &&
            nativeFile(context, "libproot.so").canExecute() &&
            nativeFile(context, "libproot_loader.so").canExecute()
    }

    fun isRootFsReady(context: Context): Boolean {
        val rootFs = File(context.filesDir, "rootfs")
        return rootFs.isDirectory &&
            File(rootFs, "bin/sh").isFile &&
            File(rootFs, "etc").isDirectory
    }

    fun createTermuxSession(context: Context): ProcessBuilder {
        val rootFs = File(context.filesDir, "rootfs")
        require(isProotAvailable(context)) {
            "PRoot runtime is incomplete: expected proot, loader, libtalloc and libandroid-shmem in the APK"
        }
        require(isRootFsReady(context)) { "Alpine root filesystem is not installed or is incomplete" }

        val nativeDir = context.applicationInfo.nativeLibraryDir
        val prootPath = nativeFile(context, "libproot.so").absolutePath
        val loaderPath = nativeFile(context, "libproot_loader.so").absolutePath
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
        env["PROOT_LOADER"] = loaderPath
        env["LD_LIBRARY_PATH"] = nativeDir
        pb.redirectErrorStream(true)
        return pb
    }
}
