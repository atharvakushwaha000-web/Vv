# VaultGold Rewards

**VaultGold Rewards** is an independent, luxury dark-and-gold Android rewards and community platform where users earn verified coins through sponsored app missions, brain puzzles, daily tasks, quizzes, referrals, and optional Unity rewarded ads, and redeem balances for genuine gift-code inventory alongside a moderated community redeem-code information center and a 20-module role-based Admin Console.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following product, architecture, and compliance decisions have been aligned from your answers and the master specification. Review the confirmed choices and external configuration checklist below before we proceed to provisioning and code execution.

- **Confirmed Brand Identity**: **VaultGold Rewards** — an original, independent brand with mandatory "Unofficial Community App" disclosures in Onboarding, About, and the Redeem-Code Center (zero Garena, Free Fire, BOOYAH, Google Play, or YouTube affiliation claims).
- **Confirmed Backend & Authentication Strategy**: **Provision AI Studio Firebase (Firestore Enterprise + Google Sign-In)** — uses Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) and an isolated custom Firestore database instance enforced by zero-trust security rules, atomic Firestore transactions (`runTransaction`), and global idempotency keys (`idempotency/{key}`).
- **Confirmed Admin Console Delivery**: **Full 20-Module Admin Console Inside the Android App** — gated by RBAC roles (`superAdmin`, `campaignManager`, `contentModerator`, `financeAdmin`, `support`, `analyst`), requiring mandatory action reasons and writing immutable `adminAuditLogs` for all state, campaign, and balance operations.
- **Confirmed Unity Ads Configuration**: **Unity Ads Android SDK (`com.unity3d.ads:unity-ads:4.21.0`) in Test Mode with Game ID `800370914`** — encapsulated behind a modular `AdProvider` interface (`UnityAdProvider`) with explicit state machine transitions (`unavailable → loading → ready → displayed → completed → verification_pending → approved | rejected | failed`), 1 coin per completed ad, strict daily caps, and zero ads on forbidden triggers (YouTube, Play Store redirects, navigation, or wallet views).

> [!NOTE]
> **Missing External Configuration & Dashboard Checklist (Section 0.3 & 19 Report)**
> Because no credentials, placement IDs, or inventory are ever fabricated, the following external items are documented for production cutover while the app operates safely with AI Studio's provisioned Firebase backend and Unity Test Mode:
> 1. **External Legacy RTDB Project (`atharva-7ef4b`)**: Missing production `apiKey`, `messagingSenderId`, and `appId` from Firebase Console if migrating off the AI Studio provisioned Firestore instance later.
> 2. **Unity Dashboard Confirmation**: Confirm whether Game ID `800370914` (*MindFlow Brain Puzzle*) is authorized for VaultGold Rewards or replace with a dedicated Unity project Game ID; supply production Rewarded Placement ID(s) (defaults to configurable test placement in Test Mode) and S2S HMAC callback secret.
> 3. **Third-Party Attribution & Inventory**: Offerwall/MMP webhook signing key (campaigns default to `[M] Manual Review` with evidence hash deduplication until configured), real KMS-encrypted gift-code stock imported via Admin Module 10, and legal sign-off on Privacy Policy URL, Support Email, and 18+ DPDP Act age-gate terms.

---

### 1. Overview & Core Concept

