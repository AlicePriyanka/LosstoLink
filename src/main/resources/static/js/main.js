/**
 * DeepSpace Analytics — Client Interaction Script
 * Handles responsive navigation toggling, smooth anchor scrolling,
 * subtle reveal transitions, unified form draft preservation, and
 * Stage 3 client-side input validation (paired with backend validation).
 */
(function () {
    "use strict";

    const ANALYSIS_DRAFT_KEY = "deepspace.stage3.analysisDraft";
    const NUMBER_PATTERN = /^[+-]?(?:\d+\.?\d*|\.\d+)$/;
    const INTEGER_PATTERN = /^[+-]?\d+$/;
    const ZERO_NUMBER_PATTERN = /^[+-]?(?:0+\.?0*|\.0+)$/;
    const TINY_POSITIVE_DECIMAL_PATTERN = /^\+?0*\.0*[1-9].*$/;
    const MAX_NUMERIC_INPUT_LENGTH = 64;
    const MAX_LAMBDA = 1000000000;
    const MAX_DROP_COUNT = 1000000000;
    const MAX_ATTEMPTS_LIMIT = 1000;

    function initMobileNav() {
        const toggle = document.querySelector(".nav-toggle");
        const nav = document.getElementById("primary-navigation");
        if (!toggle || !nav) {
            return;
        }

        function setMenuState(open) {
            nav.classList.toggle("is-open", open);
            toggle.setAttribute("aria-expanded", String(open));
        }

        toggle.addEventListener("click", function () {
            const isOpen = nav.classList.contains("is-open");
            setMenuState(!isOpen);
        });

        nav.querySelectorAll("a, button").forEach(function (el) {
            el.addEventListener("click", function () {
                setMenuState(false);
            });
        });

        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && nav.classList.contains("is-open")) {
                setMenuState(false);
                toggle.focus();
            }
        });
    }

    function initSmoothScroll() {
        document.querySelectorAll("[data-scroll-link]").forEach(function (link) {
            link.addEventListener("click", function (event) {
                const href = link.getAttribute("href");
                if (!href || !href.startsWith("#")) {
                    return;
                }
                const target = document.querySelector(href);
                if (target) {
                    event.preventDefault();
                    target.scrollIntoView({ behavior: "smooth", block: "start" });
                }
            });
        });
    }

    function initRevealAnimations() {
        const items = document.querySelectorAll("[data-reveal]");
        if (!items.length) {
            return;
        }

        const reducedMotion = window.matchMedia &&
            window.matchMedia("(prefers-reduced-motion: reduce)").matches;

        if (reducedMotion || !("IntersectionObserver" in window)) {
            items.forEach(function (item) {
                item.classList.add("is-visible");
            });
            return;
        }

        const observer = new IntersectionObserver(
            function (entries, obs) {
                entries.forEach(function (entry) {
                    if (entry.isIntersecting) {
                        entry.target.classList.add("is-visible");
                        obs.unobserve(entry.target);
                    }
                });
            },
            { threshold: 0.12, rootMargin: "0px 0px -24px 0px" }
        );

        items.forEach(function (item) {
            observer.observe(item);
        });
    }

    function isDecimalStringGreaterThanOne(trimmed) {
        const unsigned = trimmed.replace(/^[+-]/, "");
        const parts = unsigned.split(".");
        const intPart = parts[0].replace(/^0+/, "") || "0";
        if (intPart.length > 1 || (intPart.length === 1 && intPart > "1")) {
            return true;
        }
        if (intPart === "1" && parts.length > 1) {
            return /[1-9]/.test(parts[1]);
        }
        return false;
    }

    function isDecimalStringBelowMinProbability(trimmed) {
        if (ZERO_NUMBER_PATTERN.test(trimmed) || trimmed.startsWith("-")) {
            return false;
        }
        const unsigned = trimmed.replace(/^[+-]/, "");
        const parts = unsigned.split(".");
        const intPart = parts[0].replace(/^0+/, "") || "0";
        if (intPart !== "0") {
            return false;
        }
        const fracPart = parts[1] || "";
        const firstTwelve = (fracPart + "000000000000").slice(0, 12);
        return /^0{12}$/.test(firstTwelve);
    }

    function validateAnalysisField(fieldId, rawValue) {
        const trimmed = typeof rawValue === "string" ? rawValue.trim() : "";

        if (fieldId === "lambda") {
            if (!trimmed) {
                return "Average Transmission Drop Rate (\u03bb) is required.";
            }
            if (!NUMBER_PATTERN.test(trimmed)) {
                return "Average Transmission Drop Rate (\u03bb) must be a valid numerical value.";
            }
            if (trimmed.length > MAX_NUMERIC_INPUT_LENGTH) {
                if (trimmed.startsWith("-")) {
                    return "Average Transmission Drop Rate (\u03bb) must be greater than or equal to 0 (negative values are not allowed).";
                }
                return "Average Transmission Drop Rate (\u03bb) must not exceed 1,000,000,000.";
            }
            const value = Number(trimmed);
            if (!Number.isFinite(value) || value < 0) {
                return "Average Transmission Drop Rate (\u03bb) must be greater than or equal to 0 (negative values are not allowed).";
            }
            if (value > MAX_LAMBDA) {
                return "Average Transmission Drop Rate (\u03bb) must not exceed 1,000,000,000.";
            }
            return null;
        }

        if (fieldId === "dropCount") {
            if (!trimmed) {
                return "Number of Transmission Drops (k) is required.";
            }
            if (!NUMBER_PATTERN.test(trimmed)) {
                return "Number of Transmission Drops (k) must be a valid whole number (integer).";
            }
            if (!INTEGER_PATTERN.test(trimmed)) {
                if (trimmed.startsWith("-")) {
                    return "Number of Transmission Drops (k) must be a whole number (integer) greater than or equal to 0.";
                }
                return "Number of Transmission Drops (k) must be a whole number (integer); decimal values are not allowed.";
            }
            if (trimmed.length > MAX_NUMERIC_INPUT_LENGTH) {
                if (trimmed.startsWith("-")) {
                    return "Number of Transmission Drops (k) must be greater than or equal to 0 (negative values are not allowed).";
                }
                return "Number of Transmission Drops (k) must not exceed 1,000,000,000.";
            }
            const intValue = Number(trimmed);
            if (!Number.isFinite(intValue) || intValue < 0) {
                return "Number of Transmission Drops (k) must be greater than or equal to 0 (negative values are not allowed).";
            }
            if (intValue > MAX_DROP_COUNT) {
                return "Number of Transmission Drops (k) must not exceed 1,000,000,000.";
            }
            return null;
        }

        if (fieldId === "successProbability") {
            if (!trimmed) {
                return "Probability of Successful Transmission (p) is required.";
            }
            if (!NUMBER_PATTERN.test(trimmed)) {
                return "Probability of Successful Transmission (p) must be a valid numerical value.";
            }
            if (ZERO_NUMBER_PATTERN.test(trimmed)) {
                return "Probability of Successful Transmission (p) must be greater than 0 (zero is not allowed).";
            }
            if (trimmed.startsWith("-")) {
                return "Probability of Successful Transmission (p) must be greater than 0 (negative values are not allowed).";
            }
            if (trimmed.length > MAX_NUMERIC_INPUT_LENGTH) {
                if (TINY_POSITIVE_DECIMAL_PATTERN.test(trimmed)) {
                    return "Probability of Successful Transmission (p) must be at least 0.000000000001 to remain within supported numerical precision.";
                }
                return "Probability of Successful Transmission (p) must be less than or equal to 1.";
            }
            if (isDecimalStringGreaterThanOne(trimmed)) {
                return "Probability of Successful Transmission (p) must be less than or equal to 1.";
            }
            if (isDecimalStringBelowMinProbability(trimmed)) {
                return "Probability of Successful Transmission (p) must be at least 0.000000000001 to remain within supported numerical precision.";
            }
            return null;
        }

        if (fieldId === "maxAttempts") {
            if (!trimmed) {
                return "Maximum Number of Attempts (n) is required.";
            }
            if (!NUMBER_PATTERN.test(trimmed)) {
                return "Maximum Number of Attempts (n) must be a valid whole number (integer).";
            }
            if (!INTEGER_PATTERN.test(trimmed)) {
                return "Maximum Number of Attempts (n) must be a whole number (integer); decimal values are not allowed.";
            }
            if (trimmed.length > MAX_NUMERIC_INPUT_LENGTH) {
                if (trimmed.startsWith("-")) {
                    return "Maximum Number of Attempts (n) must be greater than or equal to 1 (zero and negative values are not allowed).";
                }
                return "Maximum Number of Attempts (n) must not exceed 1,000.";
            }
            const intValue = Number(trimmed);
            if (!Number.isFinite(intValue) || intValue < 1) {
                return "Maximum Number of Attempts (n) must be greater than or equal to 1 (zero and negative values are not allowed).";
            }
            if (intValue > MAX_ATTEMPTS_LIMIT) {
                return "Maximum Number of Attempts (n) must not exceed 1,000.";
            }
            return null;
        }

        return null;
    }

    function initAnalysisForm() {
        const form = document.getElementById("communication-analysis-form");
        if (!form) {
            return;
        }

        const lambdaInput = document.getElementById("lambda");
        const dropCountInput = document.getElementById("dropCount");
        const successProbabilityInput = document.getElementById("successProbability");
        const maxAttemptsInput = document.getElementById("maxAttempts");
        const analysisTypeInput = document.getElementById("analysisType");
        const calculateBtn = document.getElementById("calculate-results-btn");
        const resetBtn = document.getElementById("reset-inputs-btn");
        const summaryBanner = document.getElementById("form-validation-summary");
        const summaryText = document.getElementById("form-validation-summary-text");
        const hasServerErrors = form.getAttribute("data-has-server-errors") === "true";

        // Mode Confirmation Modal Elements
        const modal = document.getElementById("mode-confirmation-modal");
        const modalTitle = document.getElementById("modal-title");
        const modalDesc = document.getElementById("modal-description");
        const modalBadge = document.getElementById("modal-mode-badge");
        const modalParamsSummary = document.getElementById("modal-params-summary");
        const modalCancelBtn = document.getElementById("modal-cancel-btn");
        const modalConfirmBtn = document.getElementById("modal-confirm-btn");

        const fieldIds = ["lambda", "dropCount", "successProbability", "maxAttempts"];
        const inputs = fieldIds
            .map(function (id) {
                return document.getElementById(id);
            })
            .filter(Boolean);

        let isConfirmedSubmission = false;
        let pendingAnalysisMode = null;

        function setFieldError(input, errorMessage) {
            const fieldContainer = input.closest ? input.closest(".form-field") : null;
            const errorEl = document.querySelector('[data-error-for="' + input.id + '"]');

            if (errorMessage) {
                input.classList.add("is-invalid");
                input.setAttribute("aria-invalid", "true");
                if (fieldContainer) {
                    fieldContainer.classList.add("has-error");
                }
                if (errorEl) {
                    errorEl.textContent = errorMessage;
                    errorEl.hidden = false;
                }
            } else {
                input.classList.remove("is-invalid");
                input.setAttribute("aria-invalid", "false");
                if (fieldContainer) {
                    fieldContainer.classList.remove("has-error");
                }
                if (errorEl) {
                    errorEl.textContent = "";
                    errorEl.hidden = true;
                }
            }
        }

        function setSummaryError(message) {
            if (!summaryBanner || !summaryText) {
                return;
            }
            if (message) {
                summaryText.textContent = message;
                summaryBanner.hidden = false;
            } else {
                summaryText.textContent = "";
                summaryBanner.hidden = true;
            }
        }

        function updateSummaryIfAllClear() {
            const anyStillInvalid = inputs.some(function (input) {
                return input.classList.contains("is-invalid");
            });
            if (!anyStillInvalid) {
                setSummaryError("");
            }
        }

        function escapeHtml(str) {
            return String(str)
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#039;");
        }

        function openConfirmationModal(mode, lambdaVal, dropCountVal, pVal, nVal) {
            if (!modal) {
                return;
            }
            pendingAnalysisMode = mode;

            if (mode === "POISSON_ONLY") {
                if (modalBadge) {
                    modalBadge.textContent = "Transmission Drop Analysis (Poisson Only)";
                    modalBadge.className = "model-tag model-tag--blue";
                }
                if (modalTitle) {
                    modalTitle.textContent = "Confirm Transmission Drop Analysis Only";
                }
                if (modalDesc) {
                    modalDesc.textContent = "You have provided parameters for transmission drop analysis (Poisson Distribution) only. Would you like to proceed with only Poisson transmission drop analysis?";
                }
                if (modalParamsSummary) {
                    modalParamsSummary.innerHTML =
                        '<div class="modal-param-row">' +
                            '<span class="modal-param-label">Average Drop Rate (&lambda;):</span>' +
                            '<strong class="modal-param-value">' + escapeHtml(lambdaVal) + '</strong>' +
                        '</div>' +
                        '<div class="modal-param-row">' +
                            '<span class="modal-param-label">Transmission Drops (k):</span>' +
                            '<strong class="modal-param-value">' + escapeHtml(dropCountVal) + '</strong>' +
                        '</div>' +
                        '<div class="modal-param-row modal-param-row--omitted">' +
                            '<span class="modal-param-label">First-Success Analysis (Geometric):</span>' +
                            '<span class="modal-param-omitted">Omitted from this analysis</span>' +
                        '</div>';
                }
                if (modalConfirmBtn) {
                    const span = modalConfirmBtn.querySelector("span") || modalConfirmBtn;
                    span.textContent = "Proceed with Poisson Analysis";
                }
            } else if (mode === "GEOMETRIC_ONLY") {
                if (modalBadge) {
                    modalBadge.textContent = "First-Success Analysis (Geometric Only)";
                    modalBadge.className = "model-tag model-tag--purple";
                }
                if (modalTitle) {
                    modalTitle.textContent = "Confirm First-Success Transmission Analysis Only";
                }
                if (modalDesc) {
                    modalDesc.textContent = "You have provided parameters for first-success transmission analysis (Geometric Distribution) only. Would you like to proceed with only Geometric first-success analysis?";
                }
                if (modalParamsSummary) {
                    modalParamsSummary.innerHTML =
                        '<div class="modal-param-row">' +
                            '<span class="modal-param-label">Success Probability (p):</span>' +
                            '<strong class="modal-param-value">' + escapeHtml(pVal) + '</strong>' +
                        '</div>' +
                        '<div class="modal-param-row">' +
                            '<span class="modal-param-label">Maximum Attempts (n):</span>' +
                            '<strong class="modal-param-value">' + escapeHtml(nVal) + '</strong>' +
                        '</div>' +
                        '<div class="modal-param-row modal-param-row--omitted">' +
                            '<span class="modal-param-label">Transmission Drop Analysis (Poisson):</span>' +
                            '<span class="modal-param-omitted">Omitted from this analysis</span>' +
                        '</div>';
                }
                if (modalConfirmBtn) {
                    const span = modalConfirmBtn.querySelector("span") || modalConfirmBtn;
                    span.textContent = "Proceed with Geometric Analysis";
                }
            }

            modal.hidden = false;
            document.body.classList.add("modal-open");
            if (modalConfirmBtn) {
                modalConfirmBtn.focus();
            }
        }

        function closeConfirmationModal() {
            if (!modal) {
                return;
            }
            modal.hidden = true;
            document.body.classList.remove("modal-open");
            pendingAnalysisMode = null;
            if (calculateBtn) {
                calculateBtn.focus();
            }
        }

        let isSubmitting = false;

        function executeSubmission(mode) {
            if (isSubmitting) {
                return;
            }
            isSubmitting = true;
            isConfirmedSubmission = true;
            if (analysisTypeInput && mode) {
                analysisTypeInput.value = mode;
            }
            if (calculateBtn) {
                calculateBtn.disabled = true;
                calculateBtn.classList.add("is-loading");
                const labelSpan = calculateBtn.querySelector("span");
                if (labelSpan && !calculateBtn.hasAttribute("data-original-label")) {
                    calculateBtn.setAttribute("data-original-label", labelSpan.textContent);
                    labelSpan.textContent = "Calculating Results...";
                }
            }
            if (modalConfirmBtn) {
                modalConfirmBtn.disabled = true;
                modalConfirmBtn.classList.add("is-loading");
                const modalSpan = modalConfirmBtn.querySelector("span");
                if (modalSpan && !modalConfirmBtn.hasAttribute("data-original-label")) {
                    modalConfirmBtn.setAttribute("data-original-label", modalSpan.textContent);
                    modalSpan.textContent = "Calculating...";
                }
            }
            clearDraft();
            form.submit();
        }

        if (modalCancelBtn) {
            modalCancelBtn.addEventListener("click", function () {
                closeConfirmationModal();
            });
        }

        if (modalConfirmBtn) {
            modalConfirmBtn.addEventListener("click", function () {
                if (pendingAnalysisMode) {
                    executeSubmission(pendingAnalysisMode);
                }
            });
        }

        if (modal) {
            modal.addEventListener("click", function (event) {
                if (event.target === modal) {
                    closeConfirmationModal();
                }
            });

            document.addEventListener("keydown", function (event) {
                if (modal && !modal.hidden) {
                    if (event.key === "Escape") {
                        event.preventDefault();
                        closeConfirmationModal();
                    } else if (event.key === "Tab") {
                        const focusable = [modalCancelBtn, modalConfirmBtn].filter(Boolean);
                        if (focusable.length > 0) {
                            const first = focusable[0];
                            const last = focusable[focusable.length - 1];
                            if (event.shiftKey) {
                                if (document.activeElement === first) {
                                    event.preventDefault();
                                    last.focus();
                                }
                            } else {
                                if (document.activeElement === last) {
                                    event.preventDefault();
                                    first.focus();
                                }
                            }
                        }
                    }
                }
            });
        }

        function saveDraft() {
            try {
                const payload = {};
                inputs.forEach(function (input) {
                    payload[input.id] = input.value;
                });
                window.sessionStorage.setItem(ANALYSIS_DRAFT_KEY, JSON.stringify(payload));
            } catch (err) {
                // Ignore storage errors in restricted environments
            }
        }

        function restoreDraft() {
            if (hasServerErrors) {
                return;
            }
            try {
                const raw = window.sessionStorage.getItem(ANALYSIS_DRAFT_KEY);
                if (!raw) {
                    return;
                }
                const payload = JSON.parse(raw);
                inputs.forEach(function (input) {
                    if (!input.value && typeof payload[input.id] === "string") {
                        input.value = payload[input.id];
                    }
                });
            } catch (err) {
                // Ignore malformed or unavailable storage
            }
        }

        function clearDraft() {
            try {
                window.sessionStorage.removeItem(ANALYSIS_DRAFT_KEY);
            } catch (err) {
                // Ignore storage errors
            }
        }

        restoreDraft();

        inputs.forEach(function (input) {
            input.addEventListener("input", function () {
                saveDraft();
                if (input.classList.contains("is-invalid")) {
                    const error = validateAnalysisField(input.id, input.value);
                    if (!error) {
                        setFieldError(input, null);
                        updateSummaryIfAllClear();
                    } else {
                        setFieldError(input, error);
                    }
                }
            });
        });

        if (resetBtn) {
            resetBtn.addEventListener("click", function () {
                inputs.forEach(function (input) {
                    input.value = "";
                    setFieldError(input, null);
                });
                setSummaryError("");
                clearDraft();
                isConfirmedSubmission = false;
                pendingAnalysisMode = null;
                if (modal && !modal.hidden) {
                    closeConfirmationModal();
                }
            });
        }

        form.addEventListener("submit", function (event) {
            if (isConfirmedSubmission) {
                return;
            }
            event.preventDefault();

            const lambdaVal = lambdaInput ? lambdaInput.value.trim() : "";
            const dropCountVal = dropCountInput ? dropCountInput.value.trim() : "";
            const pVal = successProbabilityInput ? successProbabilityInput.value.trim() : "";
            const nVal = maxAttemptsInput ? maxAttemptsInput.value.trim() : "";

            const hasLambda = lambdaVal !== "";
            const hasDropCount = dropCountVal !== "";
            const hasP = pVal !== "";
            const hasN = nVal !== "";

            const hasPoissonStarted = hasLambda || hasDropCount;
            const hasGeometricStarted = hasP || hasN;
            const isPoissonComplete = hasLambda && hasDropCount;
            const isGeometricComplete = hasP && hasN;

            // Scenario 1: All 4 fields empty
            if (!hasPoissonStarted && !hasGeometricStarted) {
                inputs.forEach(function (input) {
                    const error = validateAnalysisField(input.id, input.value);
                    setFieldError(input, error);
                });
                setSummaryError("Please complete all required fields and fix any highlighted errors before calculating results.");
                if (lambdaInput && typeof lambdaInput.focus === "function") {
                    lambdaInput.focus();
                }
                return;
            }

            // Scenario 2: Incomplete group submissions (partial inputs)
            if ((hasPoissonStarted && !isPoissonComplete) || (hasGeometricStarted && !isGeometricComplete)) {
                let firstInvalid = null;
                if (hasPoissonStarted) {
                    [lambdaInput, dropCountInput].filter(Boolean).forEach(function (input) {
                        const error = validateAnalysisField(input.id, input.value);
                        setFieldError(input, error);
                        if (error && !firstInvalid) {
                            firstInvalid = input;
                        }
                    });
                }
                if (hasGeometricStarted) {
                    [successProbabilityInput, maxAttemptsInput].filter(Boolean).forEach(function (input) {
                        const error = validateAnalysisField(input.id, input.value);
                        setFieldError(input, error);
                        if (error && !firstInvalid) {
                            firstInvalid = input;
                        }
                    });
                }
                setSummaryError("Please complete all required fields and fix any highlighted errors before calculating results.");
                if (firstInvalid && typeof firstInvalid.focus === "function") {
                    firstInvalid.focus();
                }
                return;
            }

            // Scenario 3: Poisson Complete ONLY
            if (isPoissonComplete && !hasGeometricStarted) {
                [successProbabilityInput, maxAttemptsInput].filter(Boolean).forEach(function (input) {
                    setFieldError(input, null);
                });

                let firstInvalid = null;
                [lambdaInput, dropCountInput].filter(Boolean).forEach(function (input) {
                    const error = validateAnalysisField(input.id, input.value);
                    setFieldError(input, error);
                    if (error && !firstInvalid) {
                        firstInvalid = input;
                    }
                });

                if (firstInvalid) {
                    setSummaryError("Please review and correct the highlighted parameter errors before calculating results.");
                    firstInvalid.focus();
                    return;
                }

                setSummaryError("");
                openConfirmationModal("POISSON_ONLY", lambdaVal, dropCountVal, pVal, nVal);
                return;
            }

            // Scenario 4: Geometric Complete ONLY
            if (isGeometricComplete && !hasPoissonStarted) {
                [lambdaInput, dropCountInput].filter(Boolean).forEach(function (input) {
                    setFieldError(input, null);
                });

                let firstInvalid = null;
                [successProbabilityInput, maxAttemptsInput].filter(Boolean).forEach(function (input) {
                    const error = validateAnalysisField(input.id, input.value);
                    setFieldError(input, error);
                    if (error && !firstInvalid) {
                        firstInvalid = input;
                    }
                });

                if (firstInvalid) {
                    setSummaryError("Please review and correct the highlighted parameter errors before calculating results.");
                    firstInvalid.focus();
                    return;
                }

                setSummaryError("");
                openConfirmationModal("GEOMETRIC_ONLY", lambdaVal, dropCountVal, pVal, nVal);
                return;
            }

            // Scenario 5: All 4 fields complete (COMBINED)
            if (isPoissonComplete && isGeometricComplete) {
                let firstInvalid = null;
                inputs.forEach(function (input) {
                    const error = validateAnalysisField(input.id, input.value);
                    setFieldError(input, error);
                    if (error && !firstInvalid) {
                        firstInvalid = input;
                    }
                });

                if (firstInvalid) {
                    setSummaryError("Please review and correct the highlighted parameter errors before calculating results.");
                    firstInvalid.focus();
                    return;
                }

                setSummaryError("");
                executeSubmission("COMBINED");
            }
        });

        window.addEventListener("pageshow", function () {
            isSubmitting = false;
            isConfirmedSubmission = false;
            if (calculateBtn) {
                calculateBtn.disabled = false;
                calculateBtn.classList.remove("is-loading");
                const labelSpan = calculateBtn.querySelector("span");
                const orig = calculateBtn.getAttribute("data-original-label");
                if (labelSpan && orig) {
                    labelSpan.textContent = orig;
                }
            }
            if (modalConfirmBtn) {
                modalConfirmBtn.disabled = false;
                modalConfirmBtn.classList.remove("is-loading");
                const modalSpan = modalConfirmBtn.querySelector("span");
                const orig = modalConfirmBtn.getAttribute("data-original-label");
                if (modalSpan && orig) {
                    modalSpan.textContent = orig;
                }
            }
        });
    }

    const chartInstances = {
        poisson: null,
        geometric: null,
        reliability: null
    };

    let activeDashboardChartsPayload = null;

    const highlightGuidePlugin = {
        id: "highlightGuide",
        afterDatasetsDraw: function (chart, args, pluginOptions) {
            if (!pluginOptions || typeof pluginOptions.index !== "number" || pluginOptions.index < 0) {
                return;
            }
            const meta = chart.getDatasetMeta(0);
            if (!meta || !meta.data || !meta.data[pluginOptions.index]) {
                return;
            }

            const element = meta.data[pluginOptions.index];
            const chartArea = chart.chartArea;
            if (!chartArea) {
                return;
            }

            const x = element.x;
            const ctx = chart.ctx;
            const label = pluginOptions.label || "";
            const lineColor = pluginOptions.color || "#38bdf8";
            const bgColor = pluginOptions.bgColor || "rgba(15, 23, 42, 0.92)";
            const textColor = pluginOptions.textColor || "#f8fafc";

            ctx.save();
            ctx.beginPath();
            ctx.setLineDash([5, 4]);
            ctx.lineWidth = 1.5;
            ctx.strokeStyle = lineColor;
            ctx.moveTo(x, chartArea.top + 4);
            ctx.lineTo(x, chartArea.bottom);
            ctx.stroke();
            ctx.setLineDash([]);

            if (label) {
                ctx.font = '600 11px "JetBrains Mono", monospace';
                const textWidth = ctx.measureText(label).width;
                const padX = 8;
                const boxW = textWidth + padX * 2;
                const boxH = 21;
                const clampedX = Math.max(
                    chartArea.left + boxW / 2 + 2,
                    Math.min(chartArea.right - boxW / 2 - 2, x)
                );
                const boxX = clampedX - boxW / 2;
                const boxY = chartArea.top + 4;

                ctx.fillStyle = bgColor;
                ctx.strokeStyle = lineColor;
                ctx.lineWidth = 1.25;
                ctx.beginPath();
                if (typeof ctx.roundRect === "function") {
                    ctx.roundRect(boxX, boxY, boxW, boxH, 5);
                } else {
                    ctx.rect(boxX, boxY, boxW, boxH);
                }
                ctx.fill();
                ctx.stroke();

                ctx.fillStyle = textColor;
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillText(label, clampedX, boxY + boxH / 2);
            }
            ctx.restore();
        }
    };

    const academicCanvasBackgroundPlugin = {
        id: "academicCanvasBackground",
        beforeDraw: function (chart) {
            const ctx = chart.ctx;
            ctx.save();
            ctx.fillStyle = "#ffffff";
            ctx.fillRect(0, 0, chart.width, chart.height);
            ctx.strokeStyle = "#cbd5e1";
            ctx.lineWidth = 1.5;
            ctx.strokeRect(0.75, 0.75, chart.width - 1.5, chart.height - 1.5);
            ctx.restore();
        }
    };

    function destroyChartInstance(key, canvas) {
        if (chartInstances[key]) {
            chartInstances[key].destroy();
            chartInstances[key] = null;
        }
        if (typeof window.Chart === "function" && typeof window.Chart.getChart === "function" && canvas) {
            const existing = window.Chart.getChart(canvas);
            if (existing) {
                existing.destroy();
            }
        }
    }

    function destroyAllCharts() {
        destroyChartInstance("poisson", document.getElementById("poisson-chart"));
        destroyChartInstance("geometric", document.getElementById("geometric-chart"));
        destroyChartInstance("reliability", document.getElementById("reliability-chart"));
    }

    function readDashboardChartsPayload(rawPayload) {
        if (rawPayload) {
            activeDashboardChartsPayload = rawPayload;
            return rawPayload;
        }
        if (activeDashboardChartsPayload) {
            return activeDashboardChartsPayload;
        }
        const dataEl = document.getElementById("dashboard-chart-data");
        if (!dataEl || !dataEl.textContent || !dataEl.textContent.trim()) {
            return null;
        }
        try {
            activeDashboardChartsPayload = JSON.parse(dataEl.textContent);
            return activeDashboardChartsPayload;
        } catch (err) {
            return null;
        }
    }

    function computeSeriesMax(values) {
        if (!Array.isArray(values) || values.length === 0) {
            return 0;
        }
        let maxVal = 0;
        for (let i = 0; i < values.length; i++) {
            const v = Number(values[i]);
            if (Number.isFinite(v) && v > maxVal) {
                maxVal = v;
            }
        }
        return maxVal;
    }

    function computeProbabilitySuggestedMax(maxVal, defaultMax) {
        if (maxVal > 0 && maxVal < 0.08) {
            return undefined;
        }
        return defaultMax;
    }

    function formatProbabilityAxisTick(val, maxVal) {
        const n = Number(val);
        if (!Number.isFinite(n)) {
            return "";
        }
        if (n === 0) {
            return "0";
        }
        if (maxVal > 0 && maxVal < 0.001) {
            return n.toExponential(2);
        }
        if (maxVal > 0 && maxVal < 0.01) {
            return n.toFixed(4);
        }
        if (maxVal > 0 && maxVal < 0.08) {
            return n.toFixed(3);
        }
        return n.toFixed(2);
    }

    function renderAllCharts(rawPayload) {
        const payload = readDashboardChartsPayload(rawPayload);
        if (!payload || typeof window.Chart !== "function") {
            return;
        }

        const axisFont = {
            family: '"Inter", -apple-system, BlinkMacSystemFont, sans-serif',
            size: 12
        };
        const tickFont = {
            family: '"JetBrains Mono", ui-monospace, SFMono-Regular, monospace',
            size: 11
        };

        // 1. Poisson Distribution Bar Chart (Blue)
        const poissonCanvas = document.getElementById("poisson-chart");
        if (poissonCanvas && Array.isArray(payload.poissonSeries) && payload.poissonSeries.length > 0) {
            destroyChartInstance("poisson", poissonCanvas);
            const pSeries = payload.poissonSeries;
            const pValues = pSeries.map(function (pt) {
                return pt.probability;
            });
            const pMax = computeSeriesMax(pValues);
            const selectedIdx = pSeries.findIndex(function (pt) {
                return pt.selected;
            });

            chartInstances.poisson = new window.Chart(poissonCanvas.getContext("2d"), {
                type: "bar",
                data: {
                    labels: pSeries.map(function (pt) {
                        return String(pt.dropCount);
                    }),
                    datasets: [{
                        label: "Poisson Probability P(X = k)",
                        data: pValues,
                        backgroundColor: pSeries.map(function (pt) {
                            return pt.selected ? "rgba(56, 189, 248, 0.92)" : "rgba(59, 130, 246, 0.36)";
                        }),
                        borderColor: pSeries.map(function (pt) {
                            return pt.selected ? "#e0f2fe" : "#38bdf8";
                        }),
                        borderWidth: pSeries.map(function (pt) {
                            return pt.selected ? 2.2 : 1.2;
                        }),
                        borderRadius: 6,
                        hoverBackgroundColor: pSeries.map(function (pt) {
                            return pt.selected ? "#7dd3fc" : "rgba(56, 189, 248, 0.62)";
                        })
                    }]
                },
                plugins: [highlightGuidePlugin],
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    animation: false,
                    layout: { padding: { top: 28, right: 10, left: 4, bottom: 4 } },
                    interaction: { mode: "index", intersect: false },
                    scales: {
                        x: {
                            title: {
                                display: true,
                                text: "Number of Transmission Drops (k)",
                                color: "#e2e8f0",
                                font: axisFont,
                                padding: { top: 8 }
                            },
                            ticks: { color: "#cbd5e1", font: tickFont },
                            grid: { color: "rgba(148, 163, 184, 0.08)" }
                        },
                        y: {
                            beginAtZero: true,
                            suggestedMax: computeProbabilitySuggestedMax(pMax, 0.3),
                            title: {
                                display: true,
                                text: "Probability P(X = k)",
                                color: "#e2e8f0",
                                font: axisFont,
                                padding: { bottom: 8 }
                            },
                            ticks: {
                                color: "#cbd5e1",
                                font: tickFont,
                                callback: function (val) {
                                    return formatProbabilityAxisTick(val, pMax);
                                }
                            },
                            grid: { color: "rgba(148, 163, 184, 0.14)" }
                        }
                    },
                    plugins: {
                        highlightGuide: {
                            index: selectedIdx,
                            label: "Selected k = " + payload.selectedDropCount,
                            color: "#38bdf8",
                            bgColor: "rgba(7, 23, 44, 0.95)"
                        },
                        legend: {
                            display: true,
                            labels: {
                                color: "#e2e8f0",
                                boxWidth: 14,
                                boxHeight: 14,
                                font: axisFont,
                                generateLabels: function () {
                                    return [
                                        {
                                            text: "Poisson P(X = k) (λ = " + payload.lambdaDisplay + ")",
                                            fillStyle: "rgba(59, 130, 246, 0.45)",
                                            strokeStyle: "#38bdf8",
                                            lineWidth: 1.5,
                                            fontColor: "#e2e8f0"
                                        },
                                        {
                                            text: "Highlighted Selected k = " + payload.selectedDropCount,
                                            fillStyle: "rgba(56, 189, 248, 0.95)",
                                            strokeStyle: "#e0f2fe",
                                            lineWidth: 2,
                                            fontColor: "#e2e8f0"
                                        }
                                    ];
                                }
                            }
                        },
                        tooltip: {
                            backgroundColor: "rgba(7, 11, 21, 0.96)",
                            borderColor: "rgba(56, 189, 248, 0.65)",
                            borderWidth: 1,
                            titleColor: "#f8fafc",
                            bodyColor: "#cbd5e1",
                            padding: 12,
                            displayColors: false,
                            callbacks: {
                                title: function (items) {
                                    if (!items || !items.length || !pSeries[items[0].dataIndex]) {
                                        return "";
                                    }
                                    const pt = pSeries[items[0].dataIndex];
                                    return "Transmission Drops k = " + pt.dropCount + (pt.selected ? " (Selected)" : "");
                                },
                                label: function (item) {
                                    const pt = pSeries[item.dataIndex];
                                    if (!pt) {
                                        return "";
                                    }
                                    return [
                                        "Number of drops: " + pt.dropCount,
                                        "Probability (Decimal): " + pt.probabilityDecimal,
                                        "Probability (Percentage): " + pt.probabilityPercent
                                    ];
                                }
                            }
                        }
                    }
                }
            });
        }

        // 2. Geometric Distribution Bar Chart (Green)
        const geometricCanvas = document.getElementById("geometric-chart");
        if (geometricCanvas && Array.isArray(payload.geometricSeries) && payload.geometricSeries.length > 0) {
            destroyChartInstance("geometric", geometricCanvas);
            const gSeries = payload.geometricSeries;
            const gValues = gSeries.map(function (pt) {
                return pt.probability;
            });
            const gMax = computeSeriesMax(gValues);
            const mostLikelyIdx = gSeries.findIndex(function (pt) {
                return pt.mostLikely;
            });

            chartInstances.geometric = new window.Chart(geometricCanvas.getContext("2d"), {
                type: "bar",
                data: {
                    labels: gSeries.map(function (pt) {
                        return "Attempt " + pt.attempt;
                    }),
                    datasets: [{
                        label: "First Success Probability P(X = k)",
                        data: gValues,
                        backgroundColor: gSeries.map(function (pt) {
                            return pt.mostLikely ? "rgba(52, 211, 153, 0.92)" : "rgba(16, 185, 129, 0.36)";
                        }),
                        borderColor: gSeries.map(function (pt) {
                            return pt.mostLikely ? "#ecfdf5" : "#34d399";
                        }),
                        borderWidth: gSeries.map(function (pt) {
                            return pt.mostLikely ? 2.2 : 1.2;
                        }),
                        borderRadius: 6,
                        hoverBackgroundColor: gSeries.map(function (pt) {
                            return pt.mostLikely ? "#6ee7b7" : "rgba(52, 211, 153, 0.62)";
                        })
                    }]
                },
                plugins: [highlightGuidePlugin],
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    animation: false,
                    layout: { padding: { top: 28, right: 10, left: 4, bottom: 4 } },
                    interaction: { mode: "index", intersect: false },
                    scales: {
                        x: {
                            title: {
                                display: true,
                                text: "Transmission Attempt Number",
                                color: "#e2e8f0",
                                font: axisFont,
                                padding: { top: 8 }
                            },
                            ticks: { color: "#cbd5e1", font: tickFont },
                            grid: { color: "rgba(148, 163, 184, 0.08)" }
                        },
                        y: {
                            beginAtZero: true,
                            suggestedMax: computeProbabilitySuggestedMax(gMax, 0.7),
                            title: {
                                display: true,
                                text: "Probability of First Success P(X = k)",
                                color: "#e2e8f0",
                                font: axisFont,
                                padding: { bottom: 8 }
                            },
                            ticks: {
                                color: "#cbd5e1",
                                font: tickFont,
                                callback: function (val) {
                                    return formatProbabilityAxisTick(val, gMax);
                                }
                            },
                            grid: { color: "rgba(148, 163, 184, 0.14)" }
                        }
                    },
                    plugins: {
                        highlightGuide: {
                            index: mostLikelyIdx,
                            label: "Most Likely: Attempt " + payload.mostLikelyAttempt,
                            color: "#34d399",
                            bgColor: "rgba(6, 44, 34, 0.95)"
                        },
                        legend: {
                            display: true,
                            labels: {
                                color: "#e2e8f0",
                                boxWidth: 14,
                                boxHeight: 14,
                                font: axisFont,
                                generateLabels: function () {
                                    return [
                                        {
                                            text: "Geometric P(X = k) (p = " + payload.successProbabilityDisplay + ")",
                                            fillStyle: "rgba(16, 185, 129, 0.42)",
                                            strokeStyle: "#34d399",
                                            lineWidth: 1.5,
                                            fontColor: "#e2e8f0"
                                        },
                                        {
                                            text: "Most Likely Attempt (" + payload.mostLikelyAttempt + ")",
                                            fillStyle: "rgba(52, 211, 153, 0.92)",
                                            strokeStyle: "#ecfdf5",
                                            lineWidth: 2,
                                            fontColor: "#e2e8f0"
                                        }
                                    ];
                                }
                            }
                        },
                        tooltip: {
                            backgroundColor: "rgba(7, 11, 21, 0.96)",
                            borderColor: "rgba(52, 211, 153, 0.65)",
                            borderWidth: 1,
                            titleColor: "#f8fafc",
                            bodyColor: "#cbd5e1",
                            padding: 12,
                            displayColors: false,
                            callbacks: {
                                title: function (items) {
                                    if (!items || !items.length || !gSeries[items[0].dataIndex]) {
                                        return "";
                                    }
                                    const pt = gSeries[items[0].dataIndex];
                                    return "Attempt " + pt.attempt + (pt.mostLikely ? " (Most Likely)" : "");
                                },
                                label: function (item) {
                                    const pt = gSeries[item.dataIndex];
                                    if (!pt) {
                                        return "";
                                    }
                                    return [
                                        "Attempt number: " + pt.attempt,
                                        "Probability (Decimal): " + pt.probabilityDecimal,
                                        "Probability (Percentage): " + pt.probabilityPercent
                                    ];
                                }
                            }
                        }
                    }
                }
            });
        }

        // 3. Communication Reliability Cumulative Line Chart (Purple)
        const reliabilityCanvas = document.getElementById("reliability-chart");
        if (reliabilityCanvas && Array.isArray(payload.reliabilitySeries) && payload.reliabilitySeries.length > 0) {
            destroyChartInstance("reliability", reliabilityCanvas);
            const rSeries = payload.reliabilitySeries;
            const rValues = rSeries.map(function (pt) {
                return pt.cumulativeProbability;
            });
            const rMax = computeSeriesMax(rValues);
            const selectedNIdx = rSeries.findIndex(function (pt) {
                return pt.selectedMaxAttempt;
            });

            chartInstances.reliability = new window.Chart(reliabilityCanvas.getContext("2d"), {
                type: "line",
                data: {
                    labels: rSeries.map(function (pt) {
                        return String(pt.attempt);
                    }),
                    datasets: [{
                        label: "Cumulative Reliability R(n) = 1 - (1 - p)^n",
                        data: rValues,
                        borderColor: "#a78bfa",
                        backgroundColor: "rgba(139, 92, 246, 0.18)",
                        borderWidth: 2.5,
                        fill: true,
                        tension: 0.22,
                        pointRadius: rSeries.map(function (pt) {
                            return pt.selectedMaxAttempt ? 7.5 : 4.5;
                        }),
                        pointHoverRadius: rSeries.map(function (pt) {
                            return pt.selectedMaxAttempt ? 9 : 6.5;
                        }),
                        pointBackgroundColor: rSeries.map(function (pt) {
                            return pt.selectedMaxAttempt ? "#ddd6fe" : "#8b5cf6";
                        }),
                        pointBorderColor: rSeries.map(function (pt) {
                            return pt.selectedMaxAttempt ? "#f8fafc" : "#c4b5fd";
                        }),
                        pointBorderWidth: rSeries.map(function (pt) {
                            return pt.selectedMaxAttempt ? 2.5 : 1.5;
                        })
                    }]
                },
                plugins: [highlightGuidePlugin],
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    animation: false,
                    layout: { padding: { top: 28, right: 16, left: 4, bottom: 4 } },
                    interaction: { mode: "index", intersect: false },
                    scales: {
                        x: {
                            title: {
                                display: true,
                                text: "Number of Attempts",
                                color: "#e2e8f0",
                                font: axisFont,
                                padding: { top: 8 }
                            },
                            ticks: { color: "#cbd5e1", font: tickFont },
                            grid: { color: "rgba(148, 163, 184, 0.08)" }
                        },
                        y: {
                            min: 0,
                            suggestedMax: computeProbabilitySuggestedMax(rMax, 1.0),
                            title: {
                                display: true,
                                text: "Cumulative Probability R(n)",
                                color: "#e2e8f0",
                                font: axisFont,
                                padding: { bottom: 8 }
                            },
                            ticks: {
                                color: "#cbd5e1",
                                font: tickFont,
                                callback: function (val) {
                                    return formatProbabilityAxisTick(val, rMax);
                                }
                            },
                            grid: { color: "rgba(148, 163, 184, 0.14)" }
                        }
                    },
                    plugins: {
                        highlightGuide: {
                            index: selectedNIdx,
                            label: "Selected n = " + payload.selectedMaxAttempts,
                            color: "#c4b5fd",
                            bgColor: "rgba(33, 18, 66, 0.95)"
                        },
                        legend: {
                            display: true,
                            labels: {
                                color: "#e2e8f0",
                                boxWidth: 14,
                                boxHeight: 14,
                                font: axisFont,
                                generateLabels: function () {
                                    return [
                                        {
                                            text: "Cumulative Reliability R(k) = 1 − (1 − p)^k",
                                            fillStyle: "rgba(139, 92, 246, 0.25)",
                                            strokeStyle: "#a78bfa",
                                            lineWidth: 2,
                                            fontColor: "#e2e8f0"
                                        },
                                        {
                                            text: "Selected Max Attempt (n = " + payload.selectedMaxAttempts + ")",
                                            fillStyle: "#ddd6fe",
                                            strokeStyle: "#f8fafc",
                                            lineWidth: 2,
                                            fontColor: "#e2e8f0"
                                        }
                                    ];
                                }
                            }
                        },
                        tooltip: {
                            backgroundColor: "rgba(7, 11, 21, 0.96)",
                            borderColor: "rgba(167, 139, 250, 0.65)",
                            borderWidth: 1,
                            titleColor: "#f8fafc",
                            bodyColor: "#cbd5e1",
                            padding: 12,
                            displayColors: false,
                            callbacks: {
                                title: function (items) {
                                    if (!items || !items.length || !rSeries[items[0].dataIndex]) {
                                        return "";
                                    }
                                    const pt = rSeries[items[0].dataIndex];
                                    const unit = pt.attempt === 1 ? "Attempt" : "Attempts";
                                    return "After " + pt.attempt + " " + unit + (pt.selectedMaxAttempt ? " (Selected n)" : "");
                                },
                                label: function (item) {
                                    const pt = rSeries[item.dataIndex];
                                    if (!pt) {
                                        return "";
                                    }
                                    return [
                                        "Attempts: " + pt.attempt,
                                        "Cumulative Probability (Decimal): " + pt.probabilityDecimal,
                                        "Cumulative Probability (Percentage): " + pt.probabilityPercent
                                    ];
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    function renderSingleOffscreenChart(width, height, config) {
        if (typeof window.Chart !== "function" || typeof document.createElement !== "function") {
            return null;
        }
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d");
        if (!ctx) {
            return null;
        }
        let tempChart = null;
        try {
            tempChart = new window.Chart(ctx, config);
            tempChart.update("none");
            return canvas.toDataURL("image/png");
        } catch (err) {
            return null;
        } finally {
            if (tempChart) {
                tempChart.destroy();
            }
        }
    }

    function renderAcademicReportChartImages() {
        const result = {
            poissonChartImage: null,
            geometricChartImage: null,
            reliabilityChartImage: null
        };
        if (typeof window.Chart !== "function") {
            return result;
        }

        const payload = readDashboardChartsPayload();
        const axisFont = {
            family: '"Inter", "Segoe UI", Arial, sans-serif',
            size: 13,
            weight: "600"
        };
        const tickFont = {
            family: '"JetBrains Mono", Consolas, monospace',
            size: 11
        };

        if (payload && Array.isArray(payload.poissonSeries) && payload.poissonSeries.length > 0) {
            const pSeries = payload.poissonSeries;
            const pValues = pSeries.map(function (pt) {
                return pt.probability;
            });
            const pMax = computeSeriesMax(pValues);
            const selectedIdx = pSeries.findIndex(function (pt) {
                return pt.selected;
            });
            result.poissonChartImage = renderSingleOffscreenChart(1120, 400, {
                type: "bar",
                data: {
                    labels: pSeries.map(function (pt) {
                        return String(pt.dropCount);
                    }),
                    datasets: [{
                        label: "Poisson Probability P(X = k)",
                        data: pValues,
                        backgroundColor: pSeries.map(function (pt) {
                            return pt.selected ? "rgba(29, 78, 216, 0.92)" : "rgba(59, 130, 246, 0.34)";
                        }),
                        borderColor: pSeries.map(function (pt) {
                            return pt.selected ? "#0f172a" : "#2563eb";
                        }),
                        borderWidth: pSeries.map(function (pt) {
                            return pt.selected ? 2.2 : 1.3;
                        }),
                        borderRadius: 6
                    }]
                },
                plugins: [academicCanvasBackgroundPlugin, highlightGuidePlugin],
                options: {
                    responsive: false,
                    maintainAspectRatio: false,
                    animation: false,
                    devicePixelRatio: 1,
                    layout: { padding: { top: 28, right: 20, left: 12, bottom: 10 } },
                    scales: {
                        x: {
                            title: {
                                display: true,
                                text: "Number of Transmission Drops (k)",
                                color: "#0f172a",
                                font: axisFont,
                                padding: { top: 8 }
                            },
                            ticks: { color: "#334155", font: tickFont },
                            grid: { color: "rgba(148, 163, 184, 0.18)" }
                        },
                        y: {
                            beginAtZero: true,
                            suggestedMax: computeProbabilitySuggestedMax(pMax, 0.3),
                            title: {
                                display: true,
                                text: "Probability P(X = k)",
                                color: "#0f172a",
                                font: axisFont,
                                padding: { bottom: 8 }
                            },
                            ticks: {
                                color: "#334155",
                                font: tickFont,
                                callback: function (val) {
                                    return formatProbabilityAxisTick(val, pMax);
                                }
                            },
                            grid: { color: "rgba(148, 163, 184, 0.28)" }
                        }
                    },
                    plugins: {
                        highlightGuide: {
                            index: selectedIdx,
                            label: "Selected k = " + payload.selectedDropCount,
                            color: "#1d4ed8",
                            bgColor: "#eff6ff",
                            textColor: "#0f172a"
                        },
                        legend: {
                            display: true,
                            labels: {
                                color: "#0f172a",
                                boxWidth: 14,
                                boxHeight: 14,
                                font: axisFont,
                                generateLabels: function () {
                                    return [
                                        {
                                            text: "Poisson P(X = k) (λ = " + payload.lambdaDisplay + ")",
                                            fillStyle: "rgba(59, 130, 246, 0.38)",
                                            strokeStyle: "#2563eb",
                                            lineWidth: 1.5,
                                            fontColor: "#0f172a"
                                        },
                                        {
                                            text: "Highlighted Selected k = " + payload.selectedDropCount,
                                            fillStyle: "rgba(29, 78, 216, 0.92)",
                                            strokeStyle: "#0f172a",
                                            lineWidth: 2,
                                            fontColor: "#0f172a"
                                        }
                                    ];
                                }
                            }
                        }
                    }
                }
            });
        }

        if (payload && Array.isArray(payload.geometricSeries) && payload.geometricSeries.length > 0) {
            const gSeries = payload.geometricSeries;
            const gValues = gSeries.map(function (pt) {
                return pt.probability;
            });
            const gMax = computeSeriesMax(gValues);
            const mostLikelyIdx = gSeries.findIndex(function (pt) {
                return pt.mostLikely;
            });
            result.geometricChartImage = renderSingleOffscreenChart(1120, 400, {
                type: "bar",
                data: {
                    labels: gSeries.map(function (pt) {
                        return "Attempt " + pt.attempt;
                    }),
                    datasets: [{
                        label: "First Success Probability P(X = k)",
                        data: gValues,
                        backgroundColor: gSeries.map(function (pt) {
                            return pt.mostLikely ? "rgba(4, 120, 87, 0.92)" : "rgba(16, 185, 129, 0.34)";
                        }),
                        borderColor: gSeries.map(function (pt) {
                            return pt.mostLikely ? "#0f172a" : "#059669";
                        }),
                        borderWidth: gSeries.map(function (pt) {
                            return pt.mostLikely ? 2.2 : 1.3;
                        }),
                        borderRadius: 6
                    }]
                },
                plugins: [academicCanvasBackgroundPlugin, highlightGuidePlugin],
                options: {
                    responsive: false,
                    maintainAspectRatio: false,
                    animation: false,
                    devicePixelRatio: 1,
                    layout: { padding: { top: 28, right: 20, left: 12, bottom: 10 } },
                    scales: {
                        x: {
                            title: {
                                display: true,
                                text: "Transmission Attempt Number",
                                color: "#0f172a",
                                font: axisFont,
                                padding: { top: 8 }
                            },
                            ticks: { color: "#334155", font: tickFont },
                            grid: { color: "rgba(148, 163, 184, 0.18)" }
                        },
                        y: {
                            beginAtZero: true,
                            suggestedMax: computeProbabilitySuggestedMax(gMax, 0.7),
                            title: {
                                display: true,
                                text: "Probability of First Success P(X = k)",
                                color: "#0f172a",
                                font: axisFont,
                                padding: { bottom: 8 }
                            },
                            ticks: {
                                color: "#334155",
                                font: tickFont,
                                callback: function (val) {
                                    return formatProbabilityAxisTick(val, gMax);
                                }
                            },
                            grid: { color: "rgba(148, 163, 184, 0.28)" }
                        }
                    },
                    plugins: {
                        highlightGuide: {
                            index: mostLikelyIdx,
                            label: "Most Likely: Attempt " + payload.mostLikelyAttempt,
                            color: "#047857",
                            bgColor: "#ecfdf5",
                            textColor: "#0f172a"
                        },
                        legend: {
                            display: true,
                            labels: {
                                color: "#0f172a",
                                boxWidth: 14,
                                boxHeight: 14,
                                font: axisFont,
                                generateLabels: function () {
                                    return [
                                        {
                                            text: "Geometric P(X = k) (p = " + payload.successProbabilityDisplay + ")",
                                            fillStyle: "rgba(16, 185, 129, 0.36)",
                                            strokeStyle: "#059669",
                                            lineWidth: 1.5,
                                            fontColor: "#0f172a"
                                        },
                                        {
                                            text: "Most Likely Attempt (" + payload.mostLikelyAttempt + ")",
                                            fillStyle: "rgba(4, 120, 87, 0.92)",
                                            strokeStyle: "#0f172a",
                                            lineWidth: 2,
                                            fontColor: "#0f172a"
                                        }
                                    ];
                                }
                            }
                        }
                    }
                }
            });
        }

        if (payload && Array.isArray(payload.reliabilitySeries) && payload.reliabilitySeries.length > 0) {
            const rSeries = payload.reliabilitySeries;
            const rValues = rSeries.map(function (pt) {
                return pt.cumulativeProbability;
            });
            const rMax = computeSeriesMax(rValues);
            const selectedNIdx = rSeries.findIndex(function (pt) {
                return pt.selectedMaxAttempt;
            });
            result.reliabilityChartImage = renderSingleOffscreenChart(1120, 400, {
                type: "line",
                data: {
                    labels: rSeries.map(function (pt) {
                        return String(pt.attempt);
                    }),
                    datasets: [{
                        label: "Cumulative Reliability R(k) = 1 − (1 − p)^k",
                        data: rValues,
                        borderColor: "#4f46e5",
                        backgroundColor: "rgba(79, 70, 229, 0.14)",
                        borderWidth: 2.8,
                        fill: true,
                        tension: 0.22,
                        pointRadius: rSeries.map(function (pt) {
                            return pt.selectedMaxAttempt ? 7.5 : 4.5;
                        }),
                        pointBackgroundColor: rSeries.map(function (pt) {
                            return pt.selectedMaxAttempt ? "#ffffff" : "#4f46e5";
                        }),
                        pointBorderColor: rSeries.map(function (pt) {
                            return pt.selectedMaxAttempt ? "#4f46e5" : "#312e81";
                        }),
                        pointBorderWidth: rSeries.map(function (pt) {
                            return pt.selectedMaxAttempt ? 3 : 1.5;
                        })
                    }]
                },
                plugins: [academicCanvasBackgroundPlugin, highlightGuidePlugin],
                options: {
                    responsive: false,
                    maintainAspectRatio: false,
                    animation: false,
                    devicePixelRatio: 1,
                    layout: { padding: { top: 28, right: 24, left: 12, bottom: 10 } },
                    scales: {
                        x: {
                            title: {
                                display: true,
                                text: "Number of Attempts",
                                color: "#0f172a",
                                font: axisFont,
                                padding: { top: 8 }
                            },
                            ticks: { color: "#334155", font: tickFont },
                            grid: { color: "rgba(148, 163, 184, 0.18)" }
                        },
                        y: {
                            min: 0,
                            suggestedMax: computeProbabilitySuggestedMax(rMax, 1.0),
                            title: {
                                display: true,
                                text: "Cumulative Probability R(n)",
                                color: "#0f172a",
                                font: axisFont,
                                padding: { bottom: 8 }
                            },
                            ticks: {
                                color: "#334155",
                                font: tickFont,
                                callback: function (val) {
                                    return formatProbabilityAxisTick(val, rMax);
                                }
                            },
                            grid: { color: "rgba(148, 163, 184, 0.28)" }
                        }
                    },
                    plugins: {
                        highlightGuide: {
                            index: selectedNIdx,
                            label: "Selected n = " + payload.selectedMaxAttempts,
                            color: "#4f46e5",
                            bgColor: "#eef2ff",
                            textColor: "#0f172a"
                        },
                        legend: {
                            display: true,
                            labels: {
                                color: "#0f172a",
                                boxWidth: 14,
                                boxHeight: 14,
                                font: axisFont,
                                generateLabels: function () {
                                    return [
                                        {
                                            text: "Cumulative Reliability R(k) = 1 − (1 − p)^k",
                                            fillStyle: "rgba(79, 70, 229, 0.18)",
                                            strokeStyle: "#4f46e5",
                                            lineWidth: 2,
                                            fontColor: "#0f172a"
                                        },
                                        {
                                            text: "Selected Max Attempt (n = " + payload.selectedMaxAttempts + ")",
                                            fillStyle: "#ffffff",
                                            strokeStyle: "#4f46e5",
                                            lineWidth: 2.5,
                                            fontColor: "#0f172a"
                                        }
                                    ];
                                }
                            }
                        }
                    }
                }
            });
        }

        return result;
    }

    function extractFilenameFromHeader(headerValue, fallbackName) {
        if (!headerValue) {
            return fallbackName;
        }
        const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(headerValue);
        if (utf8Match && utf8Match[1]) {
            try {
                return decodeURIComponent(utf8Match[1].trim());
            } catch (err) {
                return utf8Match[1].trim();
            }
        }
        const basicMatch = /filename="?([^";]+)"?/i.exec(headerValue);
        if (basicMatch && basicMatch[1]) {
            return basicMatch[1].trim();
        }
        return fallbackName;
    }

    function initDownloadPdfReport() {
        const btn = document.getElementById("download-report-btn");
        if (!btn) {
            return;
        }

        const labelEl = btn.querySelector(".download-btn-label");
        const statusBanner = document.getElementById("report-download-status");
        const statusText = document.getElementById("report-download-status-text");
        const errorBanner = document.getElementById("report-download-error");
        const errorText = document.getElementById("report-download-error-text");

        function setStatus(message) {
            if (errorBanner) {
                errorBanner.hidden = true;
            }
            if (statusBanner && statusText) {
                if (message) {
                    statusText.textContent = message;
                    statusBanner.hidden = false;
                } else {
                    statusText.textContent = "";
                    statusBanner.hidden = true;
                }
            }
        }

        function setError(message) {
            if (statusBanner) {
                statusBanner.hidden = true;
            }
            if (errorBanner && errorText) {
                if (message) {
                    errorText.textContent = message;
                    errorBanner.hidden = false;
                } else {
                    errorText.textContent = "";
                    errorBanner.hidden = true;
                }
            }
        }

        btn.addEventListener("click", function () {
            if (btn.disabled) {
                return;
            }

            const endpoint = btn.getAttribute("data-endpoint") || "/analysis/report/pdf";
            const defaultFilename = "DeepSpace-Analytics-Report.pdf";
            const requestBody = {
                lambda: btn.getAttribute("data-lambda") || "",
                dropCount: btn.getAttribute("data-drop-count") || "",
                successProbability: btn.getAttribute("data-success-probability") || "",
                maxAttempts: btn.getAttribute("data-max-attempts") || ""
            };
            const analysisType = btn.getAttribute("data-analysis-type");
            if (analysisType) {
                requestBody.analysisType = analysisType;
            }

            const snapshots = renderAcademicReportChartImages();
            if (snapshots.poissonChartImage) {
                requestBody.poissonChartImage = snapshots.poissonChartImage;
            }
            if (snapshots.geometricChartImage) {
                requestBody.geometricChartImage = snapshots.geometricChartImage;
            }
            if (snapshots.reliabilityChartImage) {
                requestBody.reliabilityChartImage = snapshots.reliabilityChartImage;
            }

            setStatus("");
            setError("");
            btn.disabled = true;
            btn.classList.add("is-loading");
            if (labelEl) {
                if (!btn.hasAttribute("data-original-label")) {
                    btn.setAttribute("data-original-label", labelEl.textContent);
                }
                labelEl.textContent = "Generating PDF Report...";
            }

            window.fetch(endpoint, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/pdf, application/json"
                },
                body: JSON.stringify(requestBody)
            }).then(function (response) {
                const contentType = (response.headers.get("Content-Type") || "").toLowerCase();
                if (!response.ok || contentType.indexOf("application/pdf") === -1) {
                    return response.text().then(function (rawText) {
                        let msg = "Unable to generate the PDF report right now. Your dashboard data remains intact.";
                        if (rawText) {
                            try {
                                const parsed = JSON.parse(rawText);
                                if (parsed && parsed.generalError) {
                                    msg = parsed.generalError;
                                }
                            } catch (err) {
                                // Retain fallback error message
                            }
                        }
                        throw new Error(msg);
                    });
                }

                const filename = extractFilenameFromHeader(
                    response.headers.get("Content-Disposition"),
                    defaultFilename
                );
                return response.blob().then(function (blob) {
                    if (!blob || blob.size === 0) {
                        throw new Error("Generated PDF report was empty. Please try again.");
                    }
                    return { blob: blob, filename: filename };
                });
            }).then(function (result) {
                const objectUrl = window.URL.createObjectURL(result.blob);
                const downloadLink = document.createElement("a");
                downloadLink.href = objectUrl;
                downloadLink.download = result.filename;
                downloadLink.style.display = "none";
                document.body.appendChild(downloadLink);
                downloadLink.click();
                document.body.removeChild(downloadLink);
                window.setTimeout(function () {
                    window.URL.revokeObjectURL(objectUrl);
                }, 4000);

                setStatus(
                    'Mathematical PDF analysis report "' + result.filename +
                    '" downloaded successfully. Your current dashboard results remain active.'
                );
            }).catch(function (err) {
                setError(err && err.message ? err.message : "Failed to generate the PDF report. Please try again.");
            }).finally(function () {
                btn.disabled = false;
                btn.classList.remove("is-loading");
                if (labelEl) {
                    const orig = btn.getAttribute("data-original-label") || "Download PDF Report";
                    labelEl.textContent = orig;
                }
            });
        });

        window.addEventListener("pageshow", function () {
            btn.disabled = false;
            btn.classList.remove("is-loading");
            if (labelEl) {
                const orig = btn.getAttribute("data-original-label") || "Download PDF Report";
                labelEl.textContent = orig;
            }
        });
    }

    function initAboutDialog() {
        const triggers = document.querySelectorAll("#about-dialog-trigger, [data-open-about]");
        const backdrop = document.getElementById("about-dialog-backdrop");
        const card = document.getElementById("about-dialog-card");
        const closeBtn = document.getElementById("about-dialog-close-btn");
        const dismissBtn = document.getElementById("about-dialog-dismiss-btn");

        if (!backdrop || !card) {
            return;
        }

        let lastActiveTrigger = null;

        function openDialog(triggerEl) {
            lastActiveTrigger = triggerEl || document.activeElement;
            backdrop.hidden = false;

            // Compute origin coordinates from trigger position for spring-like expansion
            const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
            if (lastActiveTrigger && !prefersReducedMotion) {
                const rect = lastActiveTrigger.getBoundingClientRect();
                const originX = Math.round(rect.left + rect.width / 2);
                const originY = Math.round(rect.top + rect.height / 2);
                card.style.transformOrigin = originX + "px " + originY + "px";
            } else {
                card.style.transformOrigin = "center center";
            }

            // Force reflow before applying active class
            void backdrop.offsetWidth;
            backdrop.classList.add("is-open");
            document.body.classList.add("dialog-open");

            triggers.forEach(function (btn) {
                btn.setAttribute("aria-expanded", "true");
            });

            // Focus management: move focus to close button
            if (closeBtn) {
                closeBtn.focus();
            } else {
                card.focus();
            }
        }

        function closeDialog() {
            if (!backdrop.classList.contains("is-open")) {
                return;
            }
            backdrop.classList.remove("is-open");
            document.body.classList.remove("dialog-open");

            triggers.forEach(function (btn) {
                btn.setAttribute("aria-expanded", "false");
            });

            const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
            const delay = prefersReducedMotion ? 0 : 220;

            window.setTimeout(function () {
                backdrop.hidden = true;
                if (lastActiveTrigger && typeof lastActiveTrigger.focus === "function") {
                    lastActiveTrigger.focus();
                }
            }, delay);
        }

        triggers.forEach(function (trigger) {
            trigger.addEventListener("click", function (event) {
                event.preventDefault();
                openDialog(trigger);
            });
        });

        if (closeBtn) {
            closeBtn.addEventListener("click", function (event) {
                event.preventDefault();
                closeDialog();
            });
        }

        if (dismissBtn) {
            dismissBtn.addEventListener("click", function (event) {
                event.preventDefault();
                closeDialog();
            });
        }

        // Close on backdrop click (outside card)
        backdrop.addEventListener("click", function (event) {
            if (event.target === backdrop) {
                closeDialog();
            }
        });

        // Keyboard handler for Escape and Tab trapping
        document.addEventListener("keydown", function (event) {
            if (!backdrop.classList.contains("is-open")) {
                return;
            }

            if (event.key === "Escape") {
                event.preventDefault();
                closeDialog();
                return;
            }

            if (event.key === "Tab") {
                const focusables = card.querySelectorAll(
                    'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
                );
                if (!focusables || focusables.length === 0) {
                    return;
                }
                const first = focusables[0];
                const last = focusables[focusables.length - 1];

                if (event.shiftKey) {
                    if (document.activeElement === first || document.activeElement === card) {
                        event.preventDefault();
                        last.focus();
                    }
                } else {
                    if (document.activeElement === last) {
                        event.preventDefault();
                        first.focus();
                    }
                }
            }
        });
    }

    function initBackToTop() {
        const btn = document.getElementById("back-to-top-btn");
        if (!btn) {
            return;
        }

        const SCROLL_THRESHOLD = 320;
        let ticking = false;

        function updateVisibility() {
            const currentScroll = window.scrollY || document.documentElement.scrollTop || 0;
            if (currentScroll > SCROLL_THRESHOLD) {
                btn.hidden = false;
                void btn.offsetWidth;
                btn.classList.add("is-visible");
            } else {
                btn.classList.remove("is-visible");
            }
            ticking = false;
        }

        btn.addEventListener("transitionend", function () {
            if (!btn.classList.contains("is-visible")) {
                btn.hidden = true;
            }
        });

        window.addEventListener("scroll", function () {
            if (!ticking) {
                window.requestAnimationFrame(updateVisibility);
                ticking = true;
            }
        }, { passive: true });

        btn.addEventListener("click", function (event) {
            event.preventDefault();
            const prefersReducedMotion = window.matchMedia &&
                window.matchMedia("(prefers-reduced-motion: reduce)").matches;

            window.scrollTo({
                top: 0,
                behavior: prefersReducedMotion ? "auto" : "smooth"
            });

            const mainContent = document.getElementById("main-content") || document.body;
            if (mainContent) {
                if (!mainContent.hasAttribute("tabindex")) {
                    mainContent.setAttribute("tabindex", "-1");
                }
                mainContent.focus({ preventScroll: true });
            }
        });

        updateVisibility();
    }

    window.DeepSpaceCharts = {
        render: renderAllCharts,
        destroy: destroyAllCharts
    };

    window.addEventListener("beforeunload", destroyAllCharts);

    document.addEventListener("DOMContentLoaded", function () {
        initMobileNav();
        initSmoothScroll();
        initRevealAnimations();
        initAnalysisForm();
        renderAllCharts();
        initDownloadPdfReport();
        initAboutDialog();
        initBackToTop();
    });
})();

