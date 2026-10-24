package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProtocolCheckTest {
    @Test
    void sameVersion_isFine() {
        assertTrue(ProtocolCheck.problem(2, 2).isEmpty());
    }

    @Test
    void oldClientThatSendsNothing_isRejectedWithBothVersionsInTheMessage() {
        String message = ProtocolCheck.problem(2, ProtocolCheck.UNKNOWN).orElseThrow();
        assertTrue(message.contains("2"));
        assertTrue(message.contains("Litematica MaterialShare"));
    }

    @Test
    void differentVersion_isRejectedAndNamesBoth() {
        String message = ProtocolCheck.problem(3, 2).orElseThrow();
        assertTrue(message.contains("3") && message.contains("2"));
    }
}
