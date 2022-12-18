package net.veldor.tor_client.model.utils

interface ShellExitCode {
    companion object {
        const val TOR_ALREADY_RAN = 128
        const val SUCCESS = 0
        const val WATCHDOG_EXIT = -1
        const val SHELL_DIED = -2
        const val SHELL_EXEC_FAILED = -3
        const val SHELL_WRONG_UID = -4
        const val SHELL_NOT_FOUND = -5
        const val TERMINATED = 130
        const val COMMAND_NOT_EXECUTABLE = 126
        const val COMMAND_NOT_FOUND = 127
    }
}