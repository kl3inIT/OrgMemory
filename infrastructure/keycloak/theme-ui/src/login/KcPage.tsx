import { Suspense, lazy } from "react";
import type { ClassKey } from "keycloakify/login";
import DefaultPage from "keycloakify/login/DefaultPage";
import Template from "keycloakify/login/Template";
import type { KcContext } from "./KcContext";
import { useI18n } from "./i18n";
import "./main.css";

const UserProfileFormFields = lazy(
  () => import("keycloakify/login/UserProfileFormFields")
);

export default function KcPage(props: { kcContext: KcContext }) {
  const { i18n } = useI18n({ kcContext: props.kcContext });

  return (
    <Suspense>
      <DefaultPage
        kcContext={props.kcContext}
        i18n={i18n}
        classes={classes}
        Template={Template}
        doUseDefaultCss={true}
        UserProfileFormFields={UserProfileFormFields}
        doMakeUserConfirmPassword={true}
      />
    </Suspense>
  );
}

const classes = {
  kcHtmlClass: "orgmemory-login",
  kcBodyClass: "orgmemory-login__body",
  kcLoginClass: "orgmemory-login__page",
  kcFormCardClass: "orgmemory-login__card"
} satisfies { [key in ClassKey]?: string };
