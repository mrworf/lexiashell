package nu.sensenet.lexiashell

object LockTaskStartPolicy {
    fun shouldStart(isLockTaskPermitted: Boolean, isKeyguardLocked: Boolean): Boolean =
        isLockTaskPermitted && !isKeyguardLocked
}
