import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { KcPage } from "./kc.gen";

const root = document.getElementById("root");
if (root === null || window.kcContext === undefined) {
  throw new Error("Unable to initialize the OrgMemory login theme");
}

createRoot(root).render(
  <StrictMode>
    <KcPage kcContext={window.kcContext} />
  </StrictMode>
);
