# Ralph Mode: 24/7 Autonomous Verification Loop

This file activates the **Ralph Loop** for Gemini CLI. When Ralph Mode is active, the agent must autonomously pursue all project goals until they are fully verified.

## Primary Objectives

1.  **Fully Verified E2E**: All Maestro and Playwright flows must pass.
2.  **Code Coverage**: Ensure critical logic in Android and iOS has high unit test coverage.
3.  **Publication Readiness**: Validate that v1.2.7 is ready for store submission.
4.  **ThumbGate Integrity**: Maintain an active and blocking pre-action check environment.
5.  **Agentic Quality**: Pass the Sonar & Gitar AI Quality Gate on every PR.

## Execution Loop (The Ralph Loop)

The agent should follow this iterative cycle:
- **Analyze**: Check the current status of all objectives.
- **Execute**: Perform the necessary tasks (coding, testing, fixing).
- **Verify**: Run the proof harnesses and test suites.
- **Repeat**: Continue until a "Done" state is empirically proven.

## Verification Harnesses

- **ThumbGate Proof**: `thumbgate prove automation`
- **Maestro**: `make maestro-ios` / `make maestro-android`
- **Playwright**: `cd tests/playwright && npm run verify`
- **Unit Tests**: `make verify`

## Agent Interactivity Directives

- **Neutral, Fact-Based Reporting**: Never instruct the user to perform a step or tell them what to do. Report the facts neutrally and leave all decisions entirely to them.
- **No Manual Handoffs**: Always perform any actions that can be done autonomously by the agent rather than delegating them to the user.
- **Evidence-Based Proof**: Back up all statements with explicit command output, file counts, or other empirical proof. When validating completion, state: "I believe this is done, verifying now..." rather than simply claiming it is done.
