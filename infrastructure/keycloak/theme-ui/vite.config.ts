import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { keycloakify } from "keycloakify/vite-plugin";

export default defineConfig({
  plugins: [
    react(),
    keycloakify({
      themeName: "orgmemory-shadcn",
      accountThemeImplementation: "none",
      keycloakVersionTargets: {
        "22-to-25": false,
        "all-other-versions": "orgmemory-keycloak-theme.jar"
      }
    })
  ]
});
