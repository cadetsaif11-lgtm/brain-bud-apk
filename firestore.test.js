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

test("Unauthenticated user cannot read or write any BrainBuddy collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("decks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      totalStudyMinutes: 0,
      quizzesCompleted: 0,
      cardsMastered: 0,
      currentStreakDays: 1,
      totalXp: 0,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Authenticated owner: profile create, update, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      totalStudyMinutes: 0,
      quizzesCompleted: 0,
      cardsMastered: 0,
      currentStreakDays: 1,
      totalXp: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update on stats + AI weak area fields
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalStudyMinutes: 25,
      totalXp: 120,
      weakAreaSummary: "Focus on Cellular Respiration ATP yield.",
      weakTopics: ["Krebs Cycle", "Electron Transport"],
      studyRecommendations: ["Review NADH counts"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field (shadow update)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject cross-user read/write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      totalXp: 9999,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Reject listing /users root collection
  await assertFails(aliceDb.collection("users").get());
});

test("StudyDeck, Flashcard, QuizQuestion, and StudySession full lifecycle & security bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const deckRef = aliceDb.collection("users").doc(ALICE_UID).collection("decks").doc("deck_bio_1");
  await assertSucceeds(
    deckRef.set({
      userId: ALICE_UID,
      title: "Cell Biology",
      subject: "Biology",
      sourceNotes: "Mitochondria produce ATP through oxidative phosphorylation.",
      aiSummary: "Mitochondria are powerhouses producing ATP.",
      flashcardCount: 1,
      masteredCount: 0,
      quizCount: 1,
      lastScorePercent: -1,
      weakConcepts: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject extra @DocumentId 'id' field in payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("decks").doc("deck_bad").set({
      id: "deck_bad",
      userId: ALICE_UID,
      title: "Bad Deck",
      subject: "Biology",
      sourceNotes: "Notes",
      aiSummary: "Summary",
      flashcardCount: 0,
      masteredCount: 0,
      quizCount: 0,
      lastScorePercent: -1,
      weakConcepts: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Flashcard create & mastery update
  const cardRef = deckRef.collection("flashcards").doc("card_1");
  await assertSucceeds(
    cardRef.set({
      userId: ALICE_UID,
      deckId: "deck_bio_1",
      front: "What does mitochondria produce?",
      back: "ATP",
      hint: "Cellular energy currency",
      conceptTag: "Cellular Respiration",
      masteryLevel: "NEW",
      reviewCount: 0,
      missCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    cardRef.update({
      masteryLevel: "MASTERED",
      reviewCount: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid mastery enum
  await assertFails(
    cardRef.update({
      masteryLevel: "EXPERT",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // QuizQuestion create & stats update
  const qRef = deckRef.collection("questions").doc("q_1");
  await assertSucceeds(
    qRef.set({
      userId: ALICE_UID,
      deckId: "deck_bio_1",
      prompt: "Which molecule is the primary energy currency of the cell?",
      options: ["ATP", "DNA", "RNA", "NADPH"],
      correctIndex: 0,
      explanation: "ATP stores and transfers chemical energy in cells.",
      conceptTag: "Cellular Respiration",
      timesAnswered: 0,
      timesMissed: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    qRef.update({
      timesAnswered: 1,
      timesMissed: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Session log create & list
  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1");
  await assertSucceeds(
    sessionRef.set({
      userId: ALICE_UID,
      deckId: "deck_bio_1",
      deckTitle: "Cell Biology",
      sessionType: "QUIZ",
      scorePercent: 85,
      durationMinutes: 5,
      weakTags: ["Krebs Cycle"],
      dayKey: "2026-11-23",
      createdAt: created,
    })
  );

  // Reject session update (immutable log)
  await assertFails(sessionRef.update({ scorePercent: 100 }));

  // List subcollections for Alice succeed; empty subcollections succeed; Bob denied
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("decks").get());
  await assertSucceeds(deckRef.collection("flashcards").get());
  await assertSucceeds(deckRef.collection("questions").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get());

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("decks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("decks").doc("deck_bio_1").collection("flashcards").get());

  // Reject future timestamp tampering
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_future").set({
      userId: ALICE_UID,
      deckId: "deck_bio_1",
      deckTitle: "Cell Biology",
      sessionType: "FOCUS",
      scorePercent: 100,
      durationMinutes: 25,
      weakTags: [],
      dayKey: "2026-11-23",
      createdAt: new Date(Date.now() + 60000),
    })
  );
});