- **What It Does**: Delivers a transparent, fraud-resistant mobile earning and community ecosystem. Users complete multi-step app install missions, solve 10 categories of anti-cheat validated brain puzzles, complete daily check-ins and quizzes, watch eligible discovery or verified video campaigns, opt into single-tap Unity rewarded ads, and redeem earned coins in a stock-backed Rewards Store—while admins manage all 20 operational modules directly from the role-gated Admin Console.
- **Target Audience / Persona**: Mobile gamers and reward enthusiasts (18+ verified for redemptions and personalized ads) seeking a trustworthy, transparent platform with clear verification badges (`[V] Verified`, `[A] Partner Attributed`, `[M] Team Reviewed`, `[N] Discovery Only`) and zero deceptive timers or fake stock claims.
- **Key Value**: Every coin is backed by an immutable ledger entry (`wallet.available == sum(approved ledger)`), every mission and video states its exact verification mode upfront, and every admin action is RBAC-governed and audited.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Onboarding, Consent & Google Auth (J1 & J9)**: Animated splash → 3-card onboarding showcase (Earn, Play, Redeem + Unofficial Disclaimer) → 18+ age & privacy consent gate → interactive **Sign in with Google** via Jetpack Credential Manager (plus explicit Guest Browse mode that prompts sign-in on any monetary action) → automatic profile, zeroed wallet, and unique referral code creation → optional one-time referral code redemption → Account Deletion workflow with re-auth and data anonymization.
  2. **Apps Install Handler & Running Missions (J2)**: Browse 8 curated sections (*Running Campaigns, Recommended Apps, New Missions, Trending, High-Reward, Nearly Completed, Completed, Available Rewards*) with **View All** search, category chips, sort (Reward, Newest, ETA), status filter, and pagination → open **Mission Details** showing official `play.google.com` launcher button, step-by-step milestone breakdown, verification badge (`[M] Reviewed by our team` or `[A] Attribution`), sponsor disclosure, and SLA → progress through `Start Mission → Continue Mission → Submit for Review (with screenshot/evidence hash check) → Verification Pending → Claim Reward → Completed`.
  3. **YouTube Watch & Earn (J3)**: Browse video campaign cards → open **Video Details** → launch official YouTube intent/embed with persistent "Back to VaultGold" return path. Campaigns marked `discovery_only` clearly display a **"Discovery – No Coins"** badge; `verifiable` campaigns enforce server timer/code checks. **No Unity ad is ever triggered when opening or watching YouTube.**
  4. **Puzzle & Brain Games Hub (J4)**: Select from 10 admin-toggleable puzzle categories (*Memory Match, Sliding Tile, Number Puzzle, Word Scramble, Pattern Recognition, Logic Grid, Daily Brain Teaser, Timed Challenge, Jigsaw Grid, Difficulty Mastery*) → server/engine issues a signed single-use `attemptToken` & seed → play interactive 48dp+ accessible board → submit move log and elapsed duration → anti-cheat verifier validates move legality and minimum plausible solve time → awards base coins atomically → presents optional **"Watch Ad for +N Bonus Coins"** button (declining keeps base reward intact).
  5. **Reusable Task Engine & Daily Check-In (J5/Section 8)**: Unified task pipeline supporting 7-day streak check-ins (server-day boundary), daily/weekly missions, server-graded knowledge quizzes (answer keys isolated from public reads), milestones, promos, and achievements.
  6. **Watch Ad & Earn (J5 & Section 21)**: Displays live coin balance, `"1 Coin per Completed Ad"` disclosure, availability badge, daily cap progress (`viewsToday / dailyLimit`), and recent ad ledger items → tapping **Watch Ad** issues a single-use `adSession` → invokes Unity Ads SDK (`Game ID 800370914`, Test Mode indicator visible) → upon `COMPLETED` callback and session elapsed-time/idempotency check, credits 1 coin (or sandbox/test-flagged ledger entry when Test Mode is active).
  7. **Wallet, Immutable Ledger & Gift-Code Rewards Store (J6)**: Inspect `Available`, `Pending`, `Lifetime Earned`, and `Redeemed` balances alongside filterable append-only ledger details → browse **Rewards Store** where availability (`available | limited | out_of_stock | coming_soon`) is computed strictly from real unreserved inventory in `giftInventory` → confirm redemption → atomic transaction reserves 1 inventory code and moves coins from `available` to `pending` → once approved by Finance Admin, owner reveals their masked code in `redemptionSecrets` or opens a linked dispute ticket.
  8. **Community Redeem-Code Center, Referrals, Notifications & Support (J7 & J8)**: Browse verified/unverified/expired Free Fire & FF MAX community info cards with real expiry countdowns, region chips, one-tap **Copy Code**, external official redemption site launcher, and rate-limited **Report Incorrect Info** → share unique referral code with self-referral/device-cluster fraud checks → view categorized in-app notifications with quiet hours → submit & track Support/Dispute tickets.
  9. **20-Module Role-Based Admin Console (Section 18)**: Accessible from Profile/Header for authorized roles (`superAdmin` bootstrap toggle included for the project owner account in development, fully audited) covering Analytics Dashboard, User Management, Apps Install Handler, Campaign & Milestone Builder, YouTube Manager, Puzzle Config, Task & Quiz Bank Manager, Redeem-Code Manager, Reward Catalogue, Encrypted Gift-Code Inventory Import, Redemption Approval Queue, Ledger Adjustments, Referral Review, Notifications Composer, Support Tickets Triage, Fraud Flags & Freeze Controls, Campaign Calendar, System Feature Flags, Immutable Audit Logs, and RBAC Role Assignment + Advertising Management.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Luxury Obsidian & Vault Gold (`#0B0D12`)** — high-contrast dark gaming aesthetic inspired by tactical esports vaults, combining deep obsidian surfaces, brushed gunmetal cards, and luminous amber/gold metallic accents.
  - *Color Palette & Mood*:
    - Background Primary: `#0B0D12` (Obsidian Void)
    - Surface / Elevated Card: `#131722` (Gunmetal Slate) & `#1B2030` (Elevated Vault Container)
    - Primary Accent: `#F5B841` (Vault Gold) with `#FFD166` highlight gradients
    - Secondary Accent: `#38BDF8` (Cyber Cyan for `[V]` verification & telemetry badges)
    - Semantic Status Chips: Pending `#F59E0B` (Amber), Approved `#10B981` (Emerald), Rejected `#EF4444` (Crimson), Discovery/Unavailable `#64748B` (Muted Slate)
  - *Typography & Hierarchy*: Local bundled Google Fonts installed via `font-util`:
    - Display & Headlines: **Rajdhani** / **Space Grotesk** (bold, tactical geometric headers for coin balances, mission titles, and vault counters)
    - Body & UI Controls: **Plus Jakarta Sans** (minimum `16.sp` body copy, WCAG AA+ contrast)
    - Monospace Telemetry & Codes: **JetBrains Mono** (redeem codes, transaction IDs, countdown timers, and SHA/idempotency hashes)
  - *Component Styling & Layout*: Material Design 3 adaptive navigation (`NavigationBar` on compact phones, `NavigationRail` on expanded tablets/foldables), full `enableEdgeToEdge()` with safe drawing insets, layered hero wallet banner with radial gold glow, minimum `48.dp` interactive touch targets, and `Modifier.testTag(...)` on all primary controls.
