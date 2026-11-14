# Contributing to SafePath AI

Welcome to the **SafePath AI** team! This guide outlines our Git workflow, coding standards, and pull request procedures to ensure smooth collaboration across all team members.

---

## 1. Branching Strategy

To prevent merge conflicts and protect the production codebase, we follow a strict **GitFlow-inspired** branching model:

* **`main`**: The protected production branch. Always stable, build-passing, and deployable. **Direct commits to `main` are strictly forbidden.**
* **`develop`**: The shared integration branch where tested features converge.
* **Feature Branches (`feature/<name>`)**: Individual branches for new capabilities.
* **Bugfix Branches (`bugfix/<issue>`)**: Targeted fixes for verified defects.

### Naming Conventions
| Branch Prefix | Purpose | Example |
| :--- | :--- | :--- |
| `feature/` | New functionality or UI additions | `feature/android-ui`, `feature/gps-tracking`, `feature/voice-codeword` |
| `bugfix/` | Fixing a bug or unexpected behavior | `bugfix/location-jitter`, `bugfix/timeout-handling` |
| `docs/` | Documentation improvements | `docs/api-specs`, `docs/setup-guide` |
| `test/` | Adding test suites or benchmarks | `test/backend-auth-tests`, `test/ml-eval` |

### Recommended Feature Branches:
- `feature/android-ui`
- `feature/gps-tracking`
- `feature/emergency-contacts`
- `feature/manual-sos`
- `feature/voice-codeword`
- `feature/crime-risk-ml`
- `feature/backend-api`
- `feature/safety-chatbot`

---

## 2. Step-by-Step Developer Workflow

### Step 1: Clone the Repository
```bash
git clone https://github.com/YOUR_ORGANIZATION/SafePath-AI.git
cd SafePath-AI
```

### Step 2: Create Your Working Branch
Always branch off the latest `develop` (or `main`):
```bash
git checkout develop
git pull origin develop
git checkout -b feature/your-feature-name
```

### Step 3: Implement & Test
Make your changes following the architectural constraints:
* **Android:** 100% Java + XML. **No Kotlin or Jetpack Compose.**
* **Backend:** Python 3.11+ with FastAPI, Pydantic, and SQLAlchemy.
* **Privacy:** On-device voice processing only (`EXTRA_PREFER_OFFLINE`).
* **Emergency Dispatch:** Alerts send exclusively to user-verified contacts.

Run tests locally before committing:
```bash
# Backend tests
cd backend
pytest

# ML tests
cd ../ml-risk
pytest
```

### Step 4: Commit with Meaningful Messages
Write clear, imperative commit messages:
```bash
git add .
git commit -m "feat(sos): implement offline queue persistence for emergency events"
```
**Commit Message Format:**
- `feat(...)`: A new feature
- `fix(...)`: A bug fix
- `docs(...)`: Documentation changes
- `refactor(...)`: Code change that neither fixes a bug nor adds a feature
- `test(...)`: Adding missing tests

### Step 5: Push and Open a Pull Request
```bash
git push -u origin feature/your-feature-name
```

1. Navigate to the repository on GitHub.
2. Click **Compare & pull request**.
3. Fill out the provided **Pull Request Template**.
4. Set the base branch to **`develop`** (or `main` for release).
5. Request a review from at least one teammate.

### Step 6: Review & Merge
* At least **1 approving review** is required.
* All automated CI checks must pass.
* Merge using **Squash and Merge** or **Rebase and Merge** to maintain a clean linear history.

---

## 3. Strict Rules & Sensitive Information

1. **NEVER commit secrets:**
   - No `.env` files.
   - No API keys, passwords, or tokens.
   - No local database files (`*.db`, `*.sqlite`).
   - No personal IP addresses.
2. **Local Configuration:**
   - Copy `backend/.env.example` $\rightarrow$ `backend/.env`.
   - Copy `android-app/gradle.properties.example` $\rightarrow$ `android-app/gradle.properties`.
3. **Respect Code Standards:**
   - Do not replace working code with mock/dummy implementations.
   - Maintain MVVM separation of concerns in the Android app.
