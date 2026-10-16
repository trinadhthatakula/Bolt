package com.valhalla.bolt.model

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ShellTest {
    @Test fun nonzeroExitNeverMeansSuccess(): Unit = runBlocking {
        val result = Shell(false).execute("printf 'failed'; exit 7")
        assertEquals(7, result.exitCode)
        assertFalse(result.isSuccess)
        assertThrows(java.io.IOException::class.java) { result.requireSuccess("installer") }
    }
    @Test fun environmentIsAvailableInsideTheInstallerProcess(): Unit = runBlocking {
        val result = Shell(false).execute("AKHOME=${shellQuote("a b'c\$d")} sh -c 'printf \"%s\" \"\$AKHOME\"'")
        assertTrue(result.isSuccess)
        assertEquals("a b'c\$d", result.output)
    }
    @Test fun metacharactersAreDataNotCommands(): Unit = runBlocking {
        val name = "space ' quote \$(printf injected) `printf injected` ; > /bad"
        val result = Shell(false).execute("printf '%s' ${shellQuote(name)}")
        assertEquals(name, result.output)
    }
    @Test fun stderrFloodDrainsWhileStdoutIsStillOpen(): Unit = runBlocking {
        var streamed = 0
        val result = Shell(false).execute("i=0; while [ \"\$i\" -lt 16000 ]; do printf 'long error line long error line long error line\\n' >&2; i=\$((i+1)); done; printf 'finished'", 10) { streamed += it.length }
        assertTrue(result.isSuccess)
        assertTrue(streamed > 500000)
        assertTrue(result.output.endsWith("finished"))
        assertTrue(result.output.length <= 65536)
    }
    @Test fun readOnlyProbeTimeoutIsReportedAsFailure(): Unit = runBlocking {
        val result = Shell(false).execute("exec sleep 4", 1)
        assertNull(result.exitCode)
        assertFalse(result.isSuccess)
        assertEquals("Timed out", result.error)
    }

    @Test fun inheritedPipeDoesNotHangAfterTheParentExits(): Unit = runBlocking {
        val start = System.nanoTime()
        val result = Shell(false).execute("sleep 4 & exit 0")
        assertFalse(result.isSuccess)
        assertTrue(result.error!!.contains("child process"))
        assertTrue((System.nanoTime() - start) / 1_000_000_000 < 4)
    }
}
