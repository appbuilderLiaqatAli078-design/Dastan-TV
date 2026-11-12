const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_user_1";
const BOB_UID = "bob_user_2";
const ADMIN_UID = "admin_lucky_1";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("catalog_overrides").where("appId", "==", "dastan_tv").get()
  );
  await assertFails(unauthDb.collection("app_config").doc("global").get());
});

test("Regular user can create own FREE profile, cannot escalate role or VIP", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, {
      email: "alice@example.com",
      email_verified: true,
    })
    .firestore();

  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice Khan",
      photoUrl: "https://example.com/a.jpg",
      role: "user",
      membershipPlan: "FREE",
      preferredQuality: "AUTO",
      subtitleEnabled: true,
      isBlocked: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Attempt privilege escalation to admin on create by Bob
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, {
      email: "bob@example.com",
      email_verified: true,
    })
    .firestore();

  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: "bob@example.com",
      displayName: "Bob",
      photoUrl: "",
      role: "admin",
      membershipPlan: "YEARLY_VIP",
      preferredQuality: "AUTO",
      subtitleEnabled: true,
      isBlocked: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("PII Isolation: User cannot read another user's profile or favorites", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      photoUrl: "",
      role: "user",
      membershipPlan: "FREE",
      preferredQuality: "AUTO",
      subtitleEnabled: true,
      isBlocked: false,
      createdAt: now,
      updatedAt: now,
    });
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("cat_95")
      .set({
        userId: ALICE_UID,
        dramaId: "cat_95",
        title: "Barbaroslar Season 1",
        posterUrl: "https://giveme5.co/poster.jpg",
        episodeCount: 32,
        createdAt: now,
        updatedAt: now,
      });
  });

  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("favorites").doc("cat_95").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
});

test("Email spoofing test: unverified admin email is rejected", async () => {
  const spoofedAdminDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: "rdxkinglucky@gmail.com",
      email_verified: false,
    })
    .firestore();

  const now = new Date();
  await assertFails(
    spoofedAdminDb.collection("catalog_overrides").doc("drama_95").set({
      appId: "dastan_tv",
      targetType: "DRAMA",
      targetId: "95",
      customTitle: "Hacked Title",
      customDescription: "Hacked",
      customPosterUrl: "",
      isHidden: false,
      isFeatured: true,
      isVipOnly: false,
      priorityOrder: 1,
      updatedBy: "spoof_uid",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Verified Admin (rdxkinglucky@gmail.com) can write catalog_overrides and upgrade user VIP", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      photoUrl: "",
      role: "user",
      membershipPlan: "FREE",
      preferredQuality: "AUTO",
      subtitleEnabled: true,
      isBlocked: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: "rdxkinglucky@gmail.com",
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    adminDb.collection("catalog_overrides").doc("drama_95").set({
      appId: "dastan_tv",
      targetType: "DRAMA",
      targetId: "95",
      customTitle: "Barbaroslar Season 1 (Urdu Dubbed)",
      customDescription: "Epic Mediterranean naval adventure.",
      customPosterUrl: "https://giveme5.co/poster.jpg",
      isHidden: false,
      isFeatured: true,
      isVipOnly: false,
      priorityOrder: 1,
      updatedBy: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin upgrades Alice to YEARLY_VIP
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      membershipPlan: "YEARLY_VIP",
      updatedAt: new Date(),
    })
  );

  // Regular user can query catalog_overrides ONLY when filtering by appId == 'dastan_tv'
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();

  await assertSucceeds(
    aliceDb.collection("catalog_overrides").where("appId", "==", "dastan_tv").get()
  );
});
