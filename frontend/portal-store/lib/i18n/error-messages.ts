/**
 * i18n 错误码 → 三语文案映射（STORE-S04，消费 error-detail §2 码表）。
 * 数字 code 稳定，文案变更不影响映射逻辑（I18N-PLAN 治理锚点）。
 * 未知 code 兜底 INTERNAL_ERROR(50000) 文案。
 * 标 [TRANSLATION_PENDING] 的 ES/FR 为占位翻译，待专业复核（I18N-PLAN）。
 */

import type { Locale } from '../api/types'

type CodeMessages = Record<number, string>

const en: CodeMessages = {
  40000: 'Please check the highlighted fields and try again.',
  40001: 'Please enter a valid email address.',
  40002: 'Authentication configuration value is out of the allowed range.',
  40100: 'You are not signed in. Please sign in again.',
  40101: 'Incorrect code. Please try again.',
  40102: 'Your session has expired. Please sign in again.',
  40300: 'You do not have permission to perform this action.',
  40301: 'This account has been disabled.',
  40303: 'This sign-in method is currently unavailable.',
  40304: 'Your primary email cannot be removed.',
  40305: 'Keep at least one sign-in method connected.',
  40901: 'This email is already in use.',
  40902: 'Please sign in with your original method first, then link this one.',
  40903: 'This method is already linked to another account.',
  41001: 'Your code has expired. Please request a new one.',
  41002: 'Too many attempts. Please request a new code.',
  42901: 'Please wait a moment before requesting another code.',
  42902: 'Too many requests. Please try again later.',
  50000: 'Something went wrong. Please try again.',
  50001: 'The operation could not be completed. Please try again.',
  50002: 'We could not send the email. Please try again.',
  50201: 'Sign-in provider is unavailable. Please use an email code instead.',
  50401: 'Sign-in provider timed out. Please use an email code instead.'
}

// [TRANSLATION_PENDING] ES — 占位翻译，待专业复核
const es: CodeMessages = {
  40000: 'Revise los campos resaltados e intentelo de nuevo.',
  40001: 'Introduzca una direccion de correo valida.',
  40002: 'El valor de configuracion de autenticacion esta fuera del rango permitido.',
  40100: 'No ha iniciado sesion. Inicie sesion de nuevo.',
  40101: 'Codigo incorrecto. Intentelo de nuevo.',
  40102: 'Su sesion ha caducado. Inicie sesion de nuevo.',
  40300: 'No tiene permiso para realizar esta accion.',
  40301: 'Esta cuenta ha sido deshabilitada.',
  40303: 'Este metodo de inicio de sesion no esta disponible.',
  40304: 'Su correo principal no se puede eliminar.',
  40305: 'Mantenga al menos un metodo de inicio de sesion.',
  40901: 'Este correo ya esta en uso.',
  40902: 'Inicie sesion con su metodo original y despues vincule este.',
  40903: 'Este metodo ya esta vinculado a otra cuenta.',
  41001: 'Su codigo ha caducado. Solicite uno nuevo.',
  41002: 'Demasiados intentos. Solicite un codigo nuevo.',
  42901: 'Espere un momento antes de solicitar otro codigo.',
  42902: 'Demasiadas solicitudes. Intentelo mas tarde.',
  50000: 'Algo salio mal. Intentelo de nuevo.',
  50001: 'No se pudo completar la operacion. Intentelo de nuevo.',
  50002: 'No pudimos enviar el correo. Intentelo de nuevo.',
  50201: 'El proveedor de inicio de sesion no esta disponible. Use un codigo por correo.',
  50401: 'El proveedor de inicio de sesion agoto el tiempo. Use un codigo por correo.'
}

// [TRANSLATION_PENDING] FR — 占位翻译，待专业复核
const fr: CodeMessages = {
  40000: 'Verifiez les champs indiques et reessayez.',
  40001: 'Veuillez saisir une adresse e-mail valide.',
  40002: 'La valeur de configuration d authentification est hors plage autorisee.',
  40100: 'Vous n etes pas connecte. Veuillez vous reconnecter.',
  40101: 'Code incorrect. Veuillez reessayer.',
  40102: 'Votre session a expire. Veuillez vous reconnecter.',
  40300: 'Vous n avez pas l autorisation d effectuer cette action.',
  40301: 'Ce compte a ete desactive.',
  40303: 'Cette methode de connexion est indisponible.',
  40304: 'Votre e-mail principal ne peut pas etre supprime.',
  40305: 'Conservez au moins une methode de connexion.',
  40901: 'Cet e-mail est deja utilise.',
  40902: 'Connectez-vous avec votre methode d origine, puis liez celle-ci.',
  40903: 'Cette methode est deja liee a un autre compte.',
  41001: 'Votre code a expire. Veuillez en demander un nouveau.',
  41002: 'Trop de tentatives. Demandez un nouveau code.',
  42901: 'Patientez un instant avant de demander un autre code.',
  42902: 'Trop de requetes. Reessayez plus tard.',
  50000: 'Une erreur s est produite. Veuillez reessayer.',
  50001: 'L operation n a pas pu aboutir. Veuillez reessayer.',
  50002: 'Nous n avons pas pu envoyer l e-mail. Veuillez reessayer.',
  50201: 'Le fournisseur de connexion est indisponible. Utilisez un code par e-mail.',
  50401: 'Le fournisseur de connexion a expire. Utilisez un code par e-mail.'
}

const errorMessages: Record<Locale, CodeMessages> = { en, es, fr }

/** 按 locale + code 取本地化错误文案；未知 code 兜底 50000。 */
export function localizeError(locale: Locale, code: number): string {
  const table = errorMessages[locale] ?? errorMessages.en
  return table[code] ?? table[50000] ?? errorMessages.en[50000]
}
