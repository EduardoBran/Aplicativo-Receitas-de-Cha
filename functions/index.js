const functions = require("firebase-functions");
const { onMessagePublished } = require("firebase-functions/v2/pubsub"); // Importação V2 para PubSub
const admin = require("firebase-admin");
const crypto = require("crypto");
const { google } = require("googleapis");

admin.initializeApp();

// Mantenha em sincronia com seu app/Play Console
const EXPECTED_PACKAGE_NAME = "com.luizeduardobrandao.appreceitascha";
const LIFETIME_PRODUCT_ID = "plan_life";

// --- FUNÇÃO V1 (HTTPS onCall) ---
// Mantemos na V1 pois é simples e estável para chamadas diretas do app
exports.verifyAndGrantLifetime = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Faça login para validar a compra.");
  }

  const uid = context.auth.uid;

  const packageName = String(data?.packageName || "");
  const productId = String(data?.productId || "");
  const purchaseToken = String(data?.purchaseToken || "");

  if (!packageName || !productId || !purchaseToken) {
    throw new functions.https.HttpsError("invalid-argument", "Parâmetros inválidos.");
  }

  if (packageName !== EXPECTED_PACKAGE_NAME) {
    throw new functions.https.HttpsError("failed-precondition", "Package inválido.");
  }

  if (productId !== LIFETIME_PRODUCT_ID) {
    throw new functions.https.HttpsError("failed-precondition", "Produto inválido.");
  }

  // Hash do token (token pode ter caracteres inválidos para key do RTDB)
  const tokenHash = crypto.createHash("sha256").update(purchaseToken).digest("hex");

  // Idempotência / anti-replay (mesmo token só pode liberar para o mesmo uid)
  const tokenRef = admin.database().ref(`purchaseTokens/${tokenHash}`);
  const tokenSnap = await tokenRef.get();
  if (tokenSnap.exists()) {
    const claimed = tokenSnap.val();
    if (claimed?.uid && claimed.uid !== uid) {
      throw new functions.https.HttpsError("permission-denied", "Token já associado a outro usuário.");
    }
    // Se já é do mesmo uid, só garante que /userPlans está correto e retorna OK.
  }

  // Autenticação Google (ADC - credenciais padrão do ambiente)
  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });

  const androidpublisher = google.androidpublisher({ version: "v3", auth });

  // Verifica compra (in-app product)
  // purchases.products.get: packageName, productId, token
  let purchase;
  try {
    const res = await androidpublisher.purchases.products.get({
      packageName,
      productId,
      token: purchaseToken,
    });
    purchase = res.data;
  } catch (err) {
    console.error("Erro Google Play API:", err);
    throw new functions.https.HttpsError("internal", "Falha ao consultar compra no Google Play.");
  }

  const purchaseState = Number(purchase.purchaseState);
  // purchaseState 0 = purchased (pago/ok)
  if (purchaseState !== 0) {
    throw new functions.https.HttpsError("failed-precondition", "Compra não está ativa (cancelada/refund/pendente).");
  }

  // Grava plano vitalício
  const userPlanRef = admin.database().ref(`userPlans/${uid}`);
  await userPlanRef.set({
    planId: LIFETIME_PRODUCT_ID,
    isLifetime: true,
    expiresAtMillis: null,
    updatedAtMillis: Date.now(),
    orderId: purchase.orderId || null,
    purchaseTimeMillis: purchase.purchaseTimeMillis ? Number(purchase.purchaseTimeMillis) : null,
  });

  // Salva claim do token
  await tokenRef.set({
    uid,
    packageName,
    productId,
    orderId: purchase.orderId || null,
    updatedAtMillis: Date.now(),
  });

  return { ok: true, planId: LIFETIME_PRODUCT_ID };
});

// --- FUNÇÃO V2 (Pub/Sub) ---
// Usamos V2 aqui para corrigir o erro "functions.pubsub.topic is not a function"
/**
 * RTDN: Escuta notificações do Google Play (Reembolsos, Cancelamentos, Revogações).
 * Configurar tópico no GCloud: 'play-store-notifications'
 */
exports.handlePlayStoreNotification = onMessagePublished("play-store-notifications", async (event) => {
    // Na V2, a mensagem está dentro de event.data.message
    const message = event.data.message;

    // Decodifica a mensagem (base64)
    const dataBase64 = message.data ? Buffer.from(message.data, "base64").toString() : "{}";
    const notification = JSON.parse(dataBase64);

    console.log("Notificação recebida:", JSON.stringify(notification));

    // Queremos apenas notificações de cancelamento/revogação de compra única (voidedPurchaseNotification)
    if (!notification.voidedPurchaseNotification) {
      return null;
    }

    const purchaseToken = notification.voidedPurchaseNotification.purchaseToken;
    const orderId = notification.voidedPurchaseNotification.orderId;

    if (!orderId) {
      console.log("OrderId não encontrado na notificação.");
      return null;
    }

    console.log(`Processando reembolso para OrderId: ${orderId}`);

    // 2. Encontrar o usuário que possui este orderId
    const userPlansRef = admin.database().ref("userPlans");

    // Consulta: busca nó onde child 'orderId' é igual ao recebido
    const snapshot = await userPlansRef.orderByChild("orderId").equalTo(orderId).once("value");

    if (!snapshot.exists()) {
      console.log("Nenhum usuário encontrado com este OrderId.");
      return null;
    }

    const updates = {};

    // 3. Revogar o acesso
    snapshot.forEach((childSnapshot) => {
      const uid = childSnapshot.key;
      console.log(`Revogando acesso do UID: ${uid}`);

      // Define isLifetime como false e remove o plano
      updates[`/${uid}/isLifetime`] = false;
      updates[`/${uid}/planId`] = "none";
      updates[`/${uid}/revokedAt`] = admin.database.ServerValue.TIMESTAMP;
    });

    return userPlansRef.update(updates);
});