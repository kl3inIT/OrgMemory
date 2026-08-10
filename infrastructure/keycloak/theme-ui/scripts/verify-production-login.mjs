import { chromium } from "@playwright/test";

const expectedTheme = process.argv[2];
if (!/^[A-Za-z0-9._-]+$/.test(expectedTheme ?? "")) {
  throw new Error(`invalid expected theme: ${JSON.stringify(expectedTheme)}`);
}
if (expectedTheme !== "keycloak" && expectedTheme !== "orgmemory-shadcn") {
  throw new Error(`unsupported expected production theme: ${expectedTheme}`);
}

const authOrigin = new URL(process.env.ORGMEMORY_AUTH_ORIGIN ?? "https://auth.kl3in.tech");
if (!/^https?:$/.test(authOrigin.protocol) || authOrigin.pathname !== "/") {
  throw new Error(`invalid authorization origin: ${authOrigin.href}`);
}
const authorizationUrl = new URL("/realms/orgmemory/protocol/openid-connect/auth", authOrigin);
authorizationUrl.search = new URLSearchParams({
  client_id: "orgmemory-web",
  redirect_uri: "https://om.kl3in.tech/login/oauth2/code/keycloak",
  response_type: "code",
  scope: "openid",
  state: "orgmemory-production-browser-smoke",
  nonce: "orgmemory-production-browser-smoke",
  code_challenge: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  code_challenge_method: "S256"
}).toString();

