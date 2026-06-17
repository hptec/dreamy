/**
 * UI 文案三语词典（FUNC-002 / 决策 9 / 决策 12）。
 * - en 为权威全集（UiMessages 全字段）；es/fr 为 DeepPartial 覆盖层。
 * - getMessages 用 deepMerge(en, override) 组装：缺失/[TRANSLATION_PENDING] 自动回退 EN（FUNC-003 / EDGE-020）。
 * - fabric 命名空间承载面料材质 + 层级名（决策 12，固定枚举，不入库）。
 */

import type { Locale } from '../api/types'

type DeepPartial<T> = {
  [K in keyof T]?: T[K] extends object ? DeepPartial<T[K]> : T[K]
}

export interface UiMessages {
  brand: string
  common: {
    loading: string
    cancel: string
    confirm: string
    retry: string
    signOut: string
    save: string
    search: string
    account: string
    cart: string
    wishlist: string
    close: string
    viewAll: string
    continueShopping: string
    back: string
    submit: string
    apply: string
    clear: string
    seeMore: string
    seeLess: string
  }
  layout: {
    header: {
      contact: string
      planning: string
      openMenu: string
      closeMenu: string
      searchAria: string
      wishlistAria: string
      accountAria: string
      cartAria: string
      currencyAria: string
      languageAria: string
      myAccount: string
      signInRegister: string
    }
    footer: {
      newsletterEyebrow: string
      newsletterTitle: string
      newsletterBody: string
      emailPlaceholder: string
      subscribe: string
      subscribed: string
      invalidEmail: string
      emailSrLabel: string
      weAccept: string
      rights: string
    }
    search: {
      placeholder: string
      popular: string
      noMatches: string
      viewAllResults: string
    }
  }
  collection: {
    filter: string
    sortBy: string
    sortNewest: string
    sortPriceAsc: string
    sortPriceDesc: string
    sortRecommended: string
    empty: string
    loadMore: string
    results: string
    clearFilters: string
  }
  product: {
    addToCart: string
    sizeGuide: string
    description: string
    designerNote: string
    reviews: string
    questions: string
    color: string
    size: string
    quantity: string
    selectSize: string
    selectColor: string
    outOfStock: string
    findMySize: string
    askQuestion: string
    customSize: string
    breadcrumbHome: string
    completeTheLook: string
    youMayAlsoLike: string
  }
  cart: {
    drawer: {
      title: string
      empty: string
      subtotal: string
      checkout: string
      viewFullBag: string
      installments: string
      remove: string
      decrease: string
      increase: string
      customSize: string
    }
  }
  checkout: {
    title: string
    shipping: string
    payment: string
    placeOrder: string
    orderSummary: string
  }
  fabric: {
    headingFabricCare: string
    headingComposition: string
    headingCare: string
    materials: {
      cotton: string
      lace: string
      satin: string
      silk: string
      tulle: string
      chiffon: string
      organza: string
      polyester: string
      crepe: string
      mikado: string
    }
    layers: {
      shell: string
      lining: string
      overlay: string
      trim: string
    }
  }
  cookieConsent: {
    body: string
    accept: string
    decline: string
  }
  empty: {
    generic: string
  }
  error: {
    generic: string
    notFoundTitle: string
    notFoundBody: string
    backHome: string
    shopDresses: string
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
    save: 'Save Changes',
    search: 'Search',
    account: 'Account',
    cart: 'Cart',
    wishlist: 'Wishlist',
    close: 'Close',
    viewAll: 'View all',
    continueShopping: 'Continue Shopping',
    back: 'Back',
    submit: 'Submit',
    apply: 'Apply',
    clear: 'Clear',
    seeMore: 'See more',
    seeLess: 'See less'
  },
  layout: {
    header: {
      contact: 'Contact',
      planning: 'Planning',
      openMenu: 'Open menu',
      closeMenu: 'Close menu',
      searchAria: 'Search',
      wishlistAria: 'Wishlist',
      accountAria: 'Account',
      cartAria: 'Cart',
      currencyAria: 'Currency',
      languageAria: 'Language',
      myAccount: 'My Account',
      signInRegister: 'Sign In / Register'
    },
    footer: {
      newsletterEyebrow: 'Join the Atelier',
      newsletterTitle: 'Be the first to know',
      newsletterBody: 'Sign up for early access to new collections and outdoor wedding inspiration.',
      emailPlaceholder: 'Your email address',
      subscribe: 'Subscribe',
      subscribed: "You're on the list — welcome to the atelier.",
      invalidEmail: 'Please enter a valid email address.',
      emailSrLabel: 'Email address',
      weAccept: 'We accept',
      rights: '© 2026 Dreamy Atelier.'
    },
    search: {
      placeholder: 'Search gowns, dresses, accessories...',
      popular: 'Popular Searches',
      noMatches: 'No matches. Try a color or silhouette.',
      viewAllResults: 'View all results →'
    }
  },
  collection: {
    filter: 'Filter',
    sortBy: 'Sort by',
    sortNewest: 'Newest',
    sortPriceAsc: 'Price: Low to High',
    sortPriceDesc: 'Price: High to Low',
    sortRecommended: 'Recommended',
    empty: 'No products found.',
    loadMore: 'Load more',
    results: 'results',
    clearFilters: 'Clear filters'
  },
  product: {
    addToCart: 'Add to Cart',
    sizeGuide: 'Size Guide',
    description: 'Description',
    designerNote: "Designer's Note",
    reviews: 'Reviews',
    questions: 'Questions',
    color: 'Color',
    size: 'Size',
    quantity: 'Quantity',
    selectSize: 'Select a size',
    selectColor: 'Select a color',
    outOfStock: 'Out of stock',
    findMySize: 'Find my size',
    askQuestion: 'Ask a question',
    customSize: 'Custom size',
    breadcrumbHome: 'Home',
    completeTheLook: 'Complete the Look',
    youMayAlsoLike: 'You may also like'
  },
  cart: {
    drawer: {
      title: 'Your Bag',
      empty: 'Your bag is empty.',
      subtotal: 'Subtotal',
      checkout: 'Checkout',
      viewFullBag: 'View full bag',
      installments: 'or 4 interest-free payments of {amount} with Klarna',
      remove: 'Remove',
      decrease: 'Decrease',
      increase: 'Increase',
      customSize: 'Custom size'
    }
  },
  checkout: {
    title: 'Checkout',
    shipping: 'Shipping',
    payment: 'Payment',
    placeOrder: 'Place Order',
    orderSummary: 'Order Summary'
  },
  fabric: {
    headingFabricCare: 'Fabric & Care',
    headingComposition: 'Composition',
    headingCare: 'Care Instructions',
    materials: {
      cotton: 'Cotton',
      lace: 'Lace',
      satin: 'Satin',
      silk: 'Silk',
      tulle: 'Tulle',
      chiffon: 'Chiffon',
      organza: 'Organza',
      polyester: 'Polyester',
      crepe: 'Crepe',
      mikado: 'Mikado'
    },
    layers: {
      shell: 'Shell',
      lining: 'Lining',
      overlay: 'Overlay',
      trim: 'Trim'
    }
  },
  cookieConsent: {
    body: 'We use analytics cookies to understand how you shop and to show you the gowns you’ll love. Choose “Accept” to allow them, or “Decline” and we won’t set any analytics cookies.',
    accept: 'Accept',
    decline: 'Decline'
  },
  empty: {
    generic: 'Nothing here yet.'
  },
  error: {
    generic: 'Something went wrong. Please try again.',
    notFoundTitle: 'This page wandered off',
    notFoundBody: "The page you're looking for doesn't exist or has moved. Let's get you back to the dresses.",
    backHome: 'Back Home',
    shopDresses: 'Shop Dresses'
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

// ES 覆盖层（DeepPartial）：未列出的键自动回退 EN（FUNC-003 / EDGE-020）。
const es: DeepPartial<UiMessages> = {
  brand: 'Dreamy',
  common: {
    loading: 'Cargando...',
    cancel: 'Cancelar',
    confirm: 'Confirmar',
    retry: 'Reintentar',
    signOut: 'Cerrar sesion',
    save: 'Guardar cambios',
    search: 'Buscar',
    account: 'Cuenta',
    cart: 'Carrito',
    wishlist: 'Favoritos',
    close: 'Cerrar',
    viewAll: 'Ver todo',
    continueShopping: 'Seguir comprando',
    back: 'Volver',
    submit: 'Enviar',
    apply: 'Aplicar',
    clear: 'Limpiar',
    seeMore: 'Ver mas',
    seeLess: 'Ver menos'
  },
  layout: {
    header: {
      contact: 'Contacto',
      planning: 'Planificacion',
      openMenu: 'Abrir menu',
      closeMenu: 'Cerrar menu',
      searchAria: 'Buscar',
      wishlistAria: 'Favoritos',
      accountAria: 'Cuenta',
      cartAria: 'Carrito',
      currencyAria: 'Moneda',
      languageAria: 'Idioma',
      myAccount: 'Mi cuenta',
      signInRegister: 'Iniciar sesion / Registrarse'
    },
    footer: {
      newsletterEyebrow: 'Unase al Atelier',
      newsletterTitle: 'Sea el primero en saberlo',
      newsletterBody: 'Registrese para acceso anticipado a nuevas colecciones e inspiracion de bodas al aire libre.',
      emailPlaceholder: 'Su correo electronico',
      subscribe: 'Suscribirse',
      subscribed: 'Esta en la lista — bienvenido al atelier.',
      invalidEmail: 'Introduzca un correo electronico valido.',
      emailSrLabel: 'Correo electronico',
      weAccept: 'Aceptamos',
      rights: '© 2026 Dreamy Atelier.'
    },
    search: {
      placeholder: 'Buscar vestidos, accesorios...',
      popular: 'Busquedas populares',
      noMatches: 'Sin resultados. Pruebe un color o silueta.',
      viewAllResults: 'Ver todos los resultados →'
    }
  },
  collection: {
    filter: 'Filtrar',
    sortBy: 'Ordenar por',
    sortNewest: 'Mas recientes',
    sortPriceAsc: 'Precio: de menor a mayor',
    sortPriceDesc: 'Precio: de mayor a menor',
    sortRecommended: 'Recomendados',
    empty: 'No se encontraron productos.',
    loadMore: 'Cargar mas',
    results: 'resultados',
    clearFilters: 'Limpiar filtros'
  },
  product: {
    addToCart: 'Anadir al carrito',
    sizeGuide: 'Guia de tallas',
    description: 'Descripcion',
    designerNote: 'Nota del disenador',
    reviews: 'Resenas',
    questions: 'Preguntas',
    color: 'Color',
    size: 'Talla',
    quantity: 'Cantidad',
    selectSize: 'Seleccione una talla',
    selectColor: 'Seleccione un color',
    outOfStock: 'Agotado',
    findMySize: 'Encontrar mi talla',
    askQuestion: 'Hacer una pregunta',
    customSize: 'Talla personalizada',
    breadcrumbHome: 'Inicio',
    completeTheLook: 'Completa el look',
    youMayAlsoLike: 'Tambien te puede gustar'
  },
  cart: {
    drawer: {
      title: 'Su bolsa',
      empty: 'Su bolsa esta vacia.',
      subtotal: 'Subtotal',
      checkout: 'Pagar',
      viewFullBag: 'Ver bolsa completa',
      installments: 'o 4 pagos sin intereses de {amount} con Klarna',
      remove: 'Eliminar',
      decrease: 'Disminuir',
      increase: 'Aumentar',
      customSize: 'Talla personalizada'
    }
  },
  checkout: {
    title: 'Pago',
    shipping: 'Envio',
    payment: 'Pago',
    placeOrder: 'Realizar pedido',
    orderSummary: 'Resumen del pedido'
  },
  fabric: {
    headingFabricCare: 'Tejido y cuidado',
    headingComposition: 'Composicion',
    headingCare: 'Instrucciones de cuidado',
    materials: {
      cotton: 'Algodon',
      lace: 'Encaje',
      satin: 'Raso',
      silk: 'Seda',
      tulle: 'Tul',
      chiffon: 'Gasa',
      organza: 'Organza',
      polyester: 'Poliester',
      crepe: 'Crepe',
      mikado: 'Mikado'
    },
    layers: {
      shell: 'Exterior',
      lining: 'Forro',
      overlay: 'Sobrecapa',
      trim: 'Ribete'
    }
  },
  cookieConsent: {
    body: 'Usamos cookies de analisis para entender como compra y mostrarle los vestidos que le encantaran. Elija “Aceptar” para permitirlas, o “Rechazar” y no estableceremos cookies de analisis.',
    accept: 'Aceptar',
    decline: 'Rechazar'
  },
  empty: {
    generic: 'Aun no hay nada aqui.'
  },
  error: {
    generic: 'Algo salio mal. Intentelo de nuevo.',
    notFoundTitle: 'Esta pagina se perdio',
    notFoundBody: 'La pagina que busca no existe o se ha movido. Volvamos a los vestidos.',
    backHome: 'Volver al inicio',
    shopDresses: 'Ver vestidos'
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

// FR 覆盖层（DeepPartial）：未列出的键自动回退 EN（FUNC-003 / EDGE-020）。
const fr: DeepPartial<UiMessages> = {
  brand: 'Dreamy',
  common: {
    loading: 'Chargement...',
    cancel: 'Annuler',
    confirm: 'Confirmer',
    retry: 'Reessayer',
    signOut: 'Se deconnecter',
    save: 'Enregistrer',
    search: 'Rechercher',
    account: 'Compte',
    cart: 'Panier',
    wishlist: 'Favoris',
    close: 'Fermer',
    viewAll: 'Tout voir',
    continueShopping: 'Continuer mes achats',
    back: 'Retour',
    submit: 'Envoyer',
    apply: 'Appliquer',
    clear: 'Effacer',
    seeMore: 'Voir plus',
    seeLess: 'Voir moins'
  },
  layout: {
    header: {
      contact: 'Contact',
      planning: 'Planification',
      openMenu: 'Ouvrir le menu',
      closeMenu: 'Fermer le menu',
      searchAria: 'Rechercher',
      wishlistAria: 'Favoris',
      accountAria: 'Compte',
      cartAria: 'Panier',
      currencyAria: 'Devise',
      languageAria: 'Langue',
      myAccount: 'Mon compte',
      signInRegister: 'Connexion / Inscription'
    },
    footer: {
      newsletterEyebrow: 'Rejoignez l Atelier',
      newsletterTitle: 'Soyez le premier informe',
      newsletterBody: 'Inscrivez-vous pour un acces anticipe aux nouvelles collections et a l inspiration de mariage en plein air.',
      emailPlaceholder: 'Votre adresse e-mail',
      subscribe: 'S abonner',
      subscribed: 'Vous etes sur la liste — bienvenue a l atelier.',
      invalidEmail: 'Saisissez une adresse e-mail valide.',
      emailSrLabel: 'Adresse e-mail',
      weAccept: 'Nous acceptons',
      rights: '© 2026 Dreamy Atelier.'
    },
    search: {
      placeholder: 'Rechercher robes, accessoires...',
      popular: 'Recherches populaires',
      noMatches: 'Aucun resultat. Essayez une couleur ou une silhouette.',
      viewAllResults: 'Voir tous les resultats →'
    }
  },
  collection: {
    filter: 'Filtrer',
    sortBy: 'Trier par',
    sortNewest: 'Plus recents',
    sortPriceAsc: 'Prix : croissant',
    sortPriceDesc: 'Prix : decroissant',
    sortRecommended: 'Recommandes',
    empty: 'Aucun produit trouve.',
    loadMore: 'Charger plus',
    results: 'resultats',
    clearFilters: 'Effacer les filtres'
  },
  product: {
    addToCart: 'Ajouter au panier',
    sizeGuide: 'Guide des tailles',
    description: 'Description',
    designerNote: 'Note du createur',
    reviews: 'Avis',
    questions: 'Questions',
    color: 'Couleur',
    size: 'Taille',
    quantity: 'Quantite',
    selectSize: 'Choisissez une taille',
    selectColor: 'Choisissez une couleur',
    outOfStock: 'Epuise',
    findMySize: 'Trouver ma taille',
    askQuestion: 'Poser une question',
    customSize: 'Taille sur mesure',
    breadcrumbHome: 'Accueil',
    completeTheLook: 'Completez le look',
    youMayAlsoLike: 'Vous aimerez aussi'
  },
  cart: {
    drawer: {
      title: 'Votre sac',
      empty: 'Votre sac est vide.',
      subtotal: 'Sous-total',
      checkout: 'Paiement',
      viewFullBag: 'Voir le sac complet',
      installments: 'ou 4 paiements sans frais de {amount} avec Klarna',
      remove: 'Retirer',
      decrease: 'Diminuer',
      increase: 'Augmenter',
      customSize: 'Taille sur mesure'
    }
  },
  checkout: {
    title: 'Paiement',
    shipping: 'Livraison',
    payment: 'Paiement',
    placeOrder: 'Passer la commande',
    orderSummary: 'Recapitulatif de la commande'
  },
  fabric: {
    headingFabricCare: 'Tissu et entretien',
    headingComposition: 'Composition',
    headingCare: 'Instructions d entretien',
    materials: {
      cotton: 'Coton',
      lace: 'Dentelle',
      satin: 'Satin',
      silk: 'Soie',
      tulle: 'Tulle',
      chiffon: 'Mousseline',
      organza: 'Organza',
      polyester: 'Polyester',
      crepe: 'Crepe',
      mikado: 'Mikado'
    },
    layers: {
      shell: 'Exterieur',
      lining: 'Doublure',
      overlay: 'Surcouche',
      trim: 'Garniture'
    }
  },
  cookieConsent: {
    body: 'Nous utilisons des cookies d analyse pour comprendre vos achats et vous montrer les robes que vous aimerez. Choisissez « Accepter » pour les autoriser, ou « Refuser » et nous ne placerons aucun cookie d analyse.',
    accept: 'Accepter',
    decline: 'Refuser'
  },
  empty: {
    generic: 'Rien ici pour le moment.'
  },
  error: {
    generic: 'Une erreur est survenue. Veuillez reessayer.',
    notFoundTitle: 'Cette page a disparu',
    notFoundBody: 'La page que vous cherchez n existe pas ou a ete deplacee. Retournons aux robes.',
    backHome: 'Retour a l accueil',
    shopDresses: 'Voir les robes'
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

/**
 * 深合并：以 en 为基底，override 逐键覆盖。
 * 空串 / [TRANSLATION_PENDING] 视为缺失 → 保留 EN 值（FUNC-003 / EDGE-020）。
 */
function deepMerge<T>(base: T, override: DeepPartial<T> | undefined): T {
  if (!override) return base
  const out: Record<string, unknown> = Array.isArray(base) ? [...(base as unknown[])] as unknown as Record<string, unknown> : { ...(base as Record<string, unknown>) }
  for (const key of Object.keys(override as Record<string, unknown>)) {
    const ov = (override as Record<string, unknown>)[key]
    const bv = (base as Record<string, unknown>)[key]
    if (ov === undefined || ov === null) continue
    if (typeof ov === 'object' && typeof bv === 'object' && bv !== null) {
      out[key] = deepMerge(bv, ov as DeepPartial<typeof bv>)
    } else if (typeof ov === 'string') {
      // 空串或待译占位 → 回退 EN
      out[key] = ov === '' || ov === '[TRANSLATION_PENDING]' ? bv : ov
    } else {
      out[key] = ov
    }
  }
  return out as T
}

// 组装后的完整词典（每个 locale 都是 UiMessages 全集，缺失键已回退 EN）
const dictionaries: Record<Locale, UiMessages> = {
  en,
  es: deepMerge(en, es),
  fr: deepMerge(en, fr)
}

export function getMessages(locale: Locale): UiMessages {
  return dictionaries[locale] ?? dictionaries.en
}



