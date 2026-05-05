# GitHub Copilot Setup Checklist

Use this checklist to finish repo-level Copilot automation for AnswerGuard.

## Enable Copilot Features

1. Open `https://github.com/IgorGanapolsky/AnswerGuard/settings`.
2. Enable Copilot coding agent for this repository.
3. Enable automatic Copilot code review on protected branch rulesets.
4. Enable Copilot memory if available for the account.

## Expected Repo Files

- `.github/copilot-instructions.md`
- `.github/instructions/android.instructions.md`
- `.github/instructions/ios.instructions.md`

## Verification

1. Open a small test issue and assign it to Copilot.
2. Open a PR and confirm Copilot review is requested automatically.
3. Ask Copilot about AnswerGuard's Android call screening flow; it should refer to `CallScreeningService`, not timer code.
