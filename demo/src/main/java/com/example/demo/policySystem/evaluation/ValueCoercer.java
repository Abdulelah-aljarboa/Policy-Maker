package com.example.demo.policySystem.evaluation;

import com.example.demo.policySystem.enums.ParamType;
import com.example.demo.policySystem.exception.RuleEvaluationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class ValueCoercer {

    public static Object coerce(String rawValue, ParamType paramType) {
        if (rawValue == null) {
            return null;
        }
        try {
            return switch (paramType) {
                case STRING -> rawValue;
                case NUMBER -> new BigDecimal(rawValue);
                case DATE -> LocalDate.parse(rawValue); // expects ISO-8601, e.g. "2026-09-18"
                case BOOLEAN -> parseBoolean(rawValue);
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new RuleEvaluationException(
                    "Value '" + rawValue + "' cannot be parsed as " + paramType, e);
        }
    }

    private static Boolean parseBoolean(String rawValue) {
        if ("true".equalsIgnoreCase(rawValue)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(rawValue)) return Boolean.FALSE;
        throw new RuleEvaluationException(
                "Value '" + rawValue + "' is not a valid boolean (expected 'true' or 'false')");
    }
}
