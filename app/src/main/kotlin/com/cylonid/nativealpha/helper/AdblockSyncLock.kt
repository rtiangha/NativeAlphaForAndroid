package com.cylonid.nativealpha.helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * Cross-process lock so that only one process (the main process or one of the sandbox processes) syncs and
 * downloads the adblock filter lists at a time. Web apps in different sandboxes run in different processes, but
 * share the same filter files, so simultaneous downloads could write the same files at once.
 *
 * A file lock is used because the OS releases it automatically if the holding process dies. As a safety net the
 * lock is also released after [MAX_HOLD_MS], in case a download hangs.
 */
internal object AdblockSyncLock {
    private const val LOCK_FILE = "adblock_sync.lock"
    private const val MAX_HOLD_MS = 10 * 60 * 1000L

    private val handler = Handler(Looper.getMainLooper())
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /** @return true if this process may sync the lists now, false if another process (or an earlier sync in this one) holds the lock. */
    @Synchronized
    fun tryAcquire(context: Context): Boolean {
        if (lock != null) return false // an earlier sync in this process is still running

        return try {
            val file = File(context.applicationContext.filesDir, LOCK_FILE)
            val newChannel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val newLock = newChannel.tryLock()
            if (newLock == null) {
                newChannel.close()
                false
            } else {
                channel = newChannel
                lock = newLock
                handler.postDelayed({ release() }, MAX_HOLD_MS)
                true
            }
        } catch (e: OverlappingFileLockException) {
            false
        } catch (e: IOException) {
            true // locking is not possible here: fail open, i.e. behave as before
        }
    }

    @Synchronized
    fun release() {
        handler.removeCallbacksAndMessages(null)
        try {
            lock?.release()
        } catch (e: IOException) {
            // already released or channel closed
        }
        try {
            channel?.close()
        } catch (e: IOException) {
            // nothing to do
        }
        lock = null
        channel = null
    }
}
