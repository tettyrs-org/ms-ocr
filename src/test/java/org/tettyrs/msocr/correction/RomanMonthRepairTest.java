package org.tettyrs.msocr.correction;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RomanMonthRepairTest {

    @Test
    void repairsOneReadAsIInRomanMonth() {
        assertEquals(Optional.of("113/SPD/XI/2026"), RomanMonthRepair.repair("113/SPD/X1/2026"));
    }

    @Test
    void repairsLeadingOneInRomanMonth() {
        assertEquals(Optional.of("090/ST/IX/2026"), RomanMonthRepair.repair("090/ST/1X/2026"));
    }

    @Test
    void leavesCorrectRomanMonthUntouched() {
        assertEquals(Optional.empty(), RomanMonthRepair.repair("090/ST/IX/2026"));
    }

    @Test
    void ignoresNumericSegmentWithoutRomanLetters() {
        assertEquals(Optional.empty(), RomanMonthRepair.repair("090/ST/11/2026"));
    }

    @Test
    void rejectsResultThatIsNotAValidMonth() {
        assertEquals(Optional.empty(), RomanMonthRepair.repair("090/ST/XV1/2026"));
    }
}
