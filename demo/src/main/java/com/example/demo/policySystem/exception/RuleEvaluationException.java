package com.example.demo.policySystem.exception;

public class RuleEvaluationException extends RuntimeException {
    public RuleEvaluationException(String message) {
        super(message);
    }

    public RuleEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
