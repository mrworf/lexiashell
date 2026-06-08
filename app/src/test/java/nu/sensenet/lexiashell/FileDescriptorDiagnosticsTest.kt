package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Test

class FileDescriptorDiagnosticsTest {
    @Test
    fun categorizesKnownDescriptorTargets() {
        assertEquals(
            FileDescriptorCategory.KGSL,
            FileDescriptorDiagnostics.categoryForTarget("/dev/kgsl-3d0"),
        )
        assertEquals(
            FileDescriptorCategory.SYNC,
            FileDescriptorDiagnostics.categoryForTarget("anon_inode:sync_file"),
        )
        assertEquals(
            FileDescriptorCategory.SOCKET,
            FileDescriptorDiagnostics.categoryForTarget("socket:[12345]"),
        )
        assertEquals(
            FileDescriptorCategory.PIPE,
            FileDescriptorDiagnostics.categoryForTarget("pipe:[12345]"),
        )
        assertEquals(
            FileDescriptorCategory.ANON_INODE,
            FileDescriptorDiagnostics.categoryForTarget("anon_inode:[eventfd]"),
        )
    }

    @Test
    fun categorizesUnknownDescriptorTargetsAsOther() {
        assertEquals(
            FileDescriptorCategory.OTHER,
            FileDescriptorDiagnostics.categoryForTarget("/data/app/example/base.apk"),
        )
    }

    @Test
    fun snapshotsDescriptorCountsAndMaxFd() {
        val snapshot = FileDescriptorDiagnostics.snapshot(
            listOf(
                FileDescriptorEntry("0", "/dev/null"),
                FileDescriptorEntry("1", "pipe:[100]"),
                FileDescriptorEntry("2", "socket:[200]"),
                FileDescriptorEntry("90", "/dev/kgsl-3d0"),
                FileDescriptorEntry("91", "anon_inode:sync_file"),
                FileDescriptorEntry("92", "anon_inode:[eventfd]"),
                FileDescriptorEntry("93", null),
                FileDescriptorEntry("not-a-fd", "/data/app/example/base.apk"),
            ),
        )

        assertEquals(8, snapshot.total)
        assertEquals(93, snapshot.maxFd)
        assertEquals(1, snapshot.unreadable)
        assertEquals(1, snapshot.kgsl)
        assertEquals(1, snapshot.sync)
        assertEquals(1, snapshot.socket)
        assertEquals(1, snapshot.pipe)
        assertEquals(1, snapshot.anonInode)
        assertEquals(2, snapshot.other)
    }

    @Test
    fun formatsSnapshotLine() {
        assertEquals(
            "FD snapshot: total=3 maxFd=2 unreadable=1 kgsl=0 sync=1 socket=0 " +
                "pipe=0 anon_inode=0 other=1",
            FileDescriptorDiagnostics.snapshotLine(
                FileDescriptorSnapshot(
                    total = 3,
                    maxFd = 2,
                    unreadable = 1,
                    kgsl = 0,
                    sync = 1,
                    socket = 0,
                    pipe = 0,
                    anonInode = 0,
                    other = 1,
                ),
            ),
        )
    }

    @Test
    fun formatsSnapshotLineWithoutMaxFd() {
        assertEquals(
            "FD snapshot: total=0 maxFd=none unreadable=0 kgsl=0 sync=0 socket=0 " +
                "pipe=0 anon_inode=0 other=0",
            FileDescriptorDiagnostics.snapshotLine(
                FileDescriptorSnapshot(
                    total = 0,
                    maxFd = null,
                    unreadable = 0,
                    kgsl = 0,
                    sync = 0,
                    socket = 0,
                    pipe = 0,
                    anonInode = 0,
                    other = 0,
                ),
            ),
        )
    }
}
