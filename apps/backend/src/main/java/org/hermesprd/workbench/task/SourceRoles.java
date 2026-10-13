package org.hermesprd.workbench.task;

public final class SourceRoles {
    public static final String DRAFT_PREFIX = "生成稿-";
    public static final String REVIEW_EXTRA_PREFIX = "评审补充-";
    public static final String DEMO_EXTRA_PREFIX = "演示补充-";
    public static final String POC_EXTRA_PREFIX = "POC补充-";
    public static final String ESTIMATE_EXTRA_PREFIX = "评估补充-";
    public static final String TEST_CASE_EXTRA_PREFIX = "用例补充-";
    public static final String TECH_SPEC_EXTRA_PREFIX = "方案补充-";

    private SourceRoles() {
    }

    public static boolean isReviewMaterial(String name) {
        return name != null && (name.startsWith(DRAFT_PREFIX)
                || name.startsWith(REVIEW_EXTRA_PREFIX)
                || name.startsWith(DEMO_EXTRA_PREFIX)
                || name.startsWith(POC_EXTRA_PREFIX)
                || name.startsWith(ESTIMATE_EXTRA_PREFIX)
                || name.startsWith(TEST_CASE_EXTRA_PREFIX)
                || name.startsWith(TECH_SPEC_EXTRA_PREFIX));
    }

    public static String draftName(String runId) {
        return DRAFT_PREFIX + runId + ".md";
    }

    public static String draftRunId(String name) {
        if (name == null || !name.startsWith(DRAFT_PREFIX) || !name.endsWith(".md")) {
            return null;
        }
        String runId = name.substring(DRAFT_PREFIX.length(), name.length() - 3);
        return runId.isBlank() ? null : runId;
    }

    public static boolean visibleForMode(String mode, String name) {
        return visibleForRun(mode, name, null, false);
    }

    public static boolean visibleForMode(String mode, String name, boolean hasDraft) {
        return visibleForRun(mode, name, null, hasDraft);
    }

    public static boolean visibleForRun(String mode, String name, String parentRunId) {
        return visibleForRun(mode, name, parentRunId, false);
    }

    public static boolean visibleForRun(String mode, String name, String parentRunId, boolean hasDraft) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if ("GENERATE".equals(mode)) {
            if (parentRunId != null && !parentRunId.isBlank()
                    && name.equals(draftName(parentRunId))) {
                return true;
            }
            return !isReviewMaterial(name);
        }
        if ("REVIEW".equals(mode)) {
            if (hasDraft) {
                return name.startsWith(DRAFT_PREFIX) || name.startsWith(REVIEW_EXTRA_PREFIX);
            }
            return !isReviewMaterial(name) || name.startsWith(REVIEW_EXTRA_PREFIX);
        }
        if ("DEMO".equals(mode)) {
            if (hasDraft) {
                return name.startsWith(DRAFT_PREFIX) || name.startsWith(DEMO_EXTRA_PREFIX);
            }
            return !isReviewMaterial(name) || name.startsWith(DEMO_EXTRA_PREFIX);
        }
        if ("POC".equals(mode)) {
            if (hasDraft) {
                return name.startsWith(DRAFT_PREFIX) || name.startsWith(POC_EXTRA_PREFIX);
            }
            return !isReviewMaterial(name) || name.startsWith(POC_EXTRA_PREFIX);
        }
        if ("ESTIMATE".equals(mode)) {
            if (hasDraft) {
                return name.startsWith(DRAFT_PREFIX) || name.startsWith(ESTIMATE_EXTRA_PREFIX);
            }
            return !isReviewMaterial(name) || name.startsWith(ESTIMATE_EXTRA_PREFIX);
        }
        if ("TEST_CASE".equals(mode)) {
            if (hasDraft) {
                return name.startsWith(DRAFT_PREFIX) || name.startsWith(TEST_CASE_EXTRA_PREFIX);
            }
            return !isReviewMaterial(name) || name.startsWith(TEST_CASE_EXTRA_PREFIX);
        }
        if ("TECH_SPEC".equals(mode)) {
            if (hasDraft) {
                return name.startsWith(DRAFT_PREFIX) || name.startsWith(TECH_SPEC_EXTRA_PREFIX);
            }
            return !isReviewMaterial(name) || name.startsWith(TECH_SPEC_EXTRA_PREFIX);
        }
        return !isReviewMaterial(name);
    }
}