- **Interactive Feedback & Motion**: Spring-animated coin counter increments, skeleton shimmer placeholders during Firestore loads, offline banner detection that blocks monetary mutations while keeping cached reads accessible, and instant clipboard toasts.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Provisioned AI Studio Firestore + Atomic Transactions for the Authoritative Economy**
  - *Chosen Approach*: Provision Firebase Auth (Google Sign-In) and a named Firestore database via AI Studio's `ProvisionFirebase` workflow, enforcing strict schema validation in `firestore.rules` and executing all wallet/ledger/idempotency mutations inside atomic Firestore `runTransaction` blocks.
  - *Why*: Guarantees `wallet.available == sum(approved ledger)`, prevents negative balances or double-spending under concurrent taps, and runs live against real Google infrastructure inside the AI Studio emulator without failing on missing external service-account keys.
  - *Alternatives Considered*: Connecting blindly to `atharva-7ef4b` with placeholder API keys (would fail initialization and violate Rule 1 & Rule 2). We also include a configurable export/migration schema mapper so data maps cleanly to the Section 22 paths.
- **Decision 2: Unity Ads SDK (`4.21.0`) in Safe Test Mode with Layered Session Validation**
  - *Chosen Approach*: Integrate `com.unity3d.ads:unity-ads:4.21.0` behind an `AdProvider` abstraction initialized with Game ID `800370914` and `testMode = true` (controlled by `/adConfig`), paired with single-use `adSessions` that verify minimum elapsed watch duration, daily user limits, and idempotency keys before minting coins (and marking test-mode rewards distinctly).
  - *Why*: Lets you test the real Unity Ads lifecycle (`initialize → load → show → COMPLETED / SKIPPED / FAILED`) immediately on device while preventing unverified or replayed callbacks from inflating production balances.
