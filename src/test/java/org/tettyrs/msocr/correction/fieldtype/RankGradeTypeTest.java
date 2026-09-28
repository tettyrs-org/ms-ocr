package org.tettyrs.msocr.correction.fieldtype;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RankGradeTypeTest {

    @Test
    void acceptsMatchingPair(){
        FieldResult result = RankGradeType.correct("Penata Muda Tingkat I (III/b)");
        assertEquals("Penata Muda Tingkat I (III/b)", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
        assertEquals(List.of(), result.violations());
    }

    @Test
    void acceptsTolerantGradeMatchingRank(){
        FieldResult result = RankGradeType.correct("Penata Muda Tingkat I 1II.b");
        assertEquals("Penata Muda Tingkat I (III/b)", result.value());
        assertEquals("confusion_map", result.correction());
        assertEquals(0.70, result.confidence());
    }

    @Test
    void derivesFromRankWhenTolerantGradeDiffers(){
        FieldResult result = RankGradeType.correct("Penata Muda Tingkat I 1II-D");
        assertEquals("Penata Muda Tingkat I (III/b)", result.value());
        assertEquals("derived", result.correction());
        assertEquals(0.60, result.confidence());
    }

    @Test
    void keepsStrictGradeWithoutRank(){
        FieldResult result = RankGradeType.correct("III/b");
        assertEquals("III/b", result.value());
        assertEquals(1.0, result.confidence());
    }

    @Test
    void doesNotGuessBetweenTiedRanks(){
        FieldResult result = RankGradeType.correct("Pembina Utama Muda IV/d");
        assertEquals("Pembina Utama Muda (IV/d)", result.value());
        assertNull(result.correction());
    }

    @Test
    void keepsRawTextWhenNothingIsReadable(){
        FieldResult result = RankGradeType.correct("Pnt Md");
        assertEquals("Pnt Md", result.value());
        assertEquals(0.30, result.confidence());
    }

    @Test
    void derivesFromIncompleteRankWithMisreadGrade(){
        FieldResult result = RankGradeType.correct("Penata / III-€");
        assertEquals("Penata (III/c)", result.value());
        assertEquals("derived", result.correction());
        assertEquals(0.60, result.confidence());
    }

    @Test
    void flagsConflictingRankGolonganWithoutOverwriting(){
        FieldResult result = RankGradeType.correct("Penata Muda Tingkat I / III-d");
        assertEquals("Penata Muda Tingkat I (III/d)", result.value());
        assertNull(result.correction());
        assertEquals(0.50, result.confidence());
        assertEquals("pangkat_golongan_tidak_cocok", result.violations().get(0).rule());
    }
}