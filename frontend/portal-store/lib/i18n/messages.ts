/**
 * UI 文案三语词典（STORE-S04 / I18N）。
 * 覆盖 login / account / settings / security 四页表单文案、按钮、提示。
 * 原型 EN 文案为基准（prototype_conformance MATCH 锚点）；ES/FR 标 [TRANSLATION_PENDING]。
 */

import type { Locale } from '../api/types'

export interface UiMessages {
  brand: string
  common: {
    loading: string
    cancel: string
    confirm: string
    retry: string
    signOut: string
    save: string
  }
  login: {
    signInTitle: string
    signInSubtitle: string
    continueWithGoogle: string
    continueWithApple: string
    or: string
    emailLabel: string
    emailMeCode: string
    appleRelayNote: string
    checkEmailTitle: string
    checkEmailSubtitle: string
    change: string
    verificationCode: string
    verifyContinue: string
    didntGetIt: string
    resendIn: string
    resend: string
    enterAllDigits: string
    terms: string
    privacy: string
    agreePrefix: string
    agreeAnd: string
  }
  account: {
    dashboardTitle: string
    welcome: string
    profileTitle: string
    name: string
    email: string
    phone: string
    tier: string
    memberSince: string
    notProvided: string
  }
  settings: {
    title: string
    profileTab: string
    fullName: string
    emailField: string
    emailChangeNote: string
    phone: string
    passwordlessTitle: string
    passwordlessBody: string
    manageSecurity: string
    dangerZone: string
    deleteAccountTitle: string
    deleteAccountBody: string
    deleteAccountCta: string
  }
  security: {
    title: string
    subtitle: string
    loginMethods: string
    loginMethodsHint: string
    primary: string
    verified: string
    notConnected: string
    lastUsed: string
    appleRelayNote: string
    relayInvalid: string
    connect: string
    disconnect: string
    primaryCannotRemove: string
    keepOneMethod: string
    keepAtLeastOne: string
    devicesTitle: string
    devicesSubtitle: string
    thisDevice: string
    via: string
    signOutDevice: string
    signOutOthers: string
    signOutOthersConfirm: string
    changePrimaryTitle: string
    changePrimaryCta: string
    pickExistingHint: string
    useAnotherEmail: string
    newEmailLabel: string
    sendCode: string
    codeLabel: string
    changePrimarySubmit: string
    deleteAccountTitle: string
    deleteAccountWarning: string
    deleteAccountConfirmLabel: string
    deleteAccountConfirmWord: string
    deleteAccountSubmit: string
  }
}

const en: UiMessages = {
  brand: 'Dreamy',
  common: {
    loading: 'Loading...',
    cancel: 'Cancel',
    confirm: 'Confirm',
    retry: 'Try again',
    signOut: 'Sign Out',
    save: 'Save Changes'
  },
  login: {
    signInTitle: 'Sign in or create account',
    signInSubtitle: "Enter your email and we'll send you a 6-digit code. No password needed.",
    continueWithGoogle: 'Continue with Google',
    continueWithApple: 'Continue with Apple',
    or: 'or',
    emailLabel: 'Email',
    emailMeCode: 'Email me a code',
    appleRelayNote: 'Apple may hide your email with a private relay address — you can still sign in.',
    checkEmailTitle: 'Check your email',
    checkEmailSubtitle: 'We sent a 6-digit code to',
    change: 'Change',
    verificationCode: 'Verification code',
    verifyContinue: 'Verify & continue',
    didntGetIt: "Didn't get it?",
    resendIn: 'Resend in',
    resend: 'Resend code',
    enterAllDigits: 'Enter all 6 digits',
    terms: 'Terms',
    privacy: 'Privacy Policy',
    agreePrefix: "By continuing you agree to Dreamy's",
    agreeAnd: 'and'
  },
  account: {
    dashboardTitle: 'My Account',
    welcome: 'Welcome back',
    profileTitle: 'Profile',
    name: 'Name',
    email: 'Email',
    phone: 'Phone',
    tier: 'Membership',
    memberSince: 'Member since',
    notProvided: 'Not provided'
  },
  settings: {
    title: 'Settings',
    profileTab: 'Profile',
    fullName: 'Full Name',
    emailField: 'Email',
    emailChangeNote: 'Changing your email requires re-verification with a one-time code.',
    phone: 'Phone',
    passwordlessTitle: 'Passwordless account',
    passwordlessBody:
      'You sign in with a one-time email code, Google, or Apple — no password to manage. Manage your login methods and devices in',
    manageSecurity: 'Login & Security',
    dangerZone: 'Danger zone',
    deleteAccountTitle: 'Delete account',
    deleteAccountBody:
      'Your account will be deactivated and permanently deleted after a 30-day grace period. This cannot be undone.',
    deleteAccountCta: 'Delete my account'
  },
  security: {
    title: 'Login & Security',
    subtitle: 'Manage how you sign in and the devices connected to your account.',
    loginMethods: 'Login methods',
    loginMethodsHint: 'Connect multiple methods — they all sign you into the same account.',
    primary: 'Primary',
    verified: 'Verified',
    notConnected: 'Not connected',
    lastUsed: 'last used',
    appleRelayNote: 'Apple may hide your email with a private relay address.',
    relayInvalid: 'Private relay address is no longer reachable.',
    connect: 'Connect',
    disconnect: 'Disconnect',
    primaryCannotRemove: 'Primary email cannot be removed',
    keepOneMethod: 'Keep at least one login method',
    keepAtLeastOne:
      'Keep at least one login method connected. Your primary email stays verified and cannot be removed.',
    devicesTitle: 'Devices & sessions',
    devicesSubtitle: "Where you're currently signed in.",
    thisDevice: 'This device',
    via: 'via',
    signOutDevice: 'Sign out',
    signOutOthers: 'Sign out other devices',
    signOutOthersConfirm: 'Sign out of all other devices? They will need to sign in again.',
    changePrimaryTitle: 'Change primary email',
    changePrimaryCta: 'Change primary email',
    pickExistingHint: 'Select a verified email to set as primary, or enter a new one.',
    useAnotherEmail: 'Use a different email',
    newEmailLabel: 'New email',
    sendCode: 'Send code',
    codeLabel: 'Verification code',
    changePrimarySubmit: 'Update primary email',
    deleteAccountTitle: 'Delete account',
    deleteAccountWarning:
      'This deactivates your account immediately and permanently deletes it after a 30-day grace period. This cannot be undone.',
    deleteAccountConfirmLabel: 'Type DELETE to confirm',
    deleteAccountConfirmWord: 'DELETE',
    deleteAccountSubmit: 'Delete account'
  }
}

// [TRANSLATION_PENDING] ES — 占位翻译，待专业复核
const es: UiMessages = {
  brand: 'Dreamy',
  common: {
    loading: 'Cargando...',
    cancel: 'Cancelar',
    confirm: 'Confirmar',
    retry: 'Reintentar',
    signOut: 'Cerrar sesion',
    save: 'Guardar cambios'
  },
  login: {
    signInTitle: 'Inicie sesion o cree una cuenta',
    signInSubtitle: 'Introduzca su correo y le enviaremos un codigo de 6 digitos. Sin contrasena.',
    continueWithGoogle: 'Continuar con Google',
    continueWithApple: 'Continuar con Apple',
    or: 'o',
    emailLabel: 'Correo electronico',
    emailMeCode: 'Enviarme un codigo',
    appleRelayNote: 'Apple puede ocultar su correo con una direccion privada — aun puede iniciar sesion.',
    checkEmailTitle: 'Revise su correo',
    checkEmailSubtitle: 'Enviamos un codigo de 6 digitos a',
    change: 'Cambiar',
    verificationCode: 'Codigo de verificacion',
    verifyContinue: 'Verificar y continuar',
    didntGetIt: 'No lo recibio?',
    resendIn: 'Reenviar en',
    resend: 'Reenviar codigo',
    enterAllDigits: 'Introduzca los 6 digitos',
    terms: 'Terminos',
    privacy: 'Politica de privacidad',
    agreePrefix: 'Al continuar acepta los',
    agreeAnd: 'y la'
  },
  account: {
    dashboardTitle: 'Mi cuenta',
    welcome: 'Bienvenido de nuevo',
    profileTitle: 'Perfil',
    name: 'Nombre',
    email: 'Correo electronico',
    phone: 'Telefono',
    tier: 'Membresia',
    memberSince: 'Miembro desde',
    notProvided: 'No proporcionado'
  },
  settings: {
    title: 'Configuracion',
    profileTab: 'Perfil',
    fullName: 'Nombre completo',
    emailField: 'Correo electronico',
    emailChangeNote: 'Cambiar su correo requiere reverificacion con un codigo de un solo uso.',
    phone: 'Telefono',
    passwordlessTitle: 'Cuenta sin contrasena',
    passwordlessBody:
      'Inicia sesion con un codigo de correo, Google o Apple — sin contrasena que gestionar. Gestione sus metodos y dispositivos en',
    manageSecurity: 'Inicio de sesion y seguridad',
    dangerZone: 'Zona de peligro',
    deleteAccountTitle: 'Eliminar cuenta',
    deleteAccountBody:
      'Su cuenta se desactivara y se eliminara permanentemente tras un periodo de gracia de 30 dias. Esto no se puede deshacer.',
    deleteAccountCta: 'Eliminar mi cuenta'
  },
  security: {
    title: 'Inicio de sesion y seguridad',
    subtitle: 'Gestione como inicia sesion y los dispositivos conectados a su cuenta.',
    loginMethods: 'Metodos de inicio de sesion',
    loginMethodsHint: 'Conecte varios metodos — todos acceden a la misma cuenta.',
    primary: 'Principal',
    verified: 'Verificado',
    notConnected: 'No conectado',
    lastUsed: 'usado por ultima vez',
    appleRelayNote: 'Apple puede ocultar su correo con una direccion privada.',
    relayInvalid: 'La direccion privada ya no es accesible.',
    connect: 'Conectar',
    disconnect: 'Desconectar',
    primaryCannotRemove: 'El correo principal no se puede eliminar',
    keepOneMethod: 'Mantenga al menos un metodo de inicio de sesion',
    keepAtLeastOne:
      'Mantenga al menos un metodo conectado. Su correo principal permanece verificado y no se puede eliminar.',
    devicesTitle: 'Dispositivos y sesiones',
    devicesSubtitle: 'Donde tiene la sesion iniciada actualmente.',
    thisDevice: 'Este dispositivo',
    via: 'via',
    signOutDevice: 'Cerrar sesion',
    signOutOthers: 'Cerrar otros dispositivos',
    signOutOthersConfirm: 'Cerrar sesion en todos los demas dispositivos? Tendran que iniciar sesion de nuevo.',
    changePrimaryTitle: 'Cambiar correo principal',
    changePrimaryCta: 'Cambiar correo principal',
    pickExistingHint: 'Seleccione un correo verificado como principal, o ingrese uno nuevo.',
    useAnotherEmail: 'Usar otro correo',
    newEmailLabel: 'Nuevo correo',
    sendCode: 'Enviar codigo',
    codeLabel: 'Codigo de verificacion',
    changePrimarySubmit: 'Actualizar correo principal',
    deleteAccountTitle: 'Eliminar cuenta',
    deleteAccountWarning:
      'Esto desactiva su cuenta de inmediato y la elimina permanentemente tras 30 dias de gracia. No se puede deshacer.',
    deleteAccountConfirmLabel: 'Escriba DELETE para confirmar',
    deleteAccountConfirmWord: 'DELETE',
    deleteAccountSubmit: 'Eliminar cuenta'
  }
}

