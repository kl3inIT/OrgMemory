import { getKcContextMock } from "./login/KcPageStory";

window.kcContext = getKcContextMock({
  pageId: "login.ftl",
  overrides: {
    realm: {
      displayName: "OrgMemory",
      displayNameHtml: "OrgMemory"
    }
  }
});

void import("./main-kc");
