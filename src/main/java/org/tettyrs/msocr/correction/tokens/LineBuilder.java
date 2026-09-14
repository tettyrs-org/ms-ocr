package org.tettyrs.msocr.correction.tokens;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LineBuilder {
    private LineBuilder(){}
    static double medianHeight(List<Token> tokens){
        double[] heights = tokens.stream().mapToDouble(Token::height).sorted().toArray();
        return heights[heights.length / 2];
    }

    private static double meanCenterY(List<Token> line){
        return line.stream().mapToDouble(Token::centerY).average().orElse(0);
    }

    public static List<Line> build(List<Token> tokens){
        if(tokens.isEmpty()){
            return List.of();
        }
        double tolerance = medianHeight(tokens)/ 2;

        List<List<Token>> lines = new ArrayList<>();
        for (Token token: tokens.stream().sorted(Comparator.comparingDouble(Token::x0)).toList()){
            List<Token> target = null;
            double bestGap = Double.MAX_VALUE;
            for(List<Token> line: lines){
                double gap = Math.abs(line.get(line.size() - 1).centerY() - token.centerY());
                if (gap <= tolerance && gap < bestGap){
                    target = line;
                    bestGap = gap;
                }
            }
            if (target == null){
                target = new ArrayList<>();
                lines.add(target);
            }
            target.add(token);
        }

        return lines.stream()
                .sorted(Comparator.comparingDouble(LineBuilder::meanCenterY))
                .map(Line::new)
                .toList();
    }
}