// [TRANSLATION_PENDING] FR — 占位翻译，待专业复核
const fr: UiMessages = {
  brand: 'Dreamy',
  common: {
    loading: 'Chargement...',
    cancel: 'Annuler',
    confirm: 'Confirmer',
    retry: 'Reessayer',
    signOut: 'Se deconnecter',
    save: 'Enregistrer'
  },
  login: {
    signInTitle: 'Connectez-vous ou creez un compte',
    signInSubtitle: 'Saisissez votre e-mail et nous vous enverrons un code a 6 chiffres. Sans mot de passe.',
    continueWithGoogle: 'Continuer avec Google',
    continueWithApple: 'Continuer avec Apple',
    or: 'ou',
    emailLabel: 'E-mail',
    emailMeCode: 'Envoyez-moi un code',
    appleRelayNote: 'Apple peut masquer votre e-mail avec une adresse privee — vous pouvez toujours vous connecter.',
    checkEmailTitle: 'Verifiez votre e-mail',
    checkEmailSubtitle: 'Nous avons envoye un code a 6 chiffres a',
    change: 'Modifier',
    verificationCode: 'Code de verification',
    verifyContinue: 'Verifier et continuer',
    didntGetIt: 'Pas recu ?',
    resendIn: 'Renvoyer dans',
    resend: 'Renvoyer le code',
    enterAllDigits: 'Saisissez les 6 chiffres',
    terms: 'Conditions',
    privacy: 'Politique de confidentialite',
    agreePrefix: 'En continuant, vous acceptez les',
    agreeAnd: 'et la'
  },
  account: {
    dashboardTitle: 'Mon compte',
    welcome: 'Bon retour',
    profileTitle: 'Profil',
    name: 'Nom',
    email: 'E-mail',
    phone: 'Telephone',
    tier: 'Adhesion',
    memberSince: 'Membre depuis',
    notProvided: 'Non renseigne'
  },
  settings: {
    title: 'Parametres',
    profileTab: 'Profil',
    fullName: 'Nom complet',
    emailField: 'E-mail',
    emailChangeNote: 'Modifier votre e-mail necessite une reverification avec un code a usage unique.',
    phone: 'Telephone',
    passwordlessTitle: 'Compte sans mot de passe',
    passwordlessBody:
      'Vous vous connectez avec un code par e-mail, Google ou Apple — aucun mot de passe a gerer. Gerez vos methodes et appareils dans',
    manageSecurity: 'Connexion et securite',
    dangerZone: 'Zone sensible',
    deleteAccountTitle: 'Supprimer le compte',
    deleteAccountBody:
      'Votre compte sera desactive et supprime definitivement apres un delai de grace de 30 jours. Action irreversible.',
    deleteAccountCta: 'Supprimer mon compte'
  },
  security: {
    title: 'Connexion et securite',
    subtitle: 'Gerez votre mode de connexion et les appareils lies a votre compte.',
    loginMethods: 'Methodes de connexion',
    loginMethodsHint: 'Connectez plusieurs methodes — elles donnent toutes acces au meme compte.',
    primary: 'Principal',
    verified: 'Verifie',
    notConnected: 'Non connecte',
    lastUsed: 'derniere utilisation',
    appleRelayNote: 'Apple peut masquer votre e-mail avec une adresse privee.',
    relayInvalid: 'L adresse privee n est plus joignable.',
    connect: 'Connecter',
    disconnect: 'Deconnecter',
    primaryCannotRemove: 'L e-mail principal ne peut pas etre supprime',
    keepOneMethod: 'Conservez au moins une methode de connexion',
    keepAtLeastOne:
      'Conservez au moins une methode connectee. Votre e-mail principal reste verifie et ne peut pas etre supprime.',
    devicesTitle: 'Appareils et sessions',
    devicesSubtitle: 'Ou vous etes actuellement connecte.',
    thisDevice: 'Cet appareil',
    via: 'via',
    signOutDevice: 'Deconnecter',
    signOutOthers: 'Deconnecter les autres appareils',
    signOutOthersConfirm: 'Se deconnecter de tous les autres appareils ? Ils devront se reconnecter.',
    changePrimaryTitle: 'Changer l e-mail principal',
    changePrimaryCta: 'Changer l e-mail principal',
    pickExistingHint: 'Selectionnez un e-mail verifie comme principal, ou entrez-en un nouveau.',
    useAnotherEmail: 'Utiliser un autre e-mail',
    newEmailLabel: 'Nouvel e-mail',
    sendCode: 'Envoyer le code',
    codeLabel: 'Code de verification',
    changePrimarySubmit: 'Mettre a jour l e-mail principal',
    deleteAccountTitle: 'Supprimer le compte',
    deleteAccountWarning:
      'Cela desactive votre compte immediatement et le supprime definitivement apres 30 jours. Action irreversible.',
    deleteAccountConfirmLabel: 'Tapez DELETE pour confirmer',
    deleteAccountConfirmWord: 'DELETE',
    deleteAccountSubmit: 'Supprimer le compte'
  }
}

const dictionaries: Record<Locale, UiMessages> = { en, es, fr }

export function getMessages(locale: Locale): UiMessages {
  return dictionaries[locale] ?? dictionaries.en
}
