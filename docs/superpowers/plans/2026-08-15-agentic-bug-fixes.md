# Agentic Bug Remediation Plan

## Goal

Fix the functional defects recorded in `BUG_HUNT.md` without changing the scope to security or
concurrency work. Each batch must have focused regression coverage and an audit status update.

## Order

1. Harden SDK contracts: schema validation, descriptor consistency, prerequisite ownership, retry
   semantics, and command catalog completeness.
2. Harden router state: aggregate context limits, target-aware freshness, chronological context,
   duplicate suppression, response termination, and tool evidence persistence.
3. Harden moderation orchestration: mode-specific capabilities, target binding, immediate flush,
   bot-name matching, and closed-service behavior.
4. Verify with focused tests, then the full Maven suite; document any residual limitation.

## Invariants

- A tool call is executed only when it matches the published contract.
- A fresh-data request is satisfied only by fresh data for the requested subject.
- A silent moderation action may still produce an immediate side effect, but never a chat reply.
- The current inbound message is not duplicated in the room context.
- A provider response terminated by length is never presented as a complete answer.

## Verification

Run the smallest relevant test class after each batch, then `mvn test`. Update each ledger item in
`BUG_HUNT.md` from Planned to Implemented and Verified only after the corresponding checks pass.
