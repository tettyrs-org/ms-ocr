package org.tettyrs.msocr.correction.tokens;

import java.util.List;
public record Token(String text, List<Double> bbox, double confidence, int sourceIndex) {
    double x0(){
        return bbox.get(0);
    }

    double centerY(){
        return (bbox.get(1) + bbox.get(3)) / 2;
    }

    double height(){
        return bbox.get(3) - bbox.get(1);
    }

    Token withText(String newText){
        return new Token(newText, bbox, confidence, sourceIndex);
    }
}
