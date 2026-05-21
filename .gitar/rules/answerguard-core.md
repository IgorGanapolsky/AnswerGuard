# AnswerGuard Core AI Review Rules

These rules are enforced by Gitar AI during the code review process.

## 1. On-Device Privacy
- Ensure that call screening data, contact info, and audio analysis never leave the device.
- Block any code that introduces network requests to external servers for PII data.

## 2. Gemini Intent Analysis
- Verify that Gemini Nano is used for intent analysis and it's always running on-device.
- Intent analysis must be synchronous or use the non-blocking pattern defined in `AnswerGuardScreeningService`.

## 3. High-Fidelity Standards
- All UI components must use the tactical 2026 design language (Emerald/DeepNavy palette).
- Named exports only for Kotlin and Swift shared logic.

## 4. Agentic Hallucination Drift
- Enforce strict deterministic guardrails on any new AI agent logic to prevent hallucination.
