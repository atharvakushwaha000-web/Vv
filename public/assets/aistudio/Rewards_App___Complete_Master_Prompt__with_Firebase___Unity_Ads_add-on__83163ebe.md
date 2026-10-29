# MASTER PROMPT: Free Fire Community Rewards & Earning App (Firebase + Unity Ads)

*Paste everything below into your AI coding agent. Part A is the original product master prompt; Part B is the Firebase + Unity Ads add-on, appended without removing anything from Part A.*

---

## 0. ROLE, SCOPE AND GROUND RULES FOR THE IMPLEMENTING AGENT

You are a senior full-stack engineer, Firebase architect, Android/Unity-ads integrator, gamification designer and security reviewer. Build the application specified below, phase by phase (Section 17). Read the whole document before writing code.

**Non-negotiable rules**

1. Never claim a feature is complete unless it was run and tested. "Described" or "mocked" is not "done".
2. Never invent credentials, IDs or inventory: Firebase keys, app IDs, Unity placement IDs, gift codes, Free Fire codes, campaign data. If something is missing, stop that item and list it under **Missing configuration** (Section 0.3).
3. Every button has a real action. No dead-end pages, placeholder claims, fake inventory or simulated success messages in production paths.
4. Coins are created only by the trusted backend. The client never writes balances, ledger entries, verified claims, roles or reward amounts.
5. When updating an existing project: inspect first, preserve everything already working, migrate data rather than overwrite, and run regression checks (Section 16).
6. The app is **independent and unofficial**. No Garena, Free Fire, BOOYAH, Google, YouTube or Google Play branding or affiliation claims. Original design only.

### 0.1 Resolved contradictions and assumptions (labelled)

| # | Issue | Resolution |
| --- | --- | --- |
| A1 | Part A suggests Cloud Firestore; the owner's existing project `atharva-7ef4b` has a Realtime Database (RTDB) | **Use the existing RTDB as the primary database** (owner instruction: reuse existing project). Schema in Section 10 is RTDB-shaped (JSON tree + path rules). Firestore may be added later only for heavy querying/analytics; do not mix rule syntaxes. |
| A2 | Part A mentions single-file HTML prototype; Part B requires Unity Ads | Unity Ads has no supported plain-HTML/PWA SDK. **Assumed target: native Android app** (matches the Android Game ID). A web build (admin panel, and optionally a user web app) is supported but **without Unity ads**; ads module stays modular and disabled there. |
| A3 | "Server verification" of ads vs. client callbacks | Prefer Unity's server-to-server (S2S) reward callback if enabled for the project. If unavailable, use layered validation (Section 21) and document the residual trust limitation. Never call a client callback "cryptographically verified". |
| A4 | Part B says "inspect available files and prior ChatGPT context" | The implementing agent has no access to prior chats. The owner-provided values in Section 19 are the only trusted configuration. Anything else must be requested. |
| A5 | Gift-code redemption vs. 1 coin per ad | Coin value is an **economics decision for the owner**. Coin costs, rates and limits are admin-configurable; show reward only when real inventory/funding exists. |
| A6 | Child safety vs. Free Fire's young audience | Assumed **18+ for redemptions and ads**; users under 18 may be blocked from rewards or need verifiable parental consent under India's DPDP Act. Legal review required before launch. |

### 0.2 Verifiability legend (used throughout)

- **\[V\] Technically verifiable** by our own backend/SDK (e.g., ledger writes, daily limits, puzzle server validation, referral signup).
- **\[A\] Requires third-party authorization/attribution** (offerwall/MMP/Play-related install or in-app level events, Unity S2S callbacks).
- **\[M\] Manual review** by an admin with evidence rules.
- **\[N\] Not verifiable**: must be presented as non-rewarded (e.g., arbitrary YouTube watch time).

### 0.3 Missing configuration to report (do not guess)

