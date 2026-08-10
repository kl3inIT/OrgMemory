import { i18nBuilder } from "keycloakify/login";
import type { ThemeName } from "../kc.gen";

const { useI18n, ofTypeI18n } = i18nBuilder
  .withThemeName<ThemeName>()
  .withCustomTranslations({
    en: {
      loginAccountTitle: "Welcome back",
      usernameOrEmail: "Email or username",
      doLogIn: "Continue",
      doForgotPassword: "Forgot password?",
      emailForgotTitle: "Reset your access",
      emailInstruction: "Enter your email address and we will send instructions to restore access.",
      backToLogin: "Back to sign in"
    }
  })
  .build();

export type I18n = typeof ofTypeI18n;
export { useI18n };
