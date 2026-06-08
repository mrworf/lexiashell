package nu.sensenet.lexiashell

import android.system.Os
import java.io.File

object FileDescriptorDiagnostics {
    fun captureProcSelf(): FileDescriptorSnapshot {
        val fdDirectory = File(PROC_SELF_FD)
        val entries = fdDirectory.listFiles().orEmpty().map { entry ->
            FileDescriptorEntry(
                name = entry.name,
                target = runCatching { Os.readlink(entry.path) }.getOrNull(),
            )
        }
        return snapshot(entries)
    }

    fun snapshot(entries: List<FileDescriptorEntry>): FileDescriptorSnapshot {
        val categoryCounts = mutableMapOf<FileDescriptorCategory, Int>().withDefault { 0 }
        var maxFd: Int? = null
        var unreadable = 0

        for (entry in entries) {
            val fd = entry.name.toIntOrNull()
            if (fd != null && (maxFd == null || fd > maxFd)) {
                maxFd = fd
            }

            val target = entry.target
            if (target == null) {
                unreadable += 1
                continue
            }

            val category = categoryForTarget(target)
            categoryCounts[category] = categoryCounts.getValue(category) + 1
        }

        return FileDescriptorSnapshot(
            total = entries.size,
            maxFd = maxFd,
            unreadable = unreadable,
            kgsl = categoryCounts.getValue(FileDescriptorCategory.KGSL),
            sync = categoryCounts.getValue(FileDescriptorCategory.SYNC),
            socket = categoryCounts.getValue(FileDescriptorCategory.SOCKET),
            pipe = categoryCounts.getValue(FileDescriptorCategory.PIPE),
            anonInode = categoryCounts.getValue(FileDescriptorCategory.ANON_INODE),
            other = categoryCounts.getValue(FileDescriptorCategory.OTHER),
        )
    }

    fun categoryForTarget(target: String): FileDescriptorCategory {
        val normalized = target.trim().lowercase()
        return when {
            "kgsl" in normalized -> FileDescriptorCategory.KGSL
            "sync" in normalized -> FileDescriptorCategory.SYNC
            normalized.startsWith("socket:") -> FileDescriptorCategory.SOCKET
            normalized.startsWith("pipe:") -> FileDescriptorCategory.PIPE
            normalized.startsWith("anon_inode:") -> FileDescriptorCategory.ANON_INODE
            else -> FileDescriptorCategory.OTHER
        }
    }

    fun snapshotLine(snapshot: FileDescriptorSnapshot): String =
        "FD snapshot: " +
            "total=${snapshot.total} " +
            "maxFd=${snapshot.maxFd ?: "none"} " +
            "unreadable=${snapshot.unreadable} " +
            "kgsl=${snapshot.kgsl} " +
            "sync=${snapshot.sync} " +
            "socket=${snapshot.socket} " +
            "pipe=${snapshot.pipe} " +
            "anon_inode=${snapshot.anonInode} " +
            "other=${snapshot.other}"

    private const val PROC_SELF_FD = "/proc/self/fd"
}

data class FileDescriptorEntry(
    val name: String,
    val target: String?,
)

data class FileDescriptorSnapshot(
    val total: Int,
    val maxFd: Int?,
    val unreadable: Int,
    val kgsl: Int,
    val sync: Int,
    val socket: Int,
    val pipe: Int,
    val anonInode: Int,
    val other: Int,
)

enum class FileDescriptorCategory {
    KGSL,
    SYNC,
    SOCKET,
    PIPE,
    ANON_INODE,
    OTHER,
}
