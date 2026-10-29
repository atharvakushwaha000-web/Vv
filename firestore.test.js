const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read user profiles or campaigns", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("appCampaigns").where("visibility", "==", "public").get());
});

test("Authenticated user: can create own profile with user role, but cannot self-promote to superAdmin", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Gamer",
      email: "alice@example.com",
      referralCode: "VG-ALICE1",
      accountStatus: "active",
      role: "user",
      ageVerified18Plus: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob Attacker",
      referralCode: "VG-BOB99",
      accountStatus: "active",
      role: "superAdmin",
      ageVerified18Plus: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Cross-user isolation: Bob cannot read Alice's wallet or ledger", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ALICE_UID).collection("wallets").doc("main").set({
      userId: ALICE_UID,
      available: 50,
      pending: 0,
      lifetimeEarned: 50,
      redeemed: 0,
      sandboxCoins: 0,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("wallets").doc("main").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("wallets").doc("main").get());
});

test("Append-only ledger & idempotency: entries can be created once, never updated or deleted", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("idempotency").doc("ad_session_1").set({
      userId: ALICE_UID,
      txId: "tx_1",
      sourceType: "unity_rewarded_ad",
      createdAt: now,
    })
  );

  // Replay / overwrite attempt on idempotency key fails
  await assertFails(
    aliceDb.collection("idempotency").doc("ad_session_1").update({
      txId: "tx_2",
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ledger").doc("tx_1").set({
      userId: ALICE_UID,
      eventId: "session_1",
      sourceType: "unity_rewarded_ad",
      sourceId: "Rewarded_Android",
      title: "Watch Ad & Earn (+1 Coin)",
      amount: 1,
      transactionType: "earn",
      verificationStatus: "approved",
      verificationLevel: "test_mode",
      idempotencyKey: "ad_session_1",
      balanceAfter: 1,
      actor: "system",
      createdAt: now,
    })
  );

  // Attempting to mutate ledger entry fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("ledger").doc("tx_1").update({
      amount: 9999,
    })
  );
});

test("Client query alignment: querying appCampaigns with visibility=='public' succeeds, unfiltered query fails", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("appCampaigns").doc("camp_1").set({
      visibility: "public",
      appName: "Tactical Arena Pro",
      packageName: "com.example.tacticalarena",
      playStoreUrl: "https://play.google.com/store/apps/details?id=com.example.tacticalarena",
      shortDescription: "Complete tutorial and reach level 5",
      fullInstructions: "1. Install from Play Store. 2. Complete tutorial.",
      totalReward: 120,
      estimatedMinutes: 15,
      status: "live",
      categoryLabel: "Featured",
      verificationMethod: "manual",
      sponsorDisclosure: "Sponsored campaign verified by VaultGold team",
      slotsRemaining: 250,
      dailyLimit: 50,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("appCampaigns").where("visibility", "==", "public").get()
  );
  await assertFails(
    aliceDb.collection("appCampaigns").get()
  );
});
