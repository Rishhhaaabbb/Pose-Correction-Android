package com.core.poseengine.model

/**
 * Result of [PoseGraph.validate].
 *
 * Replaces Python's Tuple[bool, List[str]] return type.
 *
 * Sealed class enables exhaustive when expressions at call sites:
 *
 *   when (val result = graph.validate()) {
 *       is ValidationResult.Valid   -> proceedNormally()
 *       is ValidationResult.Invalid -> showErrors(result.errors)
 *   }
 */
sealed class ValidationResult {

    /** The graph passed all validation checks. */
    object Valid : ValidationResult()

    /**
     * The graph failed one or more validation checks.
     *
     * @param errors Human-readable error descriptions.
     *   Matches the strings appended to [errors] in Python's validate().
     */
    data class Invalid(val errors: List<String>) : ValidationResult() {
        /** Convenience: true when this result carries at least one error. */
        val hasErrors: Boolean get() = errors.isNotEmpty()

        /** First error message, for single-error display contexts. */
        val firstError: String? get() = errors.firstOrNull()
    }

    /** True only for [Valid]. */
    val isValid: Boolean get() = this is Valid
}