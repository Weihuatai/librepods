package me.kavishdevar.librepods.services

import org.junit.Assert.*
import org.junit.Test

class A2dpSuspendSnapshotTest {
    @Test fun confirmedExternalAndAppliedBitsCanBeRead() {
        assertEquals(A2dpSuspendSnapshot(true, false, true), A2dpSuspendSnapshot.parse(
            "prefix\n  Bluetooth A2DP suspended, requested ext: true, requested int: false, applied true\nother"))
    }
    @Test fun requestedButNotAppliedIsNotReportedAsApplied() {
        assertEquals(A2dpSuspendSnapshot(true, false, false), A2dpSuspendSnapshot.parse(
            "Bluetooth A2DP suspended, requested ext: true, requested int: false, applied false"))
    }
    @Test fun absentOrVendorChangedDumpIsUnknown() {
        assertNull(A2dpSuspendSnapshot.parse("Bluetooth A2DP connected: true"))
        assertNull(A2dpSuspendSnapshot.parse("Bluetooth A2DP suspended, requested ext: true"))
    }
    @Test fun internalSuspendIsNotConfusedWithOurExternalRequest() {
        assertEquals(A2dpSuspendSnapshot(false, true, true), A2dpSuspendSnapshot.parse(
            "Bluetooth A2DP suspended, requested ext: false, requested int: true, applied true"))
    }
}