1. Firebase `apiKey`, `messagingSenderId`, `appId` (placeholders in owner's config) from Firebase Console → Project settings → Your apps.
2. Confirmation that the Android app (package name) is registered in `atharva-7ef4b` and `google-services.json` is available.
3. Unity: confirmation that Game ID `800370914` belongs to a Unity project suitable for this app (see 19.2), the **rewarded placement ID(s)**, and whether S2S callbacks + secret are enabled.
4. Auth providers enabled (Google needs SHA-1/SHA-256 fingerprints registered).
5. Blaze plan (Cloud Functions require it), App Check provider setup, FCM setup.
6. Legal/business inputs: privacy policy URL, support email, gift-code supplier, budget, age policy.

---

# PART A: CORE PRODUCT

## 1. Product vision

A premium dark/gold, gaming-themed rewards platform where users earn coins through approved, policy-compliant activities: sponsored app missions, eligible video campaigns, puzzles, daily/weekly tasks, quizzes, referrals and optional rewarded ads. Coins can be redeemed for Google Play gift codes **only** when genuine inventory and budget exist. A Free Fire redeem-code information center provides curated, moderated community info. Trust, transparency and anti-fraud are product features, not afterthoughts.

## 2. Complete feature inventory (nothing omitted)

1. Onboarding, auth (Google + email), guest browsing, profile, account deletion.
2. **Apps Install Handler** (admin-controlled campaigns, milestone rewards, mission detail pages).
3. Running Missions & View All (8 sections with search/filter/sort/pagination).
4. **YouTube Watch & Earn** (eligible rewarded or non-rewarded discovery).
5. **Puzzle & Brain Games** (10 categories, difficulty, leaderboards, anti-cheat).
6. **Reusable Task Engine** (daily check-in, daily/weekly, quizzes, knowledge, puzzles, referrals, sponsored, promo, events, milestones, limited-time, announcements, achievements).
7. **Watch Ad & Earn** (Unity rewarded ads, 1 coin each, configurable limits) plus optional ad bonuses after tasks/puzzles.
8. Wallet & immutable transaction ledger.
9. Google Play gift-code Rewards Store with private inventory.
10. Free Fire / Free Fire MAX redeem-code information center.
11. Referral system with fraud checks.
12. Notifications (FCM + in-app).
13. Support tickets, reports, disputes.
14. Secure Admin Panel (20 modules, RBAC, audit logs) including Advertising Management.
15. Anti-fraud and review tooling.
16. Analytics/crash reporting (consent-based).

## 3. Detailed user journeys

**J1 New user:** Splash → onboarding (3 cards: earn, play, redeem; unofficial disclaimer) → guest home (browse only) → tap Earn/Claim → sign-in sheet (Google / email) → age and terms/privacy consent → profile created (server-side) with referral code, wallet = 0 → optional referral-code entry (once, within N days) → home.

**J2 App mission:** Home → Running Campaigns → card (icon, name, total coins) → Mission Details → Start Mission (creates a `claim` with step progress) → opens official Play Store URL (`https://play.google.com/store/apps/details?id=<package>`) → user installs/opens → returns → app shows step states. Steps verified via \[A\] attribution/offerwall callback or \[M\] evidence submission (admin review). Each milestone pays once through the reward engine. States: *Start → Continue → Verification Pending → Claim Reward → Reward Approved/Rejected*.

**J3 Video:** Videos tab → grid → card → Video Details → Watch Video (official YouTube player/app, not manipulated) → return path button. If campaign `rewardMode = verifiable` a supported verification path is used; otherwise item is labelled **"Discovery – no coins"**. **No Unity ad is ever triggered here.**

**J4 Puzzle:** Puzzles → category → level → play → on finish, client submits attempt (moves, timings, seed) → server validates → base coins (if enabled) → optional "Watch ad for +N bonus coins" prompt showing exact bonus. Declining the ad never removes the base reward.

**J5 Watch Ad & Earn:** Screen shows balance, reward (1 coin), availability, remaining views today → Watch Ad → Unity ad → completion → backend verifies → coin appears (status Pending → Approved). Failure/no-fill shows retry; no coins lost.

**J6 Redemption:** Rewards Store → denomination (available only if stock>0) → confirm dialog (cost, irreversibility, ETA) → backend atomically reserves coins → status Pending → admin/automated fulfilment → Approved/Delivered (code revealed only to owner in-app, masked in lists) or Rejected (coins released with reason) → support/dispute option.

**J7 Referral:** Share link/code → friend signs up and completes qualifying action (configurable, e.g., verified account + first task) → server checks fraud rules → reward both/either → status visible in Referral History.

**J8 Redeem-code center:** View verified/unverified/expired cards → Copy code → link to the official Free Fire redemption site (open externally) → Report incorrect info. Never ask for game passwords/OTPs.

**J9 Account deletion:** Profile → Delete Account → re-auth → confirmation → backend job removes/anonymizes personal data, keeps minimal anonymized ledger as legally required, cancels pending redemptions (coins forfeited/refunded per policy shown beforehand).

## 4. Apps Install Handler specification

**Campaign fields (admin editable, no code changes):** appName, iconUrl, playStoreUrl (validated to `play.google.com`), packageName (regex-validated, unique per active campaign), shortDescription, fullInstructions, bannerUrl, totalReward (computed = sum of milestones, shown as "up to"), startAt/endAt, slots (campaignLimit), dailyLimit, eligibility (countries, min Android version, new-users-only, device), estimatedMinutes, status (`draft|scheduled|live|paused|archived|removed`), labels (featured/trending/new/popular), rules and disqualification conditions, verificationMethod (`attribution|offerwall|manual|none`), sponsorDisclosure text.

**Milestones (`campaignMilestones`):** sequence, title, instructions, completionCondition (or requiredLevel), rewardCoins (can be 0), verification (`attribution|manual|self_confirm_no_reward`), evidenceRequired (bool), approvalSLA text, status. Example only: Step 1 open listing (0 coins), Step 2 install (20), Step 6 verified milestone (100). All editable.

**Mission Details page:** icon, name, official store link button, "up to X coins", description, numbered steps with per-step reward and progress indicator, level/milestone breakdown, requirements, ETA, verification method (plainly stated), approval time, terms/restrictions, sponsor disclosure, support link; CTA state machine: `Start Mission → Continue Mission → Submit for Review → Verification Pending → Claim Reward → Completed`.

**Honesty rule:** install/level detection is \[A\] or \[M\]. If no attribution partner is integrated, the campaign must be set to `manual` and the UI must say "Reviewed by our team". Reject fake installs, fabricated/duplicated screenshots (store evidence hash; reject duplicates), repeat claims, forced ratings/reviews, and any incentivized-rating design.

**Anti-abuse:** one claim per (uid, campaign); one per device ID/ install ID where permitted; milestone idempotency key `appmission:{campaignId}:{milestoneId}:{uid}`; slot counter decremented transactionally; rewards frozen if campaign paused mid-way (grandfathering rule configurable).

## 5. Running missions and View All

Sections: Running Campaigns, Recommended Apps, New Missions, Trending, High-Reward, Nearly Completed, Completed, Available Rewards. Each has **View All** → list screen with: search, category chips, sort (reward, newest, ETA), status filter, cursor-based pagination (`orderByKey().startAt().limitToFirst(20)` or indexed query with `limitToLast`), skeleton loading, empty state, error + retry, offline banner, duplicate-campaign guard (dedupe by `packageName` and `campaignId`), responsive layout. Recommendations are computed from server-side rules (country, completed set, popularity) and cached in `/feeds/*` by a scheduled function.

## 6. YouTube Watch & Earn specification

Fields: youtubeVideoId (validated 11-char), canonicalUrl (derived), thumbnail (`i.ytimg.com`), title, channelName, description, rewardMode (`discovery_only | verifiable`), rewardCoins, minWatchSeconds (only if verifiable), schedule, dailyLimit, totalLimit, region eligibility, status, moderation/report counters.

User states: Not Started, In Progress, Watched, Verification Pending, Reward Approved, Reward Unavailable. **Policy position:** opening a link or starting an embedded player proves nothing \[N\]. Rewards allowed only if (a) campaign owner/creator authorization exists, (b) the verification method complies with YouTube API Services Terms and incentivized-engagement policies, and (c) no engagement manipulation (likes, subs, comments, views) is requested. Otherwise `discovery_only` (no coins). Use official embed/IFrame player or open the YouTube app; no scraping, no background playback, no auto-play loops. A "Back to app" path is always visible. Admin controls: visibility, schedule, limits, region, report review.

## 7. Puzzle and brain games specification

Categories (admin-toggleable): jigsaw, sliding tile, memory match, number puzzles, word puzzles, pattern recognition, logic, daily brain teaser, timed challenge, difficulty levels. Each game: instructions, difficulty tiers, level progression, score, completion status, daily play limit, optional coin reward, retry/continue, history, leaderboard (optional, opt-in display name), accessible touch targets (≥48dp), offline play for practice (no coins offline; reward claims sync when online).

**Anti-cheat \[V\]:** the server issues a signed puzzle `seed`/`attemptToken` (expires, single-use). Client submits the move log + durations. Function re-simulates or sanity-checks (min plausible time per level, move count validity, solution correctness against seed). Reject impossible times, replayed tokens, over-limit attempts. Rewards computed by server from `puzzleGames/{id}/rewardRules` only. Daily reward cap per user and per game; velocity checks; flag outliers to `fraudFlags`.

## 8. Task engine (reusable)

One `tasks` definition model: `type`, `title`, `rules` (eligibility, cooldown, repeat policy), `reward` (fixed or tiered), `startAt/endAt`, `dailyLimit`, `totalLimit`, `verification` (`server_event | attribution | manual | quiz_answer_key | puzzle_attempt | ad_event`), `status`. One `claims` pipeline for all. Types: daily check-in (streak with server-time day boundary in IST or configurable TZ), daily/weekly missions, quiz and knowledge tasks (answers verified server-side; answer key never in public path), puzzle completion, referral tasks, sponsored offers, promo/event/limited-time, level milestones, announcements (read-only, optional read-reward), achievements (computed server-side). New task types must be addable by config + a verifier plug-in, not new bespoke screens.

## 9. Wallet and ledger

Wallet (`/wallets/{uid}`, server-write only): `available`, `pending`, `lifetimeEarned`, `redeemed`, `updatedAt`. Ledger entries (`/ledger/{uid}/{txId}`, append-only): amount, type (`earn|reserve|release|spend|reversal|adjustment`), sourceType, sourceId, eventId, idempotencyKey, status (`pending|approved|rejected|reversed`), reason, actor (`system|adminUid`), balanceAfter, createdAt. Global idempotency index `/idempotency/{key}` created with a transaction (`set` only if absent). Wallet changes use RTDB `transaction()` inside Cloud Functions, so balance can never go negative. Admin adjustments require reason, second-admin approval above a configurable threshold, and write an audit log. Reconciliation job nightly: `sum(ledger) == wallet`; mismatches raise alerts and freeze redemptions for that user.

## 10. Rewards store (Google Play gift codes)

Catalogue (`/rewards`, public-safe): name, denomination (₹), pointsCost, availability (`available|limited|out_of_stock|coming_soon`), regionRules, minAccountAgeDays, minLifetimeEarned, status. **Private inventory** (`/giftInventory/{denom}/{codeId}`: encryptedCode, status `available|reserved|delivered|void`, supplierRef, purchasedAt) readable only by server/authorized finance admins; codes encrypted at rest with a KMS-managed key (Cloud KMS/Secret Manager); never logged. Public `availability` is derived by a function from real stock (never hand-set to "available" without stock). Flow (callable `requestRedemption`): auth → eligibility → transaction: reserve one inventory code + move coins available→pending → create `/redemptions/{uid}/{id}` (`pending`). Admin `approveRedemption` → delivered (code written to `/redemptionSecrets/{uid}/{id}` readable only by owner) or `rejectRedemption(reason)` → release coins, unreserve code. Handle exhausted stock, double-tap (idempotency key), concurrent redemptions, insufficient balance, user suspended, and expired reservations (scheduled function releases after N hours). Disputes via support tickets linked to redemptionId. Follow Google Play gift-code resale/distribution rules and regional law; source codes only through authorized channels.

## 11. Free Fire redeem-code information center

Admin-moderated `redeemCodes`: code, title, rewardDescription, regions, game (`FF|FF_MAX`), expiryAt, sourceReference (required), status (`verified|unverified|expired|hidden`), reports count. User UI: code cards, **Copy** (clipboard + toast), expiry countdown (real, never fake urgency), region chips, link to the official Free Fire redemption site (external browser), news/announcements, "Report incorrect info" (rate-limited). Auto-expire via scheduled function. Persistent disclaimer: unofficial, no guarantee of rewards, never share password/OTP. Never fabricate codes or promise diamonds.

## 12. Referral system

Unique code/link (`/referralCodes/{code} → uid`, server-generated, collision-checked). Referral record states: `signed_up → qualified → rewarded | rejected | under_review`. Qualification (configurable): verified email or Google account, first valid task, N days active. Fraud checks: self-referral (same uid/device/IP-cluster/email-alias patterns), duplicate devices, burst signups, disposable emails, per-referrer cap, reward delayed by cooldown. Admin review queue with approve/reject and audit log.

## 13. User account and profile

Google Sign-In, email+password with email verification and password reset, session persistence, account linking (merge guest/email with Google where supported), avatar (Storage upload with size/type limits, or preset avatars), history tabs (missions, videos, puzzles, ads, redemptions, ledger), notification and privacy settings, help/support, terms/privacy, account deletion (Section 3, J9), accountStatus (`active|suspended|banned|pending_deletion`). Guests: browse-only; any earn/redeem prompts sign-in.

## 14. Notifications

FCM topics/tokens stored under `/fcmTokens/{uid}`; in-app inbox `/notifications/{uid}`. Types: new missions, mission/claim updates, reward approvals, redemption status, new videos/puzzles, genuinely expiring campaigns, announcements. Opt-in per category, quiet hours, max N pushes/day, no deceptive countdowns or misleading "you won" copy. Android 13+ notification permission requested in context.

## 15. Design and UX

Dark background (#0B0D12-ish), amber/gold accents, clear typography (min 16sp body, contrast ≥ WCAG AA), card-based layout, progress bars/rings, status chips (Pending=amber, Approved=green, Rejected=red, Unavailable=grey), lightweight transitions (≤250ms, respect reduce-motion), mobile-first, TalkBack labels, empty/loading/error/offline states on every list. Original assets only; include the "Unofficial app" notice in onboarding, About and the Redeem-code center.

**Screens (all required):** Splash, Onboarding, Auth, Home, App Missions list, Mission Details, View All, Videos, Video Details, Puzzles hub, Puzzle play/result, Task categories, Daily check-in, Watch Ad & Earn, Wallet, Ledger detail, Rewards Store, Redemption confirm/history/detail, Redeem-code center, Referral, Profile, Settings/Privacy, Notifications, Support/Tickets, Terms/Privacy, Admin (all modules in Section 18).

## 16. Testing, acceptance criteria and regression protocol

*Cross-reference key: in Section 0.1 the phrase "Section 10" for the schema means **Section 22**; "Section 16" = this section; "Section 17" = phased plan; "Section 18" = Admin Panel; "Section 19" = owner configuration; "Section 21" = ad validation.*

Every item needs an automated test where possible (Firebase Emulator Suite for Auth, RTDB, Functions and rules unit tests) plus a manual device test. Report results as pass/fail with evidence.

**Core flows:** new-user registration; login, logout, password recovery; Google sign-in; app-install campaign creation; app icon + Play Store link rendering; opening campaign details; completing individual milestones; rejecting unverified claims; adding YouTube campaigns; ineligible/unavailable video rewards shown as discovery; completing puzzle challenges; coins awarded **exactly once**; duplicate claim prevention; gift-code redemption; exhausted inventory; insufficient balance; unauthorized admin action denied; network failure/offline recovery; accurate task and redemption statuses.

**Ad and backend (Part B):** Firebase initialization; authentication success; unauthorized access denial; rules tests (allowed + denied matrix, Section 23); admin-only config access; Unity SDK initialization; test-ad load; ad unavailable; ad failure; completed rewarded ad; duplicate reward-event rejection; correct 1-coin processing; balance consistency (`wallet == sum(ledger)`); daily-limit enforcement; puzzle base rewards unaffected by declining an ad; no ad triggered by YouTube/Play Store/install/wallet navigation; one ad per tap (no loops).

**Concurrency/race tests:** 20 simultaneous redemption requests against 1 code; replaying the same ad event 50 times; double-tapped Claim; two devices claiming the same milestone.

**Definition of done:** all above pass; no dead buttons (automated UI crawl); no secrets in the APK/web bundle (secret scan); rules emulator suite green; release build tested on at least 3 Android versions/devices; accessibility pass (TalkBack, 200% font scale).

**Preserving existing functionality when updating a project:** (1) inspect the repo, config and current Firebase data before changing anything; (2) list existing features and write smoke tests for them first; (3) make additive, backward-compatible changes; data migrations are scripted, reversible and dry-run first; (4) never overwrite existing rules/functions without diffing and merging; (5) keep feature flags (`/settings/features`) so new modules can be disabled without redeploy; (6) re-run the full regression suite after each phase; (7) report any existing behaviour that was intentionally changed and why.

## 17. Phased implementation plan

| Phase | Deliverables | Exit criteria |
| --- | --- | --- |
| 0. Discovery | Inspect repo/Firebase; fill Section 0.3 list; confirm Unity project fit; architecture decision record | Missing-config list sent to owner |
| 1. Foundation | Firebase init, Auth (Google/email), user profile function, App Check, base rules, emulator tests, design system | Rules tests green, login works on device |
| 2. Wallet & reward engine | Wallet, ledger, idempotency index, `awardReward` core, admin adjustment, reconciliation | Exactly-once and race tests pass |
| 3. Task engine + daily check-in + referrals | Config-driven tasks, claims pipeline, referral flow, fraud flags | Duplicate/self-referral tests pass |
| 4. Apps Install Handler | Admin campaign editor, user list/details, milestones, manual review queue, attribution adapter (if partner available) | Unverified claim rejected, milestone paid once |
| 5. Puzzles | 10 game types (priority: memory, sliding, number, word first), server validation, leaderboards | Anti-cheat tests pass |
| 6. Unity Ads + Watch Ad & Earn | SDK init (test mode), rewarded flow, S2S/validation, limits, admin ad module | Test-ad flow passes; no ad on forbidden triggers |
| 7. Videos | Admin video manager, discovery/rewarded modes, reports | Ineligible shows discovery state |
| 8. Rewards store | Catalogue, encrypted inventory, redemption workflow, disputes | Stock/race tests pass |
| 9. Redeem-code center, notifications, support | FCM, inbox, tickets, moderation | End-to-end pass |
| 10. Hardening & launch | Fraud dashboards, load test, privacy/legal review, Play Console data-safety form, staged rollout (internal → closed → production) | Launch checklist signed |

## 18. Admin Panel: modules and workflows

**Delivery:** separate web app (own hosting target, e.g. `admin` site in the same Firebase project) with its own auth; admin access via **custom claims** set only by a server function callable by a `superAdmin`; enforce MFA for all admin accounts; session timeout; IP/anomaly alerts. **Roles:** `superAdmin`, `campaignManager`, `contentModerator`, `financeAdmin` (inventory/redemptions), `support`, `analyst` (read-only). Permissions are a role→capability map in `/settings/roles` enforced in functions *and* rules. Admin actions run via callable functions (never direct client writes to authoritative data), each requiring `reason` where it changes money/status and writing `adminAuditLogs`.

**The 20 modules:**

1. **Dashboard/analytics:** DAU/MAU, coins issued vs redeemed, ad completions and rejection rate, campaign funnel, fraud flags, stock levels, budget burn.
2. **User management:** search, view wallet/ledger/history, suspend/ban/restore, force sign-out, deletion queue.
3. **Apps Install Handler:** list/filter campaigns, status changes, slot usage, duplicate-package warnings.
4. **App campaign editor:** all fields in Section 4, milestone builder (drag reorder), preview-as-user, schedule, clone, archive; validation (Play URL host, package regex, icon size/type).
5. **YouTube manager:** add by URL/ID (auto-derive canonical URL/thumbnail), set rewardMode, schedule, limits, region, hide/report review.
6. **Puzzle/game config:** enable/disable, difficulty, reward rules, daily limits, daily challenge schedule, leaderboard toggle.
7. **Task and mission manager:** CRUD for task definitions, verifier selection, quiz question bank (answer keys private).
8. **Free Fire code manager:** add/edit/expire/hide codes, source reference required, user report queue.
9. **Reward catalogue:** denominations, coin cost, availability rules, region rules.
10. **Gift-code inventory:** bulk import (encrypted at ingest), void/reserve views, supplier reference, low-stock alerts; codes always masked in UI except for explicit audited reveal.
11. **Redemption requests:** queue with approve/reject(reason)/deliver, SLA timers, dispute link.
12. **Coin ledger and adjustments:** search ledger, create adjustment/reversal (reason + dual approval above threshold).
13. **Referral management:** review queue, caps, fraud signals, manual approve/reject.
14. **Notifications/announcements:** compose, audience targeting, schedule, throttle, preview.
15. **Reports and support tickets:** triage, reply, link to user/redemption, status.
16. **Fraud detection and review:** flags by rule (velocity, device clusters, ad-event anomalies, duplicate evidence hashes), freeze/unfreeze rewards.
17. **Campaign scheduling:** calendar view of live/scheduled/expiring campaigns across modules.
18. **System settings:** feature flags, limits, maintenance mode, minimum app version, test/production ad mode.
19. **Audit logs:** immutable, filterable, exportable; no edit/delete path.
20. **Role and permission management:** assign/revoke roles (superAdmin only), view effective permissions.

**Advertising Management module (Part B, Section 13 requirements):** configure rewarded placements, reward per ad (default 1 coin), per-user daily ad cap and daily ad-coin cap, enable/disable per placement, optional puzzle/task bonus rules (exact bonus amounts shown to users), test vs production mode indicator, placement descriptions, delivery/error report (load rate, no-fill rate, completion rate, rejected events). No Unity dashboard credentials stored in the client; changes audited.

---

# PART B: FIREBASE BACKEND + UNITY ADS + REWARDED COIN ECONOMY (ADD-ON)

*Appended to Part A. Nothing in Part A is removed or simplified.*

## 19. Owner-provided configuration and discovery

### 19.1 Firebase (existing project: do not create a new one without the owner's explicit approval)

| Key | Value | Status |
| --- | --- | --- |
| projectId | `atharva-7ef4b` | Provided |
| authDomain | `atharva-7ef4b.firebaseapp.com` | Provided |
| databaseURL | `https://atharva-7ef4b-default-rtdb.firebaseio.com` | Provided |
| storageBucket | `atharva-7ef4b.firebasestorage.app` | Provided |
| apiKey | placeholder | **MISSING: obtain from Firebase Console; do not invent** |
| messagingSenderId | placeholder | **MISSING** |
| appId | placeholder | **MISSING** (Android app needs its own app ID via `google-services.json`; web admin needs a web app ID) |

**Discovery steps before connecting anything:** inspect repo/config files for `google-services.json`, `firebase.json`, `.firebaserc`, existing rules, functions, hosting targets; inspect the live project (Auth providers enabled, RTDB tree, Storage rules, deployed functions, App Check status). If more than one config is found, identify the correct app and ask the owner. Reuse existing resources where compatible; **snapshot/export the RTDB and rules before any change**. The Firebase web/Android API key is not a secret but is not a security control either; protection comes from Auth, rules and App Check. **Never** place service-account JSON, Admin SDK credentials or the Unity S2S secret in client code; store them in Secret Manager / Functions config.

**Services and purpose:** Authentication (identity); Realtime Database (primary data, per A1); Cloud Functions 2nd gen (all authoritative logic; requires Blaze plan); Security Rules (RTDB + Storage); App Check (Play Integrity on Android, reCAPTCHA Enterprise on web admin) enforced on RTDB, Storage and callable functions; FCM (push); Storage (icons, banners, avatars, evidence images with size/type limits and per-user paths); Analytics/Crashlytics (only with consent, no PII in events); Secret Manager/KMS (gift codes, S2S secret). Do not enable anything without documenting its purpose, cost and permissions.

### 19.2 Unity Ads

- Provided: Android Game ID `800370914`, previously associated by the owner with the *MindFlow Brain Puzzle* project.
- **Verify before use:** Unity Ads Game IDs are per project/platform. Reusing the MindFlow ID in a different app mixes revenue, reporting and policy standing, and Unity may require each distinct app to have its own project/Game ID. Ask the owner to confirm in the Unity dashboard either (a) this Rewards App is registered under that project, or (b) create a new project/Game ID for it. Until confirmed, use the ID **only in test mode**.
- A Game ID is **not** a placement ID or API key. The rewarded **placement ID(s) must be read from the dashboard** (do not assume defaults such as `Rewarded_Android`; confirm). Store IDs in a build-config file (`BuildConfig`/remote config), not scattered in code.
- Required from owner: placement IDs, test-mode toggle status, S2S callback availability and secret, COPPA/child-directed and privacy settings for the project.

## 20. Authentication (complete)

Google Sign-In (register SHA-1/SHA-256 in Firebase), email/password with email verification, account creation, login/logout, password reset, persistent sessions, optional anonymous guest (browse-only; converted via account linking, never given a wallet), provider linking (Google ↔ email) with conflict handling, account deletion with recent-login requirement, mapped error handling (wrong password, user-not-found, network, too-many-requests, account-disabled, credential-already-in-use). A **function trigger on user creation** creates `/users/{uid}`, `/wallets/{uid}` (zeros), the referral code and default settings: the client cannot create wallets. Every callable function verifies `context.auth`, App Check token and account status; **UIDs are never taken from request bodies**. Admin roles come only from custom claims (set by `setAdminRole`, superAdmin-only, audited); `users/{uid}` fields `accountStatus`, `role`, balances are not client-writable. Admin sign-in is on the separate admin app with MFA.

## 21. Unity Ads integration and Watch Ad & Earn

**Platform decision:** native Android (Kotlin/Java) with the current Unity Ads / Unity LevelPlay Android SDK (select and pin the current supported version after checking the official docs; confirm Gradle/repository setup). If the app is built in Unity, use the Unity Advertisement SDK instead. **Web/PWA/HTML: no Unity Ads**; keep an `AdProvider` interface with `UnityAdProvider`, `NoopAdProvider` (web) so another approved network can be plugged in; the Watch Ad screen on web shows "Ads available in the Android app".

**Initialization sequence:** after consent/age gate and Firebase Auth → fetch ad config (`/adConfig`, public-safe) → `initialize(gameId, testMode, listener)` once per process → on success, `load(placementId)` → state `ready`. Handle lifecycle (pause/resume), network loss (retry with backoff, max attempts), and re-load after each show. Test mode comes from remote config *and* build type; production builds never ship with test mode forced on, and test-mode events never mint real coins (flagged `testMode:true`, ledger-excluded or credited to a sandbox balance).

**State machine (explicit, one source of truth):** `unavailable → loading → ready → displayed → completed → verification_pending → approved | rejected`, plus `failed`. UI maps each to text, enabled/disabled button and retry options.

**Business rule:** one successfully completed eligible rewarded ad = **1 coin** (server-configured `adConfig.rewardPerAd`, never client-supplied). Coins are NOT awarded for: tapping Watch Ad, load start, ad open, early close/skip, fabricated callbacks, replayed events, or test-mode events in production.

**Reward verification flow:**

1. Before showing: client calls `requestAdSession(placementId)`; server checks auth, status, daily limit and creates a single-use `adSession` (`sessionId`, uid, placement, expiresAt, status=`issued`). The `sessionId` (or hashed uid + session) is passed to Unity as the S2S `ObjectID/userId` custom parameter if supported.
2. SDK completion callback (`COMPLETED`, not `SKIPPED`) → client calls `claimAdReward(sessionId)`; status shows `verification_pending`.
3. **Preferred:** Unity S2S callback hits an HTTPS function that validates the signature (HMAC with the secret), maps it to the session, rejects duplicates by `eventId`/`sessionId`, and marks the session `verified`. `claimAdReward` then credits via the reward engine only if `verified`.
4. **Fallback if S2S unavailable:** award only if session valid + not expired + minimum elapsed time (≥ plausible ad length) + App Check passed + daily caps OK + device/account not flagged; mark transactions `verificationLevel: client_attested` and **document the residual risk** (a rooted/hooked client could fake the callback). Lower caps and heavier monitoring apply.
5. Duplicate/replay → rejected with `duplicate_event`; no ledger entry.

**Watch Ad UI:** balance, "1 coin per completed ad" disclosure before the button, availability badge, daily limit and remaining views, Watch Ad button (disabled with reason if unavailable), live status, error/retry copy, recent ad transactions. No-fill = friendly message and "Try again" (with cooldown), never a deducted coin.

**Optional placements (user-initiated only):** Watch Ad & Earn; an explicitly offered bonus after a validated puzzle ("Watch an ad for +N coins": exact N shown, base reward already credited); optional bonus after a validated task; clearly disclosed reward-related screens. **Forbidden triggers (enforce in code review and UI tests):** ordinary navigation, opening/watching YouTube, opening Play Store, installing apps, typing in forms, reading instructions, opening wallet or redemption history, profile navigation. One ad per tap; no auto-chained ads; no ad loop. Interstitials: default **off**; if enabled, strict frequency cap (e.g., configurable, none adjacent to incentivized actions), never on app launch/exit abuse, and per Unity policy. YouTube campaigns (Section 6) and App Install flows (Section 4) stay completely independent of ad logic.

## 22. Database schema (Realtime Database tree)

Owner-listed collections are preserved and mapped to RTDB paths. **R** = who can read, **W** = who can write.

| Path | Key fields | R | W |
| --- | --- | --- | --- |
| `/users/{uid}` | displayName, email, photoURL, referralCode, accountStatus, createdAt, lastLoginAt, ageBracket, country, settings | owner, admin | owner limited fields (displayName, photoURL, settings); rest server |
| `/wallets/{uid}` | available (pointsBalance), pending, totalEarned, totalRedeemed, updatedAt | owner, admin | **server only** |
| `/ledger/{uid}/{txId}` | = rewardTransactions: transactionId, uid, eventId, sourceType, sourceId, amount, transactionType, verificationStatus, createdAt, idempotencyKey, balanceAfter | owner, admin | **server only, append-only** |
| `/idempotency/{key}` | txId, createdAt | none | server only |
| `/appCampaigns/{id}` (public-safe) | campaignId, appName, packageName, playStoreUrl, iconUrl, bannerUrl, shortDescription, fullInstructions, totalReward, status, startAt, endAt, eligibilityRules(public part), verificationMethod, dailyLimit, campaignLimit, labels | signed-in (or guest for live only) | campaignManager via function |
| `/appCampaignsAdmin/{id}` | internal notes, partner IDs, caps, disqualification internals | admin | campaignManager |
| `/campaignMilestones/{campaignId}/{milestoneId}` | milestoneId, campaignId, title, instructions, requiredLevel/completionCondition, rewardCoins, sequence, verificationRequirement | signed-in | campaignManager |
| `/taskClaims/{uid}/{claimId}` (= taskClaims) | claimId, uid, taskType, taskId, campaignId, progress, verificationStatus, submittedAt, verifiedAt, rejectionReason, evidenceRef | owner, admin | owner may create `submitted` claims with validated shape; `verificationStatus` and later fields **server only** |
| `/tasks/{taskId}` | task definition (Section 8) | signed-in (public fields) | admin |
| `/videoCampaigns/{id}` | videoCampaignId, youtubeVideoId, canonicalUrl, thumbnailUrl, title, channelName, description, rewardCoins, eligibleRewardMode, campaignStatus, startAt, endAt, eligibilityRules | signed-in | admin |
| `/puzzleGames/{gameId}` | gameId, title, category, difficulty, configuration, status, rewardRules, dailyLimit | signed-in (answer data excluded) | admin |
| `/puzzleAttempts/{uid}/{attemptId}` | attemptId, uid, gameId, score, completionStatus, serverVerified, rewardTransactionId, createdAt | owner, admin | server only |
| `/adConfig` | rewardPerAd, placements (public descriptions), dailyLimit, mode flags | signed-in | admin via function |
| `/adSessions/{uid}/{sessionId}` | placement, status, issuedAt, expiresAt, verifiedAt, testMode | owner (read) | server only |
| `/adStats/{uid}/{yyyymmdd}` | count, coins | owner read | server only |
| `/rewards/{rewardId}` | rewardId, rewardName, denomination, pointsCost, availability, redemptionRules, status (stock count hidden or coarse) | signed-in | financeAdmin via function |
| `/giftInventory/**`, `/redemptionSecrets/**` | encrypted codes | **none / server (owner reads own delivered code)** | server only |
| `/redemptions/{uid}/{redemptionId}` | redemptionId, uid, rewardId, pointsReserved, status, createdAt, processedAt, rejectionReason | owner, finance/admin | **created by `requestRedemption` function only** |
| `/redeemCodes/{codeId}` (public, approved) | codeId, title, description, region, expiryAt, sourceReference, verificationStatus, publicDisplayStatus, code text | signed-in/guest | moderator |
| `/redeemCodesAdmin/**` | drafts, unreleased/reserved codes | admin | moderator |
| `/referrals/{id}` | referralId, referrerUid, referredUid, eligibilityStatus, rewardStatus, createdAt | involved users (own side), admin | server only |
| `/adminAuditLogs/{logId}` | logId, adminUid, action, targetType, targetId, reason, createdAt, before/after hashes | superAdmin/analyst | **server only, append-only** |
| `/notifications/{uid}`, `/fcmTokens/{uid}`, `/tickets/{id}`, `/fraudFlags/**`, `/settings/**`, `/feeds/**` | as described in Part A | scoped | scoped |

RTDB design notes: denormalize for read paths; index with `.indexOn` (status, startAt, createdAt); avoid deep nesting in lists; paginate with `limitToFirst/Last`; store timestamps as server time; keep payloads small.

## 23. Security rules model (RTDB)

Start from **deny-all** (`.read:false, .write:false` at root). Never use open rules. Rules must implement and **test** (via emulator unit tests, one allowed and one denied test per row):

| Requirement | Mechanism |
| --- | --- |
| Users read only own profile/wallet/ledger/claims | `auth.uid === $uid` |
| Clients cannot write wallets, ledger, verified claims, roles, redemptions' status | `.write:false` on those paths; only Admin SDK (functions) writes |
| No self-promotion to admin | roles only from `auth.token` custom claims; profile validate rejects `role`/`accountStatus` changes |
| Admin collections admin-only | `auth.token.admin === true` plus role check |
| Gift inventory/secrets protected | no client read except owner of own delivered code |
| Campaign reward values immutable by users | campaign paths writable only by admin claim via function (rules deny direct writes) |
| Public campaign reads limited to public fields | public-safe nodes separated from `*Admin` nodes (RTDB cannot hide individual fields) |
| Shape validation | `.validate` for types, string lengths, enums, timestamps `<= now`, required children, no unknown children (`$other: .validate false`) |
| Claim creation by user | only `submitted` status, own uid, existing campaign/milestone, size-limited evidence reference |
| Storage rules | per-user paths, MIME + size limits, admin-only for icons/banners |
| App Check | enforced on RTDB, Storage, Functions |

Deliverables: the rules file, a human explanation per path, and an emulator test report showing permitted **and** denied cases (e.g., user A reading user B's wallet = denied; client writing `/wallets/uid/available` = denied; non-admin writing `/rewards` = denied; guest reading ledger = denied).

## 24. Centralized reward engine and Cloud Functions

One internal module `awardReward({uid, sourceType, sourceId, eventId, ...})` used by every source (ads, puzzles, tasks, quizzes, app milestones, sponsored, videos, referrals, promos). Steps: (1) authenticate; (2) validate source event; (3) campaign/task eligibility (status, schedule, region, slots); (4) **amount from server config only**; (5) daily + total + per-campaign limits; (6) idempotency check via `/idempotency/{sourceType:eventId}` using a transaction; (7) write ledger entry once; (8) update wallet using `transaction()` (no negatives); (9) return `{status, amount, balance, reason}`; (10) audit trail + fraud signals.

**Function inventory:** `onUserCreate`, `onUserDelete`, `setAdminRole`, `requestAdSession`, `claimAdReward`, `unityS2SCallback` (HTTPS), `submitMilestone`, `reviewClaim` (admin), `attributionWebhook` (HTTPS, signed), `submitPuzzleAttempt`, `startPuzzleAttempt`, `claimTask`, `dailyCheckIn`, `submitQuiz`, `redeemReferral`, `requestRedemption`, `approveRedemption`, `rejectRedemption`, `releaseExpiredReservations` (scheduled), `adjustCoins` (admin), `reconcileLedger` (scheduled), `expireCampaigns` (scheduled), `expireRedeemCodes` (scheduled), `buildFeeds` (scheduled), `sendNotification`, `reportContent`, `deleteAccountJob`, `inventoryImport` (admin), `getAdminDashboardStats`. All validate input schemas, check App Check, rate limit, log structured errors without PII/secrets, and are idempotent on retry.

## 25. Daily limits and anti-fraud

Configurable (no universal limits invented; defaults chosen by the owner and tuned to Unity policy and budget): max rewarded ads/user/day, max ad-coins/user/day, per-campaign and per-task caps, per-IP/device caps, cooldown between ads, account-age gates before redemption. Detection: duplicate-event hashes, velocity anomalies, device/emulator signals (Play Integrity verdicts), multiple accounts per device, referral rings, repeated evidence hashes, abnormal ad-completion timings. Actions: soft flag → hold rewards in `pending` → admin review → reversal ledger entries (never delete). **Do not punish legitimate users for ad load failures**: failures are neutral events. Provide appeals through support tickets.

## 26. Error handling and edge cases

Offline: cached read-only views, queued non-monetary actions, monetary actions blocked with clear message. Function timeouts: client polls claim status, idempotency prevents double pay. Expired campaign mid-mission: honor already-verified steps per campaign policy. Slot exhausted, campaign paused, duplicate package, clock skew (use server time), user suspended mid-redemption, partial failures (compensating ledger entries), inventory shortage after reservation, S2S callback arriving before/after client claim (either order must converge), S2S outage (claims stay pending, retried), app killed during ad (session expires; no coin), user changes device mid-session, deleted account with pending items, localisation/RTL, time zone day boundaries for daily limits, rooted devices (reduced limits), low-memory devices.

## 27. Security, privacy and policy compliance

Cover and document: authentication/authorization; server-side verification; rate limits; duplicate-event prevention; audit logs; secure gift-code handling (encryption, masked views, access logging); data minimization (no unnecessary PII, no game credentials ever); privacy policy, in-app consent, data-safety form for Google Play, account deletion path inside app **and** a web URL; retention schedule (e.g., ledger kept anonymized for accounting/dispute period, tickets purged after N months); child safety/age gate and DPDP Act 2023 obligations (verifiable parental consent for minors, no tracking/targeted ads for children, consider restricting under-18s from ads/redemptions); Google Play Developer Program policies (deceptive behavior, incentivized installs/ratings, ads, user data, gift-code/rewards apps); YouTube API Services and incentivized-engagement rules; Unity Ads program policies (no incentivizing clicks/installs, no ad manipulation, rewarded ad disclosure, test-mode handling); sponsor/offerwall terms; clear advertising disclosure; applicable Indian law (including gaming/rewards regulation, consumer protection and tax considerations on prizes) **requires qualified legal review before launch**. Do not build features that fake installs, spoof engagement, manipulate ad metrics, bypass platform restrictions or promise undeliverable rewards.

## 28. Required output quality and final report

Deliver a polished, production-oriented result: clean architecture (UI / domain / data layers, DI), config-driven (no hard-coded rewards or IDs), strong typing and input validation, logging and monitoring, README with setup and deployment steps, environment separation (dev/staging/prod Firebase aliases; test vs production Unity), CI running rules tests, function tests and lint, and release checklist.

**Final report from the agent must include:** what was implemented and verified (with test evidence); what was only partially done; **Missing configuration / external dashboard actions** (Section 0.3 updated): Firebase apiKey/messagingSenderId/appId, SHA fingerprints, Blaze plan, App Check setup, Unity project confirmation, placement IDs, S2S secret, attribution/offerwall partner, gift-code supplier, legal sign-off; known limitations (e.g., ad completion trust level, install verification mode per campaign); and the list of residual risks.

*End of master prompt.*