const browser = await chromium.launch({ headless: true });
try {
  const context = await browser.newContext({
    serviceWorkers: "block",
    viewport: { width: 1280, height: 900 },
  });
  const page = await context.newPage();
  const failedRequests = [];
  const failedResponses = [];
  const externalRequests = [];
  const consoleErrors = [];
  const successfulThemeAssets = [];
  let successfulStockStylesheet = false;
  await page.route("**/*", async route => {
    const requestUrl = new URL(route.request().url());
    if (requestUrl.origin !== authorizationUrl.origin) {
      externalRequests.push(route.request().url());
      await route.abort("blockedbyclient");
      return;
    }
    await route.continue();
  });
  await page.routeWebSocket("**/*", webSocket => {
    externalRequests.push(webSocket.url());
    webSocket.close();
  });
  page.on("requestfailed", request => failedRequests.push(`${request.method()} ${request.url()}`));
  page.on("console", message => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });
  page.on("response", response => {
    const resourceType = response.request().resourceType();
    if (resourceType !== "document" && !response.ok()) {
      failedResponses.push(`${response.status()} ${response.url()}`);
    }
    if (
      response.ok() &&
      response.url().includes(`/${expectedTheme}/dist/`) &&
      /\.(?:css|js|woff2)(?:\?|$)/.test(response.url())
    ) {
      successfulThemeAssets.push(response.url());
    }
    if (response.ok() && /\/login\/keycloak\/css\/login\.css(?:\?|$)/.test(response.url())) {
      successfulStockStylesheet = true;
    }
  });

  const response = await page.goto(authorizationUrl.href, { waitUntil: "networkidle" });
  if (!response?.ok()) {
    const body = (await page.locator("body").innerText()).slice(0, 500);
    throw new Error(`authorization endpoint returned ${response?.status()} at ${page.url()}: ${body}`);
  }

  const username = page.locator('input[name="username"]');
  const password = page.locator('input[name="password"]');
  const loginForm = page.locator("#kc-form-login");
  const submit = page.locator("#kc-login");
  await username.waitFor({ state: "visible" });
  await password.waitFor({ state: "visible" });
  await loginForm.waitFor({ state: "visible" });
  await submit.waitFor({ state: "visible" });
  const formSemantics = await page.evaluate(() => {
    const form = document.querySelector("#kc-form-login");
    const username = document.querySelector('input[name="username"]');
    const password = document.querySelector('input[name="password"]');
    const submit = document.querySelector("#kc-login");
    return {
      action: form?.action ?? "",
      method: form?.method ?? "",
      passwordEditable: password instanceof HTMLInputElement && !password.disabled && !password.readOnly,
      passwordAssociated: password instanceof HTMLInputElement && password.form === form,
      passwordType: password instanceof HTMLInputElement ? password.type : "",
      submitAssociated: (submit instanceof HTMLButtonElement || submit instanceof HTMLInputElement)
        && submit.form === form,
      submitEnabled: submit instanceof HTMLButtonElement || submit instanceof HTMLInputElement
        ? !submit.disabled
        : false,
      submitType: submit instanceof HTMLButtonElement || submit instanceof HTMLInputElement
        ? submit.type
        : "",
      usernameEditable: username instanceof HTMLInputElement && !username.disabled && !username.readOnly,
      usernameAssociated: username instanceof HTMLInputElement && username.form === form,
    };
  });
  const formAction = new URL(formSemantics.action, authorizationUrl);
  if (
    formSemantics.method !== "post"
    || !formSemantics.usernameEditable
    || !formSemantics.usernameAssociated
    || !formSemantics.passwordEditable
    || !formSemantics.passwordAssociated
    || formSemantics.passwordType !== "password"
    || !formSemantics.submitAssociated
    || !formSemantics.submitEnabled
    || formSemantics.submitType !== "submit"
    || formAction.origin !== authorizationUrl.origin
    || formAction.pathname !== "/realms/orgmemory/login-actions/authenticate"
    || !formAction.searchParams.get("session_code")
    || !formAction.searchParams.get("execution")
    || formAction.searchParams.get("client_id") !== "orgmemory-web"
    || !formAction.searchParams.get("tab_id")
  ) {
    throw new Error(`production login form has unsafe semantics: ${JSON.stringify(formSemantics)}`);
  }

  if (expectedTheme === "orgmemory-shadcn") {
    await page.locator(".orgmemory-login__card").waitFor({ state: "visible" });
    const stylesheet = await page.locator('link[rel="stylesheet"]').evaluateAll(links =>
      links.map(link => link.href).find(href => href.includes("/orgmemory-shadcn/dist/assets/"))
    );
    if (!stylesheet) throw new Error("rendered OrgMemory page did not load its theme stylesheet");
    const fontFamily = await username.evaluate(element => getComputedStyle(element).fontFamily);
    if (!fontFamily.includes("Hanken Grotesk Variable")) {
      throw new Error(`rendered OrgMemory page did not apply Hanken Grotesk Variable: ${fontFamily}`);
    }
    await page.evaluate(() => document.fonts.ready);
    const fontLoaded = await page.evaluate(async () => {
      await document.fonts.load('16px "Hanken Grotesk Variable"');
      return {
        checked: document.fonts.check('16px "Hanken Grotesk Variable"'),
        loadedFaces: [...document.fonts].filter(
          face => face.family.replaceAll('"', "") === "Hanken Grotesk Variable" && face.status === "loaded",
        ).length,
      };
    });
    if (!fontLoaded.checked || fontLoaded.loadedFaces === 0) {
      throw new Error("rendered OrgMemory page did not load Hanken Grotesk Variable");
    }
    for (const extension of [".css", ".js", ".woff2"]) {
      if (!successfulThemeAssets.some(url => url.includes(extension))) {
        throw new Error(`no successful OrgMemory theme ${extension} response was observed`);
      }
    }
    if (!successfulThemeAssets.some(url => /hanken-grotesk-(?:latin|vietnamese)-wght-normal[^/]*\.woff2(?:\?|$)/.test(url))) {
      throw new Error("no successful Hanken Grotesk Variable font response was observed");
    }
  } else if (expectedTheme === "keycloak") {
    if (await page.locator(".orgmemory-login__card").count()) {
      throw new Error(`expected stock theme ${expectedTheme}, but OrgMemory theme rendered`);
    }
    await page.locator("html.login-pf").waitFor({ state: "attached" });
    await page.locator('body[data-page-id="login-login"]').waitFor({ state: "visible" });
    if (!successfulStockStylesheet) {
      throw new Error("stock Keycloak login stylesheet did not load successfully");
    }
    const stockStylesApplied = await page.evaluate(() => {
      const expectedSuffix = "/login/keycloak/css/login.css";
      const linked = [...document.querySelectorAll('link[rel="stylesheet"]')].some(
        link => !link.disabled && new URL(link.href).pathname.endsWith(expectedSuffix),
      );
      const parsed = [...document.styleSheets].some(
        sheet => sheet.href && new URL(sheet.href).pathname.endsWith(expectedSuffix),
      );
      const submitStyle = getComputedStyle(document.querySelector("#kc-login"));
      return linked && parsed && submitStyle.display !== "none"
        && submitStyle.backgroundColor !== "rgba(0, 0, 0, 0)";
    });
    if (!stockStylesApplied) throw new Error("stock Keycloak stylesheet was not applied");
  }

  if (failedRequests.length || failedResponses.length || externalRequests.length || consoleErrors.length) {
    throw new Error(JSON.stringify({ failedRequests, failedResponses, externalRequests, consoleErrors }));
  }
  if (await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)) {
    throw new Error("production login page has horizontal overflow");
  }

  console.log(`Rendered production login smoke passed for ${expectedTheme}.`);
} finally {
  await browser.close();
}