- **Decision 3: Honest Verification Badges (`[V]`, `[A]`, `[M]`, `[N]`) Across Missions & Videos**
  - *Chosen Approach*: Every App Mission, Milestone, Task, and YouTube campaign explicitly declares its verification mode. App installs without a live third-party MMP webhook require `[M] Manual Review` (user submits screenshot/package evidence hash, admin approves in Module 11/16), and YouTube videos without creator API verification run in `discovery_only` mode (`Discovery – No Coins`).
  - *Why*: Strictly complies with Google Play Developer Program policies, YouTube API Services terms, and Unity Ads policies against incentivized manipulation or fake verification promises.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                         VAULTGOLD REWARDS (ANDROID APP)                           │
│  ┌─────────────────────────────────────────────────────────────────────────────┐  │
│  │ PRESENTATION LAYER (Jetpack Compose + Material 3 + Adaptive Window Classes) │  │
│  │  ├─ Auth & Onboarding Gate (CredentialManager Google Sign-In / Guest Mode)  │  │
│  │  ├─ Home & Running Missions Hub (8 Sections + View All Search/Filter/Sort)  │  │
│  │  ├─ Apps Install Handler & Milestone Tracker ([M] Evidence / [A] Webhook)   │  │
│  │  ├─ Puzzle & Brain Games Arena (10 Categories + Signed Seed + Move Logger)  │  │
│  │  ├─ Watch Ad & Earn + Reusable Task Engine (Check-In, Quizzes, Referrals)   │  │
│  │  ├─ YouTube Discovery/Verified Hub & Free Fire Redeem-Code Info Center      │  │
│  │  ├─ Wallet, Immutable Ledger, Rewards Store & Support/Dispute Tickets       │  │
│  │  └─ 20-Module Role-Based Admin Console (Dashboard, Inventory, Fraud, RBAC)  │  │
│  └──────────────────────────────────────┬──────────────────────────────────────┘  │
│                                         │ StateFlow / UI Events                   │
│  ┌──────────────────────────────────────▼──────────────────────────────────────┐  │
│  │ DOMAIN & VERIFICATION LAYER (MVVM ViewModels + Anti-Cheat & Policy Guards)  │  │
│  │  ├─ Centralized Reward Engine (awardReward + Idempotency + Daily Caps)      │  │
│  │  ├─ Puzzle Anti-Cheat Validator (Seed Token + Move Simulation + Min Time)   │  │
│  │  ├─ AdSession State Machine & UnityAdProvider (Game ID 800370914 Test Mode) │  │
│  │  └─ Reconciliation & Fraud Detector (Velocity, Duplicate Evidence, Self-Ref)│  │
│  └──────────────────────────────────────┬──────────────────────────────────────┘  │
│                                         │ Coroutines / Atomic Transactions        │
│  ┌──────────────────────────────────────▼──────────────────────────────────────┐  │
│  │ DATA LAYER (Named FirebaseFirestore Instance + Hardened Security Rules)     │  │
│  │  ├─ /users/{uid} & /users/{uid}/wallets/main & /users/{uid}/ledger/{txId}   │  │
│  │  ├─ /idempotency/{key} & /users/{uid}/adSessions/{id} & /adStats/{yyyymmdd} │  │
│  │  ├─ /appCampaigns/{id}, /campaignMilestones/{id}, /users/{uid}/taskClaims   │  │
│  │  ├─ /videoCampaigns/{id}, /puzzleGames/{id}, /users/{uid}/puzzleAttempts    │  │
│  │  ├─ /rewards/{id}, /giftInventory/{id}, /users/{uid}/redemptions/{id}       │  │
│  │  └─ /redeemCodes/{id}, /referrals/{id}, /tickets/{id}, /adminAuditLogs/{id} │  │
│  └─────────────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - **User & Wallet (`users/{uid}`, `users/{uid}/wallet/summary`)**: Stores `displayName`, `email`, `photoUrl`, `referralCode`, `accountStatus` (`active | suspended | banned | pending_deletion`), `role` (`user | superAdmin | campaignManager | contentModerator | financeAdmin | support | analyst`), `ageVerified18Plus`, and wallet balances (`available`, `pending`, `lifetimeEarned`, `redeemed`, `updatedAt`).
  - **Append-Only Ledger & Idempotency (`users/{uid}/ledger/{txId}`, `idempotency/{key}`)**: Records `amount`, `type` (`earn | reserve | release | spend | reversal | adjustment`), `sourceType`, `sourceId`, `eventId`, `idempotencyKey`, `status` (`pending | approved | rejected | reversed`), `verificationLevel` (`server_verified | s2s_verified | client_attested | manual_review`), `actor`, `reason`, `balanceAfter`, and `createdAt`.
  - **Campaigns, Tasks, Videos & Puzzles (`appCampaigns`, `campaignMilestones`, `tasks`, `videoCampaigns`, `puzzleGames`)**: Config-driven documents with schedule windows (`startAt`, `endAt`), slot counters, verification requirements, and reward rules. Answer keys and admin-only notes live in isolated admin documents (`quizAdminKeys`, `appCampaignsAdmin`, `giftInventory`) protected by security rules.
  - **Claims, Redemptions & Audit Logs (`taskClaims`, `puzzleAttempts`, `adSessions`, `redemptions`, `redemptionSecrets`, `adminAuditLogs`)**: Per-user subcollections and root operational queues with strict state transitions and mandatory admin audit logging.

- **Interactive Component & State Mapping**:
  - **Centralized `awardReward` Transaction Pipeline**: Validates user auth & `active` status → checks campaign/task schedule, daily cap, and slot availability → atomically checks and writes `idempotency/{sourceType}_{eventId}_{uid}` → appends immutable `ledger` entry → increments `wallet.available` (or `wallet.pending` for held/manual claims) → logs velocity/fraud telemetry if thresholds are exceeded.
  - **Puzzle Anti-Cheat Verifier**: Generates a single-use `attemptToken` and board seed on game start; when the player completes a Memory Match, Sliding Tile, Number, Word, Pattern, Logic, Daily Teaser, Timed, or Jigsaw level, the verifier checks token expiry, move sequence validity, and minimum plausible completion time before calling `awardReward`.
  - **Unity Ads State Machine (`UnityAdProvider`)**: Wraps `UnityAds.initialize(context, "800370914", testMode, initListener)` and `UnityAds.load(placementId, loadListener)` / `UnityAds.show(activity, placementId, showOptions, showListener)`. Tapping **Watch Ad** creates an `adSession` (`issued`), transitions through `displayed → completed → verification_pending`, validates session duration and idempotency, and credits 1 coin.
  - **Gift-Code Redemption Reservation & Delivery**: `requestRedemption` runs a Firestore transaction verifying `wallet.available >= reward.pointsCost` and locating an `available` encrypted code in `giftInventory`, atomically marking the inventory item `reserved`, moving `pointsCost` from `available` to `pending`, and creating a `pending` redemption record. Finance Admin approval moves `pending` to `redeemed` and writes the unmasked code to `users/{uid}/redemptionSecrets/{redemptionId}` (readable only by that user); rejection releases the reserved code and restores `available` coins.
  - **Two-Tier Automated Verification**: Includes JavaScript Firestore rules unit tests (`firestore.test.js`) and Android Robolectric repository rule tests (`*RuleTest.kt`) against local Firebase Emulators verifying both permitted operations and unauthorized access denials before deploying rules.
