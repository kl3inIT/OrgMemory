import type { DeepPartial } from "keycloakify/tools/DeepPartial";
import { createGetKcContextMock } from "keycloakify/login/KcContext";
import type {
  KcContext,
  KcContextExtension,
  KcContextExtensionPerPage
} from "./KcContext";
import KcPage from "./KcPage";
import { kcEnvDefaults, themeNames } from "../kc.gen";

const kcContextExtension: KcContextExtension = {
  themeName: themeNames[0],
  properties: { ...kcEnvDefaults }
};

export const { getKcContextMock } = createGetKcContextMock({
  kcContextExtension,
  kcContextExtensionPerPage: {} as KcContextExtensionPerPage,
  overrides: {},
  overridesPerPage: {}
});

export function createKcPageStory<PageId extends KcContext["pageId"]>(params: {
  pageId: PageId;
}) {
  function KcPageStory(props: {
    kcContext?: DeepPartial<Extract<KcContext, { pageId: PageId }>>;
  }) {
    return (
      <KcPage
        kcContext={getKcContextMock({
          pageId: params.pageId,
          overrides: props.kcContext
        })}
      />
    );
  }

  return { KcPageStory };
}
