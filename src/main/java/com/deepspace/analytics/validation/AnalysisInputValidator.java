package com.deepspace.analytics.validation;

import com.deepspace.analytics.model.AnalysisType;
import com.deepspace.analytics.model.CommunicationAnalysisInput;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Backend Spring {@link Validator} enforcing parameter validation rules across
 * {@link AnalysisType#POISSON_ONLY}, {@link AnalysisType#GEOMETRIC_ONLY}, and
 * {@link AnalysisType#COMBINED} analysis modes.
 */
@Component
public class AnalysisInputValidator implements Validator {

    public static final int MAX_NUMERIC_INPUT_LENGTH = 64;
    public static final BigDecimal MAX_LAMBDA = new BigDecimal("1000000000");
    public static final BigInteger MAX_DROP_COUNT = BigInteger.valueOf(1_000_000_000L);
    public static final BigDecimal MIN_SUCCESS_PROBABILITY = new BigDecimal("0.000000000001");
    public static final BigInteger MAX_ATTEMPTS_LIMIT = BigInteger.valueOf(1_000L);

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[+-]?(?:\\d+\\.?\\d*|\\.\\d+)$");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^[+-]?\\d+$");

    @FunctionalInterface
    private interface FieldErrorConsumer {
        void reject(String field, String code, String message);
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return CommunicationAnalysisInput.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        CommunicationAnalysisInput input = (CommunicationAnalysisInput) target;
        validateInputGroups(input, errors::rejectValue);
    }

    /**
     * Validates submitted parameters according to the active or inferred
     * {@link AnalysisType} and returns an ordered field error map.
     *
     * @param input submitted communication parameters
     * @return ordered field error map keyed by field name
     */
    public Map<String, String> validateParameters(CommunicationAnalysisInput input) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (input == null) {
            fieldErrors.put("lambda", "Average Transmission Drop Rate (λ) is required.");
            fieldErrors.put("dropCount", "Number of Transmission Drops (k) is required.");
            fieldErrors.put("successProbability", "Probability of Successful Transmission (p) is required.");
            fieldErrors.put("maxAttempts", "Maximum Number of Attempts (n) is required.");
            return fieldErrors;
        }
        validateInputGroups(input, (field, code, message) -> fieldErrors.putIfAbsent(field, message));
        return fieldErrors;
    }

    /**
     * Determines the effective {@link AnalysisType} from an input's explicit mode
     * (if provided) and populated parameter groups.
     *
     * @param input submitted communication parameters
     * @return resolved {@link AnalysisType}
     */
    public AnalysisType resolveAnalysisType(CommunicationAnalysisInput input) {
        if (input == null) {
            return AnalysisType.COMBINED;
        }
        if (input.getAnalysisType() != null) {
            return input.getAnalysisType();
        }
        boolean poissonStarted = isPoissonGroupStarted(input);
        boolean geometricStarted = isGeometricGroupStarted(input);
        if (poissonStarted && !geometricStarted) {
            return AnalysisType.POISSON_ONLY;
        }
        if (!poissonStarted && geometricStarted) {
            return AnalysisType.GEOMETRIC_ONLY;
        }
        return AnalysisType.COMBINED;
    }

    /**
     * Returns {@code true} if any parameter in a required or partially started
     * model group is missing or blank.
     *
     * @param input submitted communication parameters
     * @return whether the submission has missing required fields for its mode
     */
    public boolean hasMissingFields(CommunicationAnalysisInput input) {
        if (input == null) {
            return true;
        }
        boolean poissonStarted = isPoissonGroupStarted(input);
        boolean geometricStarted = isGeometricGroupStarted(input);
        AnalysisType explicitType = input.getAnalysisType();

        boolean requirePoisson = shouldValidatePoissonGroup(explicitType, poissonStarted, geometricStarted);
        boolean requireGeometric = shouldValidateGeometricGroup(explicitType, poissonStarted, geometricStarted);

        boolean missingPoisson = requirePoisson && (isBlank(input.getLambda()) || isBlank(input.getDropCount()));
        boolean missingGeometric = requireGeometric
                && (isBlank(input.getSuccessProbability()) || isBlank(input.getMaxAttempts()));
        return missingPoisson || missingGeometric;
    }

    public boolean isPoissonGroupStarted(CommunicationAnalysisInput input) {
        return input != null && (!isBlank(input.getLambda()) || !isBlank(input.getDropCount()));
    }

    public boolean isGeometricGroupStarted(CommunicationAnalysisInput input) {
        return input != null && (!isBlank(input.getSuccessProbability()) || !isBlank(input.getMaxAttempts()));
    }

    private boolean shouldValidatePoissonGroup(
            AnalysisType explicitType,
            boolean poissonStarted,
            boolean geometricStarted) {
        if (explicitType == AnalysisType.POISSON_ONLY || explicitType == AnalysisType.COMBINED) {
            return true;
        }
        if (!poissonStarted && !geometricStarted) {
            return explicitType == null;
        }
        return poissonStarted;
    }

    private boolean shouldValidateGeometricGroup(
            AnalysisType explicitType,
            boolean poissonStarted,
            boolean geometricStarted) {
        if (explicitType == AnalysisType.GEOMETRIC_ONLY || explicitType == AnalysisType.COMBINED) {
            return true;
        }
        if (!poissonStarted && !geometricStarted) {
            return explicitType == null;
        }
        return geometricStarted;
    }

    private void validateInputGroups(CommunicationAnalysisInput input, FieldErrorConsumer consumer) {
        if (input == null) {
            validatePoissonGroup(new CommunicationAnalysisInput(), consumer);
            validateGeometricGroup(new CommunicationAnalysisInput(), consumer);
            return;
        }
        boolean poissonStarted = isPoissonGroupStarted(input);
        boolean geometricStarted = isGeometricGroupStarted(input);
        AnalysisType explicitType = input.getAnalysisType();

        boolean validatePoisson = shouldValidatePoissonGroup(explicitType, poissonStarted, geometricStarted);
        boolean validateGeometric = shouldValidateGeometricGroup(explicitType, poissonStarted, geometricStarted);

        if (validatePoisson) {
            validatePoissonGroup(input, consumer);
        }
        if (validateGeometric) {
            validateGeometricGroup(input, consumer);
        }
    }

    private void validatePoissonGroup(CommunicationAnalysisInput input, FieldErrorConsumer consumer) {
        validateLambda(input.getLambda(), consumer);
        validateDropCount(input.getDropCount(), consumer);
    }

    private void validateGeometricGroup(CommunicationAnalysisInput input, FieldErrorConsumer consumer) {
        validateSuccessProbability(input.getSuccessProbability(), consumer);
        validateMaxAttempts(input.getMaxAttempts(), consumer);
    }

    private void validateLambda(String raw, FieldErrorConsumer consumer) {
        if (isBlank(raw)) {
            consumer.reject("lambda", "lambda.required",
                    "Average Transmission Drop Rate (λ) is required.");
            return;
        }
        String trimmed = raw.trim();
        if (!NUMBER_PATTERN.matcher(trimmed).matches()) {
            consumer.reject("lambda", "lambda.invalid",
                    "Average Transmission Drop Rate (λ) must be a valid numerical value.");
            return;
        }
        if (trimmed.length() > MAX_NUMERIC_INPUT_LENGTH) {
            if (trimmed.startsWith("-")) {
                consumer.reject("lambda", "lambda.negative",
                        "Average Transmission Drop Rate (λ) must be greater than or equal to 0 (negative values are not allowed).");
            } else {
                consumer.reject("lambda", "lambda.tooLarge",
                        "Average Transmission Drop Rate (λ) must not exceed 1,000,000,000.");
            }
            return;
        }
        BigDecimal value = new BigDecimal(trimmed);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            consumer.reject("lambda", "lambda.negative",
                    "Average Transmission Drop Rate (λ) must be greater than or equal to 0 (negative values are not allowed).");
        } else if (value.compareTo(MAX_LAMBDA) > 0) {
            consumer.reject("lambda", "lambda.tooLarge",
                    "Average Transmission Drop Rate (λ) must not exceed 1,000,000,000.");
        }
    }

    private void validateDropCount(String raw, FieldErrorConsumer consumer) {
        if (isBlank(raw)) {
            consumer.reject("dropCount", "dropCount.required",
                    "Number of Transmission Drops (k) is required.");
            return;
        }
        String trimmed = raw.trim();
        if (!NUMBER_PATTERN.matcher(trimmed).matches()) {
            consumer.reject("dropCount", "dropCount.invalid",
                    "Number of Transmission Drops (k) must be a valid whole number (integer).");
            return;
        }
        if (!INTEGER_PATTERN.matcher(trimmed).matches()) {
            if (trimmed.length() <= MAX_NUMERIC_INPUT_LENGTH
                    && new BigDecimal(trimmed).compareTo(BigDecimal.ZERO) < 0) {
                consumer.reject("dropCount", "dropCount.negativeDecimal",
                        "Number of Transmission Drops (k) must be a whole number (integer) greater than or equal to 0.");
            } else {
                consumer.reject("dropCount", "dropCount.decimal",
                        "Number of Transmission Drops (k) must be a whole number (integer); decimal values are not allowed.");
            }
            return;
        }
        if (trimmed.length() > MAX_NUMERIC_INPUT_LENGTH) {
            if (trimmed.startsWith("-")) {
                consumer.reject("dropCount", "dropCount.negative",
                        "Number of Transmission Drops (k) must be greater than or equal to 0 (negative values are not allowed).");
            } else {
                consumer.reject("dropCount", "dropCount.tooLarge",
                        "Number of Transmission Drops (k) must not exceed 1,000,000,000.");
            }
            return;
        }
        BigInteger integer = new BigInteger(trimmed);
        if (integer.compareTo(BigInteger.ZERO) < 0) {
            consumer.reject("dropCount", "dropCount.negative",
                    "Number of Transmission Drops (k) must be greater than or equal to 0 (negative values are not allowed).");
        } else if (integer.compareTo(MAX_DROP_COUNT) > 0) {
            consumer.reject("dropCount", "dropCount.tooLarge",
                    "Number of Transmission Drops (k) must not exceed 1,000,000,000.");
        }
    }

    private void validateSuccessProbability(String raw, FieldErrorConsumer consumer) {
        if (isBlank(raw)) {
            consumer.reject("successProbability", "successProbability.required",
                    "Probability of Successful Transmission (p) is required.");
            return;
        }
        String trimmed = raw.trim();
        if (!NUMBER_PATTERN.matcher(trimmed).matches()) {
            consumer.reject("successProbability", "successProbability.invalid",
                    "Probability of Successful Transmission (p) must be a valid numerical value.");
            return;
        }
        if (trimmed.length() > MAX_NUMERIC_INPUT_LENGTH) {
            if (trimmed.matches("^[+-]?(?:0+\\.?0*|\\.0+)$")) {
                consumer.reject("successProbability", "successProbability.zero",
                        "Probability of Successful Transmission (p) must be greater than 0 (zero is not allowed).");
            } else if (trimmed.startsWith("-")) {
                consumer.reject("successProbability", "successProbability.negative",
                        "Probability of Successful Transmission (p) must be greater than 0 (negative values are not allowed).");
            } else if (trimmed.matches("^\\+?0*\\.0*[1-9].*$")) {
                consumer.reject("successProbability", "successProbability.tooSmall",
                        "Probability of Successful Transmission (p) must be at least 0.000000000001 to remain within supported numerical precision.");
            } else {
                consumer.reject("successProbability", "successProbability.aboveOne",
                        "Probability of Successful Transmission (p) must be less than or equal to 1.");
            }
            return;
        }
        BigDecimal value = new BigDecimal(trimmed);
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            consumer.reject("successProbability", "successProbability.zero",
                    "Probability of Successful Transmission (p) must be greater than 0 (zero is not allowed).");
        } else if (value.compareTo(BigDecimal.ZERO) < 0) {
            consumer.reject("successProbability", "successProbability.negative",
                    "Probability of Successful Transmission (p) must be greater than 0 (negative values are not allowed).");
        } else if (value.compareTo(BigDecimal.ONE) > 0) {
            consumer.reject("successProbability", "successProbability.aboveOne",
                    "Probability of Successful Transmission (p) must be less than or equal to 1.");
        } else if (value.compareTo(MIN_SUCCESS_PROBABILITY) < 0) {
            consumer.reject("successProbability", "successProbability.tooSmall",
                    "Probability of Successful Transmission (p) must be at least 0.000000000001 to remain within supported numerical precision.");
        }
    }

    private void validateMaxAttempts(String raw, FieldErrorConsumer consumer) {
        if (isBlank(raw)) {
            consumer.reject("maxAttempts", "maxAttempts.required",
                    "Maximum Number of Attempts (n) is required.");
            return;
        }
        String trimmed = raw.trim();
        if (!NUMBER_PATTERN.matcher(trimmed).matches()) {
            consumer.reject("maxAttempts", "maxAttempts.invalid",
                    "Maximum Number of Attempts (n) must be a valid whole number (integer).");
            return;
        }
        if (!INTEGER_PATTERN.matcher(trimmed).matches()) {
            consumer.reject("maxAttempts", "maxAttempts.decimal",
                    "Maximum Number of Attempts (n) must be a whole number (integer); decimal values are not allowed.");
            return;
        }
        if (trimmed.length() > MAX_NUMERIC_INPUT_LENGTH) {
            if (trimmed.startsWith("-")) {
                consumer.reject("maxAttempts", "maxAttempts.belowOne",
                        "Maximum Number of Attempts (n) must be greater than or equal to 1 (zero and negative values are not allowed).");
            } else {
                consumer.reject("maxAttempts", "maxAttempts.tooLarge",
                        "Maximum Number of Attempts (n) must not exceed 1,000.");
            }
            return;
        }
        BigInteger integer = new BigInteger(trimmed);
        if (integer.compareTo(BigInteger.ONE) < 0) {
            consumer.reject("maxAttempts", "maxAttempts.belowOne",
                    "Maximum Number of Attempts (n) must be greater than or equal to 1 (zero and negative values are not allowed).");
        } else if (integer.compareTo(MAX_ATTEMPTS_LIMIT) > 0) {
            consumer.reject("maxAttempts", "maxAttempts.tooLarge",
                    "Maximum Number of Attempts (n) must not exceed 1,000.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

