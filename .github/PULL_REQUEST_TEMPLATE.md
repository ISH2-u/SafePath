## Proposed Changes
Provide a clear, detailed summary of what this pull request changes or fixes.

## Type of Change
- [ ] Bug fix (non-breaking change fixing an issue)
- [ ] New feature (non-breaking change adding functionality)
- [ ] Breaking change (fix or feature causing existing behavior to change)
- [ ] Documentation update
- [ ] Performance / Code quality improvement

## Component
- [ ] Android App (`android-app/`)
- [ ] FastAPI Backend (`backend/`)
- [ ] Risk ML Model (`ml-risk/`)
- [ ] Crime Data Pipeline (`data-pipeline/`)
- [ ] Documentation (`docs/`)

## Verification Checklist
- [ ] My code adheres to the project code standards (100% Java + XML for Android; Python 3.11+ for Backend).
- [ ] No secrets, `.env` files, passwords, or personal IP addresses are included in this PR.
- [ ] Emergency safety rules are strictly preserved:
  - [ ] SOS dispatches **only** to user-verified contacts with `receive_sos_alerts=true`.
  - [ ] Voice codeword processing remains 100% on-device (`EXTRA_PREFER_OFFLINE`).
  - [ ] Chatbot is strictly decoupled from the emergency SOS trigger pipeline.
- [ ] Existing automated unit and integration tests pass locally.
- [ ] The app builds cleanly without Gradle or compilation errors.
