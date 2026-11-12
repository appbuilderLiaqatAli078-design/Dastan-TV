import * as admin from "firebase-admin";

/**
 * Server-side script to assign the verified Admin custom claim and /admins/{uid}
 * record to rdxkinglucky@gmail.com using the Firebase Admin SDK.
 *
 * Usage:
 *   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"
 *   npm run set-admin
 */
const AUTHORIZED_ADMIN_EMAIL = "rdxkinglucky@gmail.com";

async function assignAdminRole() {
  if (!admin.apps.length) {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
    });
  }

  const auth = admin.auth();
  const db = admin.firestore();

  const userRecord = await auth.getUserByEmail(AUTHORIZED_ADMIN_EMAIL);
  await auth.setCustomUserClaims(userRecord.uid, {
    role: "admin",
    admin: true,
  });

  await db.collection("admins").doc(userRecord.uid).set(
    {
      uid: userRecord.uid,
      email: AUTHORIZED_ADMIN_EMAIL,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  console.log(
    `Successfully assigned Admin custom claims and /admins/${userRecord.uid} document for ${AUTHORIZED_ADMIN_EMAIL}`
  );
}

assignAdminRole().catch((err) => {
  console.error("Failed to assign admin role:", err);
  process.exit(1);
});
