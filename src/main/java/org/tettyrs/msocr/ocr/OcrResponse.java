package org.tettyrs.msocr.ocr;

import java.util.List;

public record OcrResponse(
        String ocrSource,
        EngineInfo engine,
        int pageCount,
        long durationMs,
        List<PageResult> pages) {

    public record EngineInfo(String name, String version) {
    }

    public record PageResult(
            int page,
            double width,
            double height,
            double deskewAngle,
            String text,
            List<Word> words) {
    }

    public record Word(String text, List<Double> bbox, double confidence) {
    }
}
