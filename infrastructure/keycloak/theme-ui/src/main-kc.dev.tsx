import { getKcContextMock } from "./login/KcPageStory";
import type { KcContext } from "./login/KcContext";

/* Dev-only page selection: /?page=login-oauth-grant.ftl previews another
   covered template with the same mock realm. */
const requestedPageId = new URLSearchParams(window.location.search).get("page");

window.kcContext = getKcContextMock({
  pageId: (requestedPageId ?? "login.ftl") as KcContext["pageId"],
  overrides: {
    realm: {
      displayName: "OrgMemory",
      displayNameHtml: "OrgMemory"
    }
  }
});

void import("./main-kc");
