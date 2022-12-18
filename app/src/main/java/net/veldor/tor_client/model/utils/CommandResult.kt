package net.veldor.tor_client.model.utils

import net.veldor.tor_client.model.utils.ShellExitCode.Companion.SUCCESS


class CommandResult(
    val stdout: List<String>,
    val stderr: List<String>,
    val exitCode: Int
) :
    ShellExitCode {
    /**
     * Check if the exit code is 0.
     *
     * @return `true` if the [.exitCode] is equal to [ShellExitCode.SUCCESS].
     */
    val isSuccessful: Boolean
        get() = exitCode == SUCCESS

    /**
     * Get the standard output.
     *
     * @return The standard output as a string.
     */
    fun getStdout(): String {
        return toString(stdout)
    }

    /**
     * Get the standard error.
     *
     * @return The standard error as a string.
     */
    fun getStderr(): String {
        return toString(stderr)
    }

    override fun toString(): String {
        return getStdout()
    }

    companion object {
        private fun toString(lines: List<String>?): String {
            val sb = StringBuilder()
            if (lines != null) {
                var emptyOrNewLine = ""
                for (line in lines) {
                    sb.append(emptyOrNewLine).append(line)
                    emptyOrNewLine = "\n"
                }
            }
            return sb.toString()
        }
    }
}