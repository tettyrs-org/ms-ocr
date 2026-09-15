package org.tettyrs.msocr.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;
class OcrEngineClientTest {

    @Test
    void deserializesOcrResponse(){
        var engine = new OcrResponse.EngineInfo("paddleocr", "2.7.3");
        var word = new OcrResponse.Word("SURAT", List.of(0.101, 0.052, 0.198, 0.071), 0.996);
        var page = new OcrResponse.PageResult(1, 2480, 3508, -1.4, "SURAT", List.of(word));
        var response = new OcrResponse("ocr", engine, 1, 1840, List.of(page));

        assertNotNull(response);
        assertEquals("ocr", response.ocrSource());
        assertEquals(1, response.pageCount());
        assertEquals(1, response.pages().size());
    }

}