package com.jaycode.rongpet.module.split.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 悬赏分账计算器（纯函数，便于单元测试与历史数据回算）。
 * 规则：
 * 1. 每条线索分得 = 本金 × 评分 ÷ 评分合计；
 * 2. 单条占比不超过 60%（仅一条线索时不受限），超出部分按权重再分配；
 * 3. 不足 1 元的并入评分最高的线索（同分取最早提交）；
 * 4. 四舍五入后的余数差额归评分最高线索。
 */
public final class SplitCalculator {

    public record Input(long clueId, int score) {
    }

    private SplitCalculator() {
    }

    public static Map<Long, Long> calculate(long baseCents, List<Input> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("参与分账的线索不能为空");
        }
        if (inputs.size() == 1) {
            return Map.of(inputs.get(0).clueId(), baseCents);
        }
        int n = inputs.size();
        int totalScore = inputs.stream().mapToInt(Input::score).sum();
        if (totalScore <= 0) {
            throw new IllegalArgumentException("评分合计必须大于0");
        }

        BigDecimal base = BigDecimal.valueOf(baseCents);
        BigDecimal cap = base.multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal[] shares = new BigDecimal[n];
        boolean[] capped = new boolean[n];
        for (int i = 0; i < n; i++) {
            shares[i] = base.multiply(BigDecimal.valueOf(inputs.get(i).score()))
                    .divide(BigDecimal.valueOf(totalScore), 6, RoundingMode.HALF_UP);
        }
        for (int round = 0; round < 10; round++) {
            BigDecimal excess = BigDecimal.ZERO;
            boolean anyCapped = false;
            for (int i = 0; i < n; i++) {
                if (!capped[i] && shares[i].compareTo(cap) > 0) {
                    excess = excess.add(shares[i].subtract(cap));
                    shares[i] = cap;
                    capped[i] = true;
                    anyCapped = true;
                }
            }
            if (!anyCapped) {
                break;
            }
            int weightSum = 0;
            for (int i = 0; i < n; i++) {
                if (!capped[i]) {
                    weightSum += inputs.get(i).score();
                }
            }
            if (weightSum == 0) {
                break;
            }
            for (int i = 0; i < n; i++) {
                if (!capped[i]) {
                    shares[i] = shares[i].add(excess.multiply(BigDecimal.valueOf(inputs.get(i).score()))
                            .divide(BigDecimal.valueOf(weightSum), 6, RoundingMode.HALF_UP));
                }
            }
        }

        long[] cents = new long[n];
        for (int i = 0; i < n; i++) {
            cents[i] = shares[i].setScale(0, RoundingMode.HALF_UP).longValue();
        }

        int top = topIndex(inputs);
        for (int i = 0; i < n; i++) {
            if (i != top && cents[i] < 100) {
                cents[top] += cents[i];
                cents[i] = 0;
            }
        }
        long sum = 0;
        for (long c : cents) {
            sum += c;
        }
        cents[top] += baseCents - sum;

        LinkedHashMap<Long, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            result.put(inputs.get(i).clueId(), Math.max(0, cents[i]));
        }
        return result;
    }

    private static int topIndex(List<Input> inputs) {
        int top = 0;
        for (int i = 1; i < inputs.size(); i++) {
            Input cur = inputs.get(i);
            Input best = inputs.get(top);
            if (cur.score() > best.score()
                    || (cur.score() == best.score() && cur.clueId() < best.clueId())) {
                top = i;
            }
        }
        return top;
    }
}
