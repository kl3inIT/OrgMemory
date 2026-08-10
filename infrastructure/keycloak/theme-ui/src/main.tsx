if (window.kcContext !== undefined) {
  void import("./main-kc");
} else if (import.meta.env.DEV) {
  void import("./main-kc.dev");
} else {
  throw new Error("Keycloak context is unavailable");
}
