package com.cellclaw.tools

/**
 * Risk tier categorization for tools available to the AI agent.
 *
 * Enforces the least-privilege security principle:
 * - [READ]: Read-only observation of system/app state (sensors, calendar query, file read).
 * - [REVERSIBLE_WRITE]: Benign mutations that can be undone or have minimal consequence (clipboard, notification).
 * - [CONSEQUENTIAL_WRITE]: Irreversible, high-impact actions (form submission, file overwrites, scheduled alarms, external communications)
 *   that strictly require an [com.cellclaw.approval.ApprovalToken] originating from an explicit, direct user tap.
 */
enum class RiskTier {
    READ,
    REVERSIBLE_WRITE,
    CONSEQUENTIAL_WRITE
}
