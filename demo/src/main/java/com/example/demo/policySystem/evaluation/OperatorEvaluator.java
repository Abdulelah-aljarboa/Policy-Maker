package com.example.demo.policySystem.evaluation;

import com.example.demo.policySystem.enums.ParamType;
import com.example.demo.policySystem.exception.RuleEvaluationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class OperatorEvaluator {
    public static boolean apply(String operator, Object actual, Object target, ParamType type) {
        return switch (operator) {
            case "=" -> Objects.equals(actual, target);
            case "!=" -> !Objects.equals(actual, target);
            case ">" -> compare(actual, target, type) > 0;
            case "<" -> compare(actual, target, type) < 0;
            case ">=" -> compare(actual, target, type) >= 0;
            case "<=" -> compare(actual, target, type) <= 0;
            case "IN" -> asList(operator, target).contains(actual);
            case "NOT_IN" -> !asList(operator, target).contains(actual);
            case "BETWEEN" -> {
                List<?> bounds = asList(operator, target);
                if (bounds.size() != 2) {
                    throw new RuleEvaluationException(
                            "BETWEEN requires exactly 2 values, got " + bounds.size());
                }
                yield compare(actual, bounds.get(0), type) >= 0
                        && compare(actual, bounds.get(1), type) <= 0;
            }
            default -> throw new RuleEvaluationException("Unsupported operator: " + operator);
        };
    }


    private static int compare(Object actual, Object target, ParamType type) {
        return switch (type) {
            case STRING -> ((String) actual).compareTo((String) target);
            case NUMBER -> ((BigDecimal) actual).compareTo((BigDecimal) target);
            case DATE -> ((LocalDate) actual).compareTo((LocalDate) target);
            case BOOLEAN -> throw new RuleEvaluationException("Boolean is not orderable");
        };
    }

    private static List<?> asList(String operator, Object target) {
        try {
            return (List<?>) target;
        } catch (ClassCastException e) {
            throw new RuleEvaluationException(
                    operator + " requires a list of values, but got a single value ("
                            + target + "). Check that this rule has the correct number of values configured.",
                    e);
        }
    }
}
