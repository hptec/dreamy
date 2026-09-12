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
    sortFeatured: string
    empty: string
    loadMore: string
    results: string
    clearFilters: string
    eyebrow: string
    styleOne: string
    stylesMany: string
    clearAll: string
    all: string
    loadFailedTitle: string
    loadFailedBody: string
    noMatchTitle: string
    noMatchBody: string
    previous: string
    next: string
    pageOf: string
    showResults: string
    price: string
    priceUnder200: string
    price200to500: string
    price500to1000: string
    priceOver1000: string
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
    /** 交期口径统一（≥14 天周数 / <14 天天数），PDP 交期行 */
    leadTimeWeeks: string
    leadTimeDays: string
    breadcrumbHome: string
    completeTheLook: string
    youMayAlsoLike: string
    qa: string
    reviewCount: string
    writeReview: string
    beFirstToReview: string
    shareExperience: string
    featuredReview: string
    loadMoreReviews: string
    loadMoreQuestions: string
    noQuestionsYet: string
    askAnything: string
    askedBy: string
    asked: string
    reviewSubmitted: string
    questionSubmitted: string
    sortReviewsAria: string
    customerPhoto: string
    defaultCustomer: string
    reviewSort: {
      featured: string
      newest: string
      highest: string
      lowest: string
    }
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
    page: {
      title: string
      emptyTitle: string
      emptyBody: string
      shopDresses: string
      bestSellers: string
      mergedNotice: string
      dyeLotNotice: string
      sizeLabel: string
      bust: string
      waist: string
      hips: string
      hollowToFloor: string
      unavailable: string
      saveForLater: string
      subtotal: string
      shipping: string
      calculatedAtCheckout: string
      estimatedTotal: string
      promoNote: string
      checkoutCta: string
      freeShipping: string
      secureCheckout: string
    }
  }
  checkout: {
    title: string
    shipping: string
    payment: string
    placeOrder: string
    orderSummary: string
    stepAddress: string
    stepReview: string
    shippingAddress: string
    loadingAddresses: string
    defaultBadge: string
    addNewAddress: string
    selectAddressError: string
    continueToShipping: string
    shippingMethod: string
    free: string
    giftWrapping: string
    weddingDate: string
    leadTimeWarning: string
    promoCode: string
    couponApplied: string
    removeCoupon: string
    continueToPayment: string
    cardName: string
    cardDesc: string
    comingSoon: string
    payAppleDesc: string
    payGoogleDesc: string
    payIn4: string
    stripeNote: string
    payIn4Note: string
    reviewOrder: string
    reviewTitle: string
    custom: string
    qty: string
    shipTo: string
    dduNote: string
    adjustQuantities: string
    placingOrder: string
    summary: string
    subtotal: string
    giftWrappingLabel: string
    discount: string
    total: string
    calculatedAtShipping: string
    fillRequired: string
    fullName: string
    phoneOptional: string
    addressLine: string
    city: string
    state: string
    zip: string
    country: string
    setDefault: string
    saving: string
    saveAddress: string
    // order-flow-complete：国家/州省下拉、服务等级、运输天数、ETA、税费、关税、锁汇
    region: string
    loadingCountries: string
    countryNotSupported: string
    standard: string
    express: string
    transitDays: string
    eta: string
    etaShort: string
    productionDays: string
    tax: string
    taxDetails: string
    taxIncluded: string
    dutiesNotice: string
    rateLocked: string
  }
  paymentPanel: {
    testModeTitle: string
    testModeBody: string
    continueLabel: string
    processing: string
    pay: string
    failed: string
    stubUnavailable: string
    alreadyPaid: string
    misconfigured: string
  }
  orderSuccess: {
    confirmingTitle: string
    confirmingBody: string
    paidTitle: string
    paidBody: string
    pendingTitle: string
    pendingBody: string
    notFoundTitle: string
    notFoundBody: string
    orderNumber: string
    total: string
    trackingNote: string
    etaNote: string
    trackOrder: string
    viewOrder: string
    retryPayment: string
    loading: string
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
    care: {
      handWashCold: string
      machineWash30: string
      doNotWash: string
      doNotBleach: string
      bleachOk: string
      tumbleDryLow: string
      lineDry: string
      doNotTumbleDry: string
      ironLow: string
      steamOnly: string
      doNotIron: string
      dryCleanOnly: string
      doNotDryClean: string
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
  blog: {
    title: string
    eyebrow: string
    description: string
    empty: string
    backToBlog: string
    keepReading: string
    previous: string
    next: string
    pageOf: string
    notFound: string
  }
  guide: {
    eyebrow: string
    title: string
    description: string
    empty: string
    startWithDress: string
    checklist: string
    complete: string
    savedOnDevice: string
    saveAcrossDevices: string
  }
  inspiration: {
    metaTitle: string
    metaDescription: string
    heroEyebrow: string
    heroTitle: string
    editsEyebrow: string
    editsTitle: string
    empty: string
    paletteEyebrow: string
    paletteTitle: string
    paletteDescription: string
    paletteCta: string
    weddingsEyebrow: string
    weddingsTitle: string
    shopEdit: string
    noStyles: string
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
    nav: {
      dashboard: string
      orders: string
      addresses: string
      wishlist: string
      myReviews: string
      weddingPlans: string
      showrooms: string
      security: string
      settings: string
    }
    weddingPlans: {
      title: string
      subtitle: string
      empty: string
    }
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
  unsubscribe: {
    title: string
    body: string
    confirm: string
    confirming: string
    successTitle: string
    successBody: string
    invalidTitle: string
    invalidBody: string
    errorTitle: string
    errorBody: string
    retry: string
  }
  orders: {
    title: string
    all: string
    orderNo: string
    placed: string
    itemsCount: string
    details: string
    none: string
    noneFiltered: string
    status: {
      pending: string
      paid: string
      shipped: string
      completed: string
      cancelled: string
      refunding: string
      refunded: string
      delivered: string
    }
    paymentStatus: {
      created: string
      processing: string
      succeeded: string
      failed: string
      refunded: string
      partiallyRefunded: string
    }
    refundStatus: {
      pending: string
      approved: string
      rejected: string
    }
    productionStage: {
      pendingReview: string
      inProduction: string
      qualityCheck: string
      readyToShip: string
    }
    shipmentStatus: {
      pending: string
      inTransit: string
      outForDelivery: string
      delivered: string
      exception: string
      cancelled: string
    }
    card: {
      eta: string
      packages: string
      tracking: string
    }
    detail: {
      somethingWrong: string
      notFound: string
      backToOrders: string
      payNow: string
      cancelOrder: string
      cancelConfirm: string
      cancelling: string
      yesCancel: string
      keepOrder: string
      requestRefund: string
      refundNo: string
      items: string
      statusLabel: string
      paidAt: string
      timelinePlaced: string
      timelinePaid: string
      timelineShipped: string
      timelineCompleted: string
      timelineProduction: string
      timelineDelivered: string
      productionTitle: string
      productionBody: string
      eta: string
      tax: string
      refunded: string
      shipments: string
      shipmentNo: string
      trackPackage: string
      contents: string
      trackingHistory: string
      noTrackingEvents: string
      activity: string
      confirmDelivery: string
      confirmDeliveryQuestion: string
      confirmDeliveryYes: string
      confirmDeliveryNo: string
      confirmingDelivery: string
      buyAgain: string
      buyAgainBusy: string
      buyAgainAdded: string
      buyAgainSkipped: string
      viewCart: string
      countdown: string
      countdownExpired: string
      refundTitle: string
      refundBody: string
      refundReason: string
      refundReasonError: string
      refundWindowEnded: string
      refundSubmitting: string
      refundSubmit: string
    }
  }
  trackOrder: {
    eyebrow: string
    title: string
    body: string
    orderNo: string
    orderNoPlaceholder: string
    email: string
    emailPlaceholder: string
    submit: string
    searching: string
    invalid: string
    notFound: string
    rateLimited: string
    orderLabel: string
    placed: string
    shipTo: string
    eta: string
    packages: string
    activity: string
    searchAgain: string
    signInHint: string
    signIn: string
  }
  wishlist: {
    title: string
    emptyBody: string
    startBrowsing: string
    moveToBag: string
    recentlyViewed: string
    madeToMeasure: string
    openProduct: string
    moving: string
  }
  searchPage: {
    placeholder: string
    trySearching: string
    somethingWrong: string
    noResultsTitle: string
    noResultsBody: string
    browseDresses: string
    resultFor: string
    resultsFor: string
  }
  flashSale: {
    eyebrow: string
    endsIn: string
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
    sortFeatured: 'Featured',
    empty: 'No products found.',
    loadMore: 'Load more',
    results: 'results',
    clearFilters: 'Clear filters',
    eyebrow: 'Dreamy Collection',
    styleOne: 'style',
    stylesMany: 'styles',
    clearAll: 'Clear all',
    all: 'All',
    loadFailedTitle: "We couldn't load this collection",
    loadFailedBody: 'Please check your connection and try again.',
    noMatchTitle: 'No styles match your filters',
    noMatchBody: 'Try removing a filter or exploring another color.',
    previous: 'Previous',
    next: 'Next',
    pageOf: 'Page {page} of {total}',
    showResults: 'Show {count}',
    price: 'Price',
    priceUnder200: 'Under $200',
    price200to500: '$200 – $500',
    price500to1000: '$500 – $1,000',
    priceOver1000: '$1,000 & up'
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
    leadTimeWeeks: 'Handcrafted to order · ships in ~{weeks} weeks',
    leadTimeDays: 'Handcrafted to order · ships in ~{days} days',
    breadcrumbHome: 'Home',
    completeTheLook: 'Complete the Look',
    youMayAlsoLike: 'You may also like',
    qa: 'Q&A',
    reviewCount: '{count} reviews',
    writeReview: 'Write a Review',
    beFirstToReview: 'Be the first to review',
    shareExperience: 'Share your experience with other brides.',
    featuredReview: 'Featured review',
    loadMoreReviews: 'Load more reviews',
    loadMoreQuestions: 'Load more questions',
    noQuestionsYet: 'No questions yet',
    askAnything: 'Ask us anything about fit, fabric, or delivery.',
    askedBy: 'Asked by {name}',
    asked: 'Asked',
    reviewSubmitted: 'Your review has been submitted and will appear after moderation.',
    questionSubmitted: 'Your question has been submitted — the answer will appear here once published.',
    sortReviewsAria: 'Sort reviews',
    customerPhoto: 'Customer photo',
    defaultCustomer: 'Dreamy Customer',
    reviewSort: {
      featured: 'Featured',
      newest: 'Newest',
      highest: 'Highest rated',
      lowest: 'Lowest rated'
    }
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
    },
    page: {
      title: 'Your Bag',
      emptyTitle: 'Your bag is empty',
      emptyBody: "Looks like you haven't added anything yet. Let's find the one.",
      shopDresses: 'Shop Wedding Dresses',
      bestSellers: 'Best Sellers',
      mergedNotice: 'Some quantities were adjusted to match available stock when we merged your bag.',
      dyeLotNotice: 'Order within 24h of your bridal party to guarantee the same dye lot for this style.',
      sizeLabel: 'Size {size}',
      bust: 'Bust',
      waist: 'Waist',
      hips: 'Hips',
      hollowToFloor: 'Hollow-to-floor',
      unavailable: 'No longer available',
      saveForLater: 'Save for later',
      subtotal: 'Subtotal',
      shipping: 'Shipping',
      calculatedAtCheckout: 'Calculated at checkout',
      estimatedTotal: 'Estimated Total',
      promoNote: 'Promo codes can be applied at checkout.',
      checkoutCta: 'Proceed to Checkout',
      freeShipping: 'Free shipping over $200',
      secureCheckout: 'Secure checkout'
    }
  },
  checkout: {
    title: 'Checkout',
    shipping: 'Shipping',
    payment: 'Payment',
    placeOrder: 'Place Order',
    orderSummary: 'Order Summary',
    stepAddress: 'Address',
    stepReview: 'Review',
    shippingAddress: 'Shipping Address',
    loadingAddresses: 'Loading addresses…',
    defaultBadge: 'Default',
    addNewAddress: 'Add new address',
    selectAddressError: 'Please select or add a shipping address.',
    continueToShipping: 'Continue to Shipping',
    shippingMethod: 'Shipping Method',
    free: 'Free',
    giftWrapping: 'Add gift wrapping',
    weddingDate: 'Wedding date (optional)',
    leadTimeWarning: 'Heads up — production for this order may take up to {days} days, which is close to your wedding date. Consider rush options or contact a stylist.',
    promoCode: 'Promo code',
    couponApplied: '{code} applied',
    removeCoupon: 'Remove',
    continueToPayment: 'Continue to Payment',
    cardName: 'Credit / Debit Card',
    cardDesc: 'Visa, Mastercard, Amex',
    comingSoon: 'Coming soon',
    payAppleDesc: 'Fast checkout with Face ID',
    payGoogleDesc: 'Pay with Google',
    payIn4: 'Pay in 4 interest-free',
    stripeNote: "You'll enter your payment details securely on the next step — powered by Stripe.",
    payIn4Note: '4 interest-free payments of {amount}. You\'ll be redirected to {provider} to complete.',
    reviewOrder: 'Review Order',
    reviewTitle: 'Review Your Order',
    custom: 'Custom',
    qty: 'Qty {count}',
    shipTo: 'Ship to',
    dduNote: 'International orders are shipped DDU (Delivered Duty Unpaid) — import duties and taxes, where applicable, are collected by the carrier on delivery.',
    adjustQuantities: 'Adjust quantities',
    placingOrder: 'Placing order…',
    summary: 'Summary',
    subtotal: 'Subtotal',
    giftWrappingLabel: 'Gift Wrapping',
    discount: 'Discount',
    total: 'Total',
    calculatedAtShipping: 'Calculated at shipping step',
    fillRequired: 'Please fill in all required fields.',
    fullName: 'Full name',
    phoneOptional: 'Phone (optional)',
    addressLine: 'Address',
    city: 'City',
    state: 'State',
    zip: 'ZIP',
    country: 'Country',
    setDefault: 'Set as default address',
    saving: 'Saving…',
    saveAddress: 'Save Address',
    region: 'State / Province',
    loadingCountries: 'Loading countries…',
    countryNotSupported: 'We do not ship to this destination yet.',
    standard: 'Standard',
    express: 'Express',
    transitDays: '{min}–{max} business days in transit',
    eta: 'Estimated delivery {from} – {to}',
    etaShort: 'Est. delivery {from} – {to}',
    productionDays: 'Made to order in about {days} days',
    tax: 'Tax',
    taxDetails: 'Tax details',
    taxIncluded: 'Duties & taxes included — no extra charges on delivery.',
    dutiesNotice: 'International orders are shipped DDU (Delivered Duty Unpaid) — import duties and taxes, where applicable, are collected by the carrier on delivery.',
    rateLocked: 'Rate locked at checkout: 1 USD = {rate} {currency}'
  },
  paymentPanel: {
    testModeTitle: 'Payment (test mode)',
    testModeBody: 'The payment service is running in test mode — no real card is required. Click Continue to confirm your order.',
    continueLabel: 'Continue',
    processing: 'Processing…',
    pay: 'Pay {amount}',
    failed: 'Payment failed. Please try again.',
    stubUnavailable: 'Test-mode payment is not available on this server. Please pay with your card instead.',
    alreadyPaid: 'This order has already been paid.',
    misconfigured: 'Payment is temporarily unavailable: the card form is not configured. Please contact support.'
  },
  orderSuccess: {
    confirmingTitle: 'Confirming your payment…',
    confirmingBody: "This usually takes just a few seconds. Please don't close this page.",
    paidTitle: 'Thank you!',
    paidBody: "Your order is confirmed. We've sent a confirmation to your email with all the details.",
    pendingTitle: 'Payment is being confirmed',
    pendingBody: "Your payment is still processing — this can take a little longer with Klarna or Afterpay. We'll email you as soon as it's confirmed. If you closed the payment window, you can retry from your order.",
    notFoundTitle: 'Order not found',
    notFoundBody: "We couldn't locate this order. Check your order history for the latest status.",
    orderNumber: 'Order Number',
    total: 'Total:',
    trackingNote: 'A tracking number will be emailed once your order ships.',
    etaNote: 'Estimated delivery {from} – {to}.',
    trackOrder: 'Track My Order',
    viewOrder: 'View My Order',
    retryPayment: 'Retry payment',
    loading: 'Loading…'
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
    },
    care: {
      handWashCold: 'Hand wash cold',
      machineWash30: 'Machine wash 30°C',
      doNotWash: 'Do not wash',
      doNotBleach: 'Do not bleach',
      bleachOk: 'Bleach when needed',
      tumbleDryLow: 'Tumble dry low',
      lineDry: 'Line dry',
      doNotTumbleDry: 'Do not tumble dry',
      ironLow: 'Iron low heat',
      steamOnly: 'Steam only',
      doNotIron: 'Do not iron',
      dryCleanOnly: 'Dry clean only',
      doNotDryClean: 'Do not dry clean'
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
  blog: {
    title: 'The Journal',
    eyebrow: 'Dreamy Atelier',
    description: 'Planning tips, fabric guides, and outdoor wedding inspiration.',
    empty: 'New stories are on the way — check back soon.',
    backToBlog: '← Back to blog',
    keepReading: 'Keep reading',
    previous: 'Previous',
    next: 'Next',
    pageOf: 'Page {page} of {total}',
    notFound: 'Post Not Found'
  },
  guide: {
    eyebrow: 'Plan with us',
    title: 'Your Wedding Wardrobe Timeline',
    description: "From the first daydream to the final fitting — here's exactly when to tackle each part of your outdoor wedding look.",
    empty: 'Planning guides are on the way — check back soon.',
    startWithDress: 'Start with the Dress',
    checklist: 'Checklist',
    complete: 'complete',
    savedOnDevice: 'Saved on this device',
    saveAcrossDevices: 'Sign in to save across devices'
  },
  inspiration: {
    metaTitle: 'Wedding Inspiration & Lookbook',
    metaDescription: 'Outdoor wedding inspiration, lookbooks, and color palettes to bring your vision to life.',
    heroEyebrow: 'Lookbook',
    heroTitle: 'Wedding Inspiration',
    editsEyebrow: 'Curated edits',
    editsTitle: 'Explore by mood',
    empty: 'Lookbooks are being curated — check back soon.',
    paletteEyebrow: 'Free tool',
    paletteTitle: 'Build your moodboard',
    paletteDescription: 'Order fabric swatches to see your wedding colors in person — on us.',
    paletteCta: 'Shop Bridesmaid Colors',
    weddingsEyebrow: 'Real love stories',
    weddingsTitle: 'Real Dreamy Weddings',
    shopEdit: 'Shop the {title} edit',
    noStyles: 'No styles linked to this lookbook yet.'
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
    notProvided: 'Not provided',
    nav: {
      dashboard: 'Dashboard',
      orders: 'Orders',
      addresses: 'Addresses',
      wishlist: 'Wishlist',
      myReviews: 'My Reviews',
      weddingPlans: 'Wedding Plans',
      showrooms: 'Showrooms',
      security: 'Login & Security',
      settings: 'Settings'
    },
    weddingPlans: {
      title: 'Wedding Plans',
      subtitle: 'Your saved wedding guide progress.',
      empty: 'No guide plans yet — start with a timeline.'
    }
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
  },
  unsubscribe: {
    title: 'Unsubscribe from our newsletter',
    body: 'You are about to stop receiving Dreamy news and private offers. You can subscribe again anytime.',
    confirm: 'Confirm unsubscribe',
    confirming: 'Unsubscribing…',
    successTitle: 'You have been unsubscribed',
    successBody: 'You will no longer receive our newsletter. We are sorry to see you go.',
    invalidTitle: 'This link is no longer valid',
    invalidBody: 'The unsubscribe link is invalid or has expired. Please use the link from our latest email.',
    errorTitle: 'Something went wrong',
    errorBody: 'We could not process your request right now. Please try again.',
    retry: 'Try again'
  },
  orders: {
    title: 'My Orders',
    all: 'All',
    orderNo: 'Order {no}',
    placed: 'Placed {date}',
    itemsCount: '{count} items',
    details: 'Details',
    none: 'No orders yet.',
    noneFiltered: 'No {status} orders.',
    status: {
      pending: 'Pending',
      paid: 'Paid',
      shipped: 'Shipped',
      completed: 'Completed',
      cancelled: 'Cancelled',
      refunding: 'Refunding',
      refunded: 'Refunded',
      delivered: 'Delivered'
    },
    paymentStatus: {
      created: 'Created',
      processing: 'Processing',
      succeeded: 'Succeeded',
      failed: 'Failed',
      refunded: 'Refunded',
      partiallyRefunded: 'Partially refunded'
    },
    refundStatus: {
      pending: 'Pending',
      approved: 'Approved',
      rejected: 'Rejected'
    },
    productionStage: {
      pendingReview: 'Pending review',
      inProduction: 'In production',
      qualityCheck: 'Quality check',
      readyToShip: 'Ready to ship'
    },
    shipmentStatus: {
      pending: 'Awaiting pickup',
      inTransit: 'In transit',
      outForDelivery: 'Out for delivery',
      delivered: 'Delivered',
      exception: 'Exception',
      cancelled: 'Cancelled'
    },
    card: {
      eta: 'Est. delivery {from} – {to}',
      packages: '{count} packages',
      tracking: 'Tracking {no}'
    },
    detail: {
      somethingWrong: 'Something went wrong',
      notFound: 'Order not found',
      backToOrders: 'Back to orders',
      payNow: 'Pay now',
      cancelOrder: 'Cancel order',
      cancelConfirm: 'Cancel this order?',
      cancelling: 'Cancelling…',
      yesCancel: 'Yes, cancel',
      keepOrder: 'Keep order',
      requestRefund: 'Request refund',
      refundNo: 'Refund {no}',
      items: 'Items',
      statusLabel: 'Status:',
      paidAt: 'Paid {date}',
      timelinePlaced: 'Placed',
      timelinePaid: 'Paid',
      timelineShipped: 'Shipped',
      timelineCompleted: 'Completed',
      timelineProduction: 'In production',
      timelineDelivered: 'Delivered',
      productionTitle: 'Production progress',
      productionBody: 'Your gown is being made to order. We will update this as it moves through our atelier.',
      eta: 'Estimated delivery {from} – {to}',
      tax: 'Tax',
      refunded: 'Refunded',
      shipments: 'Shipments',
      shipmentNo: 'Package {no}',
      trackPackage: 'Track package',
      contents: 'Contents',
      trackingHistory: 'Tracking history',
      noTrackingEvents: 'No tracking updates yet.',
      activity: 'Order activity',
      confirmDelivery: 'Confirm delivery',
      confirmDeliveryQuestion: 'Have you received all items in this order?',
      confirmDeliveryYes: 'Yes, received',
      confirmDeliveryNo: 'Not yet',
      confirmingDelivery: 'Confirming…',
      buyAgain: 'Buy again',
      buyAgainBusy: 'Adding…',
      buyAgainAdded: '{count} items added to your cart.',
      buyAgainSkipped: '{count} items could not be added (out of stock or unavailable).',
      viewCart: 'View cart',
      countdown: 'Complete payment within {time}',
      countdownExpired: 'The payment window has expired.',
      refundTitle: 'Request a Refund',
      refundBody: "Tell us why you'd like a refund and our team will review your request.",
      refundReason: 'Reason',
      refundReasonError: 'Please tell us briefly why you want a refund (max 255 characters).',
      refundWindowEnded: 'Refund window ended {date}.',
      refundSubmitting: 'Submitting…',
      refundSubmit: 'Submit Request'
    }
  },
  trackOrder: {
    eyebrow: 'Order Status',
    title: 'Track Your Order',
    body: 'Enter your order number and the email used at checkout to see the latest status, production progress and tracking.',
    orderNo: 'Order number',
    orderNoPlaceholder: 'DRM-20260101-0001',
    email: 'Email',
    emailPlaceholder: 'you@example.com',
    submit: 'Track order',
    searching: 'Searching…',
    invalid: 'Please enter your order number and a valid email address.',
    notFound: "We couldn't find an order matching that number and email.",
    rateLimited: 'Too many lookups. Please wait a while and try again.',
    orderLabel: 'Order {no}',
    placed: 'Placed {date}',
    shipTo: 'Ship to',
    eta: 'Estimated delivery {from} – {to}',
    packages: 'Packages',
    activity: 'Order activity',
    searchAgain: 'Track another order',
    signInHint: 'Have an account?',
    signIn: 'Sign in to see full order details'
  },
  wishlist: {
    title: 'My Wishlist',
    emptyBody: 'Your wishlist is empty. Tap the heart on any style to save it here.',
    startBrowsing: 'Start Browsing',
    moveToBag: 'Move to bag',
    recentlyViewed: 'Recently Viewed',
    madeToMeasure: 'This style is made-to-measure — add your measurements on the product page.',
    openProduct: 'Open Product Page',
    moving: 'Moving…'
  },
  searchPage: {
    placeholder: 'Search gowns, colors, occasions...',
    trySearching: 'Try searching for',
    somethingWrong: 'Something went wrong',
    noResultsTitle: 'No results for “{q}”',
    noResultsBody: 'Try a color, silhouette, or occasion — or browse our collections.',
    browseDresses: 'Browse Dresses',
    resultFor: '{count} result for “{q}”',
    resultsFor: '{count} results for “{q}”'
  },
  flashSale: {
    eyebrow: 'Flash Sale',
    endsIn: 'Ends in {time}'
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
    sortFeatured: 'Destacados',
    empty: 'No se encontraron productos.',
    loadMore: 'Cargar mas',
    results: 'resultados',
    clearFilters: 'Limpiar filtros',
    eyebrow: 'Coleccion Dreamy',
    styleOne: 'estilo',
    stylesMany: 'estilos',
    clearAll: 'Limpiar todo',
    all: 'Todo',
    loadFailedTitle: 'No pudimos cargar esta coleccion',
    loadFailedBody: 'Compruebe su conexion e intentelo de nuevo.',
    noMatchTitle: 'Ningun estilo coincide con sus filtros',
    noMatchBody: 'Pruebe a quitar un filtro o explorar otro color.',
    previous: 'Anterior',
    next: 'Siguiente',
    pageOf: 'Pagina {page} de {total}',
    showResults: 'Ver {count}',
    price: 'Precio',
    priceUnder200: 'Menos de $200',
    price200to500: '$200 – $500',
    price500to1000: '$500 – $1,000',
    priceOver1000: 'Mas de $1,000'
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
    leadTimeWeeks: 'Hecho a mano por encargo · envio en ~{weeks} semanas',
    leadTimeDays: 'Hecho a mano por encargo · envio en ~{days} dias',
    breadcrumbHome: 'Inicio',
    completeTheLook: 'Completa el look',
    youMayAlsoLike: 'Tambien te puede gustar',
    qa: 'Preguntas y respuestas',
    reviewCount: '{count} resenas',
    writeReview: 'Escribir una resena',
    beFirstToReview: 'Sea la primera en opinar',
    shareExperience: 'Comparta su experiencia con otras novias.',
    featuredReview: 'Resena destacada',
    loadMoreReviews: 'Cargar mas resenas',
    loadMoreQuestions: 'Cargar mas preguntas',
    noQuestionsYet: 'Aun no hay preguntas',
    askAnything: 'Pregunte lo que quiera sobre ajuste, tela o entrega.',
    askedBy: 'Pregunto {name}',
    asked: 'Pregunta realizada',
    reviewSubmitted: 'Su resena ha sido enviada y aparecera tras la moderacion.',
    questionSubmitted: 'Su pregunta ha sido enviada; la respuesta aparecera aqui cuando se publique.',
    sortReviewsAria: 'Ordenar resenas',
    customerPhoto: 'Foto del cliente',
    defaultCustomer: 'Cliente Dreamy',
    reviewSort: {
      featured: 'Destacadas',
      newest: 'Mas recientes',
      highest: 'Mejor valoradas',
      lowest: 'Menos valoradas'
    }
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
    },
    page: {
      title: 'Su bolsa',
      emptyTitle: 'Su bolsa esta vacia',
      emptyBody: 'Parece que aun no ha anadido nada. Vamos a encontrar el indicado.',
      shopDresses: 'Ver vestidos de novia',
      bestSellers: 'Mas vendidos',
      mergedNotice: 'Al fusionar su bolsa, algunas cantidades se ajustaron al stock disponible.',
      dyeLotNotice: 'Pida dentro de las 24h junto a su grupo nupcial para garantizar el mismo lote de tinte en este estilo.',
      sizeLabel: 'Talla {size}',
      bust: 'Busto',
      waist: 'Cintura',
      hips: 'Caderas',
      hollowToFloor: 'De hueco a suelo',
      unavailable: 'Ya no disponible',
      saveForLater: 'Guardar para despues',
      subtotal: 'Subtotal',
      shipping: 'Envio',
      calculatedAtCheckout: 'Se calcula al pagar',
      estimatedTotal: 'Total estimado',
      promoNote: 'Los codigos promocionales se pueden aplicar al pagar.',
      checkoutCta: 'Ir a pagar',
      freeShipping: 'Envio gratis desde $200',
      secureCheckout: 'Pago seguro'
    }
  },
  checkout: {
    title: 'Pago',
    shipping: 'Envio',
    payment: 'Pago',
    placeOrder: 'Realizar pedido',
    orderSummary: 'Resumen del pedido',
    stepAddress: 'Direccion',
    stepReview: 'Revision',
    shippingAddress: 'Direccion de envio',
    loadingAddresses: 'Cargando direcciones…',
    defaultBadge: 'Predeterminada',
    addNewAddress: 'Anadir nueva direccion',
    selectAddressError: 'Seleccione o anada una direccion de envio.',
    continueToShipping: 'Continuar al envio',
    shippingMethod: 'Metodo de envio',
    free: 'Gratis',
    giftWrapping: 'Anadir envoltorio de regalo',
    weddingDate: 'Fecha de la boda (opcional)',
    leadTimeWarning: 'Atencion: la produccion de este pedido puede tardar hasta {days} dias, cerca de su fecha de boda. Considere opciones urgentes o contacte a un asesor.',
    promoCode: 'Codigo promocional',
    couponApplied: '{code} aplicado',
    removeCoupon: 'Quitar',
    continueToPayment: 'Continuar al pago',
    cardName: 'Tarjeta de credito / debito',
    cardDesc: 'Visa, Mastercard, Amex',
    comingSoon: 'Proximamente',
    payAppleDesc: 'Pago rapido con Face ID',
    payGoogleDesc: 'Pague con Google',
    payIn4: 'Pague en 4 sin intereses',
    stripeNote: 'Introducira sus datos de pago de forma segura en el siguiente paso, con Stripe.',
    payIn4Note: '4 pagos sin intereses de {amount}. Sera redirigido a {provider} para completar.',
    reviewOrder: 'Revisar pedido',
    reviewTitle: 'Revise su pedido',
    custom: 'Personalizado',
    qty: 'Cant. {count}',
    shipTo: 'Enviar a',
    dduNote: 'Los pedidos internacionales se envian DDU (entrega con derechos no pagados): los aranceles e impuestos, cuando apliquen, los cobra el transportista en la entrega.',
    adjustQuantities: 'Ajustar cantidades',
    placingOrder: 'Realizando pedido…',
    summary: 'Resumen',
    subtotal: 'Subtotal',
    giftWrappingLabel: 'Envoltorio de regalo',
    discount: 'Descuento',
    total: 'Total',
    calculatedAtShipping: 'Se calcula en el paso de envio',
    fillRequired: 'Complete todos los campos obligatorios.',
    fullName: 'Nombre completo',
    phoneOptional: 'Telefono (opcional)',
    addressLine: 'Direccion',
    city: 'Ciudad',
    state: 'Estado / Provincia',
    zip: 'Codigo postal',
    country: 'Pais',
    setDefault: 'Establecer como direccion predeterminada',
    saving: 'Guardando…',
    saveAddress: 'Guardar direccion',
    region: 'Estado / Provincia',
    loadingCountries: 'Cargando paises…',
    countryNotSupported: 'Aun no enviamos a este destino.',
    standard: 'Estandar',
    express: 'Express',
    transitDays: '{min}–{max} dias habiles en transito',
    eta: 'Entrega estimada {from} – {to}',
    etaShort: 'Entrega est. {from} – {to}',
    productionDays: 'Hecho a medida en unos {days} dias',
    tax: 'Impuestos',
    taxDetails: 'Detalle de impuestos',
    taxIncluded: 'Aranceles e impuestos incluidos: sin cargos adicionales en la entrega.',
    dutiesNotice: 'Los pedidos internacionales se envian DDU (entrega con derechos no pagados): los aranceles e impuestos, cuando apliquen, los cobra el transportista en la entrega.',
    rateLocked: 'Tipo de cambio fijado al pagar: 1 USD = {rate} {currency}'
  },
  paymentPanel: {
    testModeTitle: 'Pago (modo de prueba)',
    testModeBody: 'El servicio de pago esta en modo de prueba: no se necesita una tarjeta real. Pulse Continuar para confirmar su pedido.',
    continueLabel: 'Continuar',
    processing: 'Procesando…',
    pay: 'Pagar {amount}',
    failed: 'El pago fallo. Intentelo de nuevo.',
    stubUnavailable: 'El pago en modo de prueba no esta disponible en este servidor. Pague con su tarjeta.',
    alreadyPaid: 'Este pedido ya esta pagado.',
    misconfigured: 'El pago no esta disponible temporalmente: el formulario de tarjeta no esta configurado. Contacte con soporte.'
  },
  orderSuccess: {
    confirmingTitle: 'Confirmando su pago…',
    confirmingBody: 'Suele tardar solo unos segundos. No cierre esta pagina.',
    paidTitle: 'Gracias!',
    paidBody: 'Su pedido esta confirmado. Le hemos enviado un correo con todos los detalles.',
    pendingTitle: 'El pago se esta confirmando',
    pendingBody: 'Su pago sigue en proceso; con Klarna o Afterpay puede tardar un poco mas. Le avisaremos por correo en cuanto se confirme. Si cerro la ventana de pago, puede reintentar desde su pedido.',
    notFoundTitle: 'Pedido no encontrado',
    notFoundBody: 'No pudimos localizar este pedido. Consulte su historial de pedidos.',
    orderNumber: 'Numero de pedido',
    total: 'Total:',
    trackingNote: 'Le enviaremos el numero de seguimiento por correo cuando se envie su pedido.',
    etaNote: 'Entrega estimada {from} – {to}.',
    trackOrder: 'Seguir mi pedido',
    viewOrder: 'Ver mi pedido',
    retryPayment: 'Reintentar pago',
    loading: 'Cargando…'
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
    },
    care: {
      handWashCold: 'Lavar a mano en frio',
      machineWash30: 'Lavar a maquina 30°C',
      doNotWash: 'No lavar',
      doNotBleach: 'No usar lejia',
      bleachOk: 'Usar lejia si es necesario',
      tumbleDryLow: 'Secadora temperatura baja',
      lineDry: 'Secar al aire',
      doNotTumbleDry: 'No usar secadora',
      ironLow: 'Planchar a baja temperatura',
      steamOnly: 'Solo vapor',
      doNotIron: 'No planchar',
      dryCleanOnly: 'Solo limpieza en seco',
      doNotDryClean: 'No limpiar en seco'
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
  blog: {
    title: 'El Journal',
    eyebrow: 'Atelier Dreamy',
    description: 'Consejos de planificacion, guias de telas e inspiracion para bodas al aire libre.',
    empty: 'Nuevas historias estan en camino — vuelve pronto.',
    backToBlog: '← Volver al blog',
    keepReading: 'Sigue leyendo',
    previous: 'Anterior',
    next: 'Siguiente',
    pageOf: 'Pagina {page} de {total}',
    notFound: 'Articulo no encontrado'
  },
  guide: {
    eyebrow: 'Planifique con nosotros',
    title: 'Su cronograma de vestuario nupcial',
    description: 'Desde el primer sueño hasta la prueba final: esto es cuándo abordar cada parte de su look de boda al aire libre.',
    empty: 'Las guias de planificacion estaran disponibles pronto. Vuelva a visitarnos.',
    startWithDress: 'Empiece por el vestido',
    checklist: 'Lista de tareas',
    complete: 'completadas'
  },
  inspiration: {
    metaTitle: 'Inspiracion y lookbooks de boda',
    metaDescription: 'Inspiracion para bodas al aire libre, lookbooks y paletas de color para hacer realidad tu vision.',
    heroEyebrow: 'Lookbook',
    heroTitle: 'Inspiracion para tu boda',
    editsEyebrow: 'Ediciones seleccionadas',
    editsTitle: 'Explora por estilo',
    empty: 'Estamos preparando nuevos lookbooks. Vuelve pronto.',
    paletteEyebrow: 'Herramienta gratuita',
    paletteTitle: 'Crea tu moodboard',
    paletteDescription: 'Pide muestras de tela para ver tus colores de boda en persona. Nosotros invitamos.',
    paletteCta: 'Ver colores para damas de honor',
    weddingsEyebrow: 'Historias de amor reales',
    weddingsTitle: 'Bodas Dreamy reales',
    shopEdit: 'Comprar la seleccion {title}',
    noStyles: 'Aun no hay estilos vinculados a este lookbook.'
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
    notProvided: 'No proporcionado',
    nav: {
      dashboard: 'Panel',
      orders: 'Pedidos',
      addresses: 'Direcciones',
      wishlist: 'Favoritos',
      myReviews: 'Mis resenas',
      weddingPlans: 'Planes de boda',
      showrooms: 'Showrooms',
      security: 'Inicio de sesion y seguridad',
      settings: 'Configuracion'
    },
    weddingPlans: {
      title: 'Planes de boda',
      subtitle: 'Su progreso guardado en las guias de planificacion.',
      empty: 'Aun no hay guias: empiece con un cronograma.'
    }
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
  },
  unsubscribe: {
    title: 'Cancelar la suscripcion al boletin',
    body: 'Esta a punto de dejar de recibir noticias y ofertas privadas de Dreamy. Puede suscribirse de nuevo en cualquier momento.',
    confirm: 'Confirmar cancelacion',
    confirming: 'Cancelando…',
    successTitle: 'Suscripcion cancelada',
    successBody: 'Ya no recibira nuestro boletin. Lamentamos verle partir.',
    invalidTitle: 'Este enlace ya no es valido',
    invalidBody: 'El enlace de cancelacion es invalido o ha caducado. Utilice el enlace de nuestro ultimo correo.',
    errorTitle: 'Algo salio mal',
    errorBody: 'No pudimos procesar su solicitud en este momento. Intentelo de nuevo.',
    retry: 'Intentar de nuevo'
  },
  orders: {
    title: 'Mis pedidos',
    all: 'Todo',
    orderNo: 'Pedido {no}',
    placed: 'Realizado {date}',
    itemsCount: '{count} articulos',
    details: 'Detalles',
    none: 'Aun no hay pedidos.',
    noneFiltered: 'No hay pedidos {status}.',
    status: {
      pending: 'Pendiente',
      paid: 'Pagado',
      shipped: 'Enviado',
      completed: 'Completado',
      cancelled: 'Cancelado',
      refunding: 'En reembolso',
      refunded: 'Reembolsado',
      delivered: 'Entregado'
    },
    paymentStatus: {
      created: 'Creado',
      processing: 'Procesando',
      succeeded: 'Completado',
      failed: 'Fallido',
      refunded: 'Reembolsado',
      partiallyRefunded: 'Reembolsado parcialmente'
    },
    refundStatus: {
      pending: 'Pendiente',
      approved: 'Aprobado',
      rejected: 'Rechazado'
    },
    productionStage: {
      pendingReview: 'Pendiente de revision',
      inProduction: 'En produccion',
      qualityCheck: 'Control de calidad',
      readyToShip: 'Listo para enviar'
    },
    shipmentStatus: {
      pending: 'Pendiente de recogida',
      inTransit: 'En transito',
      outForDelivery: 'En reparto',
      delivered: 'Entregado',
      exception: 'Incidencia',
      cancelled: 'Anulado'
    },
    card: {
      eta: 'Entrega est. {from} – {to}',
      packages: '{count} paquetes',
      tracking: 'Seguimiento {no}'
    },
    detail: {
      somethingWrong: 'Algo salio mal',
      notFound: 'Pedido no encontrado',
      backToOrders: 'Volver a pedidos',
      payNow: 'Pagar ahora',
      cancelOrder: 'Cancelar pedido',
      cancelConfirm: 'Cancelar este pedido?',
      cancelling: 'Cancelando…',
      yesCancel: 'Si, cancelar',
      keepOrder: 'Mantener pedido',
      requestRefund: 'Solicitar reembolso',
      refundNo: 'Reembolso {no}',
      items: 'Articulos',
      statusLabel: 'Estado:',
      paidAt: 'Pagado {date}',
      timelinePlaced: 'Realizado',
      timelinePaid: 'Pagado',
      timelineShipped: 'Enviado',
      timelineCompleted: 'Completado',
      timelineProduction: 'En produccion',
      timelineDelivered: 'Entregado',
      productionTitle: 'Progreso de produccion',
      productionBody: 'Su vestido se esta confeccionando a medida. Actualizaremos esta seccion a medida que avance en nuestro taller.',
      eta: 'Entrega estimada {from} – {to}',
      tax: 'Impuestos',
      refunded: 'Reembolsado',
      shipments: 'Envios',
      shipmentNo: 'Paquete {no}',
      trackPackage: 'Seguir paquete',
      contents: 'Contenido',
      trackingHistory: 'Historial de seguimiento',
      noTrackingEvents: 'Aun no hay actualizaciones de seguimiento.',
      activity: 'Actividad del pedido',
      confirmDelivery: 'Confirmar recepcion',
      confirmDeliveryQuestion: 'Ha recibido todos los articulos de este pedido?',
      confirmDeliveryYes: 'Si, recibido',
      confirmDeliveryNo: 'Todavia no',
      confirmingDelivery: 'Confirmando…',
      buyAgain: 'Comprar de nuevo',
      buyAgainBusy: 'Anadiendo…',
      buyAgainAdded: '{count} articulos anadidos a su carrito.',
      buyAgainSkipped: '{count} articulos no se pudieron anadir (sin stock o no disponibles).',
      viewCart: 'Ver carrito',
      countdown: 'Complete el pago en {time}',
      countdownExpired: 'El plazo de pago ha expirado.',
      refundTitle: 'Solicitar un reembolso',
      refundBody: 'Cuente por que desea un reembolso y nuestro equipo revisara su solicitud.',
      refundReason: 'Motivo',
      refundReasonError: 'Cuente brevemente por que desea un reembolso (max. 255 caracteres).',
      refundWindowEnded: 'El plazo de reembolso termino el {date}.',
      refundSubmitting: 'Enviando…',
      refundSubmit: 'Enviar solicitud'
    }
  },
  trackOrder: {
    eyebrow: 'Estado del pedido',
    title: 'Seguir su pedido',
    body: 'Introduzca su numero de pedido y el correo usado al comprar para ver el estado, el progreso de produccion y el seguimiento.',
    orderNo: 'Numero de pedido',
    orderNoPlaceholder: 'DRM-20260101-0001',
    email: 'Correo electronico',
    emailPlaceholder: 'usted@ejemplo.com',
    submit: 'Seguir pedido',
    searching: 'Buscando…',
    invalid: 'Introduzca su numero de pedido y un correo valido.',
    notFound: 'No encontramos ningun pedido con ese numero y correo.',
    rateLimited: 'Demasiadas consultas. Espere un momento e intentelo de nuevo.',
    orderLabel: 'Pedido {no}',
    placed: 'Realizado {date}',
    shipTo: 'Enviar a',
    eta: 'Entrega estimada {from} – {to}',
    packages: 'Paquetes',
    activity: 'Actividad del pedido',
    searchAgain: 'Seguir otro pedido',
    signInHint: 'Tiene una cuenta?',
    signIn: 'Inicie sesion para ver todos los detalles'
  },
  wishlist: {
    title: 'Mis favoritos',
    emptyBody: 'Su lista de favoritos esta vacia. Toque el corazon en cualquier estilo para guardarlo aqui.',
    startBrowsing: 'Empezar a explorar',
    moveToBag: 'Mover a la bolsa',
    recentlyViewed: 'Vistos recientemente',
    madeToMeasure: 'Este estilo se confecciona a medida: anada sus medidas en la pagina del producto.',
    openProduct: 'Abrir pagina del producto',
    moving: 'Moviendo…'
  },
  searchPage: {
    placeholder: 'Buscar vestidos, colores, ocasiones...',
    trySearching: 'Pruebe a buscar',
    somethingWrong: 'Algo salio mal',
    noResultsTitle: 'Sin resultados para "{q}"',
    noResultsBody: 'Pruebe un color, silueta u ocasion, o explore nuestras colecciones.',
    browseDresses: 'Ver vestidos',
    resultFor: '{count} resultado para "{q}"',
    resultsFor: '{count} resultados para "{q}"'
  },
  flashSale: {
    eyebrow: 'Venta flash',
    endsIn: 'Termina en {time}'
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
    sortFeatured: 'En vedette',
    empty: 'Aucun produit trouve.',
    loadMore: 'Charger plus',
    results: 'resultats',
    clearFilters: 'Effacer les filtres',
    eyebrow: 'Collection Dreamy',
    styleOne: 'style',
    stylesMany: 'styles',
    clearAll: 'Tout effacer',
    all: 'Tout',
    loadFailedTitle: 'Impossible de charger cette collection',
    loadFailedBody: 'Verifiez votre connexion et reessayez.',
    noMatchTitle: 'Aucun style ne correspond a vos filtres',
    noMatchBody: 'Essayez de retirer un filtre ou d explorer une autre couleur.',
    previous: 'Precedent',
    next: 'Suivant',
    pageOf: 'Page {page} sur {total}',
    showResults: 'Voir {count}',
    price: 'Prix',
    priceUnder200: 'Moins de $200',
    price200to500: '$200 – $500',
    price500to1000: '$500 – $1,000',
    priceOver1000: 'Plus de $1,000'
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
    leadTimeWeeks: 'Fait main sur commande · expedition en ~{weeks} semaines',
    leadTimeDays: 'Fait main sur commande · expedition en ~{days} jours',
    breadcrumbHome: 'Accueil',
    completeTheLook: 'Completez le look',
    youMayAlsoLike: 'Vous aimerez aussi',
    qa: 'Questions et reponses',
    reviewCount: '{count} avis',
    writeReview: 'Ecrire un avis',
    beFirstToReview: 'Soyez la premiere a donner votre avis',
    shareExperience: 'Partagez votre experience avec les autres Mariees.',
    featuredReview: 'Avis en vedette',
    loadMoreReviews: 'Charger plus d avis',
    loadMoreQuestions: 'Charger plus de questions',
    noQuestionsYet: 'Pas encore de questions',
    askAnything: 'Posez toutes vos questions sur la coupe, le tissu ou la livraison.',
    askedBy: 'Posee par {name}',
    asked: 'Question posee',
    reviewSubmitted: 'Votre avis a ete envoye et apparaitra apres moderation.',
    questionSubmitted: 'Votre question a ete envoyee — la reponse apparaitra ici des sa publication.',
    sortReviewsAria: 'Trier les avis',
    customerPhoto: 'Photo client',
    defaultCustomer: 'Cliente Dreamy',
    reviewSort: {
      featured: 'En vedette',
      newest: 'Plus recents',
      highest: 'Mieux notes',
      lowest: 'Moins bien notes'
    }
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
    },
    page: {
      title: 'Votre sac',
      emptyTitle: 'Votre sac est vide',
      emptyBody: 'On dirait que vous n avez rien ajoute encore. Trouvons la robe parfaite.',
      shopDresses: 'Voir les robes de Mariee',
      bestSellers: 'Meilleures ventes',
      mergedNotice: 'Lors de la fusion de votre sac, certaines quantites ont ete ajustees au stock disponible.',
      dyeLotNotice: 'Commandez sous 24h avec votre cortege pour garantir le meme lot de teinture pour ce style.',
      sizeLabel: 'Taille {size}',
      bust: 'Buste',
      waist: 'Taille',
      hips: 'Hanches',
      hollowToFloor: 'Du creux au sol',
      unavailable: 'Plus disponible',
      saveForLater: 'Garder pour plus tard',
      subtotal: 'Sous-total',
      shipping: 'Livraison',
      calculatedAtCheckout: 'Calcule au paiement',
      estimatedTotal: 'Total estime',
      promoNote: 'Les codes promo peuvent etre appliques au paiement.',
      checkoutCta: 'Passer au paiement',
      freeShipping: 'Livraison offerte des $200',
      secureCheckout: 'Paiement securise'
    }
  },
  checkout: {
    title: 'Paiement',
    shipping: 'Livraison',
    payment: 'Paiement',
    placeOrder: 'Passer la commande',
    orderSummary: 'Recapitulatif de la commande',
    stepAddress: 'Adresse',
    stepReview: 'Verification',
    shippingAddress: 'Adresse de livraison',
    loadingAddresses: 'Chargement des adresses…',
    defaultBadge: 'Par defaut',
    addNewAddress: 'Ajouter une adresse',
    selectAddressError: 'Selectionnez ou ajoutez une adresse de livraison.',
    continueToShipping: 'Continuer vers la livraison',
    shippingMethod: 'Mode de livraison',
    free: 'Offerte',
    giftWrapping: 'Ajouter l emballage cadeau',
    weddingDate: 'Date du mariage (facultatif)',
    leadTimeWarning: 'Attention — la production de cette commande peut prendre jusqu a {days} jours, ce qui est proche de votre date de mariage. Envisagez les options express ou contactez un conseiller.',
    promoCode: 'Code promo',
    couponApplied: '{code} applique',
    removeCoupon: 'Retirer',
    continueToPayment: 'Continuer vers le paiement',
    cardName: 'Carte de credit / debit',
    cardDesc: 'Visa, Mastercard, Amex',
    comingSoon: 'Bientot disponible',
    payAppleDesc: 'Paiement rapide avec Face ID',
    payGoogleDesc: 'Payez avec Google',
    payIn4: 'Payez en 4 fois sans frais',
    stripeNote: 'Vous saisirez vos informations de paiement en toute securite a l etape suivante, via Stripe.',
    payIn4Note: '4 paiements sans frais de {amount}. Vous serez redirige vers {provider} pour finaliser.',
    reviewOrder: 'Verifier la commande',
    reviewTitle: 'Verifiez votre commande',
    custom: 'Personnalise',
    qty: 'Qte {count}',
    shipTo: 'Livrer a',
    dduNote: 'Les commandes internationales sont expediees DDU (droits non payes) — les droits et taxes, le cas echeant, sont percus par le transporteur a la livraison.',
    adjustQuantities: 'Ajuster les quantites',
    placingOrder: 'Commande en cours…',
    summary: 'Recapitulatif',
    subtotal: 'Sous-total',
    giftWrappingLabel: 'Emballage cadeau',
    discount: 'Remise',
    total: 'Total',
    calculatedAtShipping: 'Calcule a l etape livraison',
    fillRequired: 'Remplissez tous les champs obligatoires.',
    fullName: 'Nom complet',
    phoneOptional: 'Telephone (facultatif)',
    addressLine: 'Adresse',
    city: 'Ville',
    state: 'Etat / Province',
    zip: 'Code postal',
    country: 'Pays',
    setDefault: 'Definir comme adresse par defaut',
    saving: 'Enregistrement…',
    saveAddress: 'Enregistrer l adresse',
    region: 'Etat / Province',
    loadingCountries: 'Chargement des pays…',
    countryNotSupported: 'Nous ne livrons pas encore cette destination.',
    standard: 'Standard',
    express: 'Express',
    transitDays: '{min}–{max} jours ouvres de transit',
    eta: 'Livraison estimee {from} – {to}',
    etaShort: 'Livraison est. {from} – {to}',
    productionDays: 'Confectionne sur commande en environ {days} jours',
    tax: 'Taxes',
    taxDetails: 'Detail des taxes',
    taxIncluded: 'Droits et taxes inclus — aucun frais supplementaire a la livraison.',
    dutiesNotice: 'Les commandes internationales sont expediees DDU (droits non payes) — les droits et taxes, le cas echeant, sont percus par le transporteur a la livraison.',
    rateLocked: 'Taux fige au paiement : 1 USD = {rate} {currency}'
  },
  paymentPanel: {
    testModeTitle: 'Paiement (mode test)',
    testModeBody: 'Le service de paiement est en mode test — aucune carte reelle n est requise. Cliquez sur Continuer pour confirmer votre commande.',
    continueLabel: 'Continuer',
    processing: 'Traitement…',
    pay: 'Payer {amount}',
    failed: 'Le paiement a echoue. Veuillez reessayer.',
    stubUnavailable: 'Le paiement en mode test n est pas disponible sur ce serveur. Veuillez payer par carte.',
    alreadyPaid: 'Cette commande est deja payee.',
    misconfigured: 'Le paiement est temporairement indisponible : le formulaire de carte n\'est pas configure. Veuillez contacter le support.'
  },
  orderSuccess: {
    confirmingTitle: 'Confirmation de votre paiement…',
    confirmingBody: 'Cela ne prend generalement que quelques secondes. Ne fermez pas cette page.',
    paidTitle: 'Merci !',
    paidBody: 'Votre commande est confirmee. Nous vous avons envoye un e-mail avec tous les details.',
    pendingTitle: 'Paiement en cours de confirmation',
    pendingBody: 'Votre paiement est toujours en cours — cela peut prendre un peu plus de temps avec Klarna ou Afterpay. Nous vous enverrons un e-mail des confirmation. Si vous avez ferme la fenetre de paiement, vous pouvez reessayer depuis votre commande.',
    notFoundTitle: 'Commande introuvable',
    notFoundBody: 'Nous n avons pas pu localiser cette commande. Consultez l historique de vos commandes.',
    orderNumber: 'Numero de commande',
    total: 'Total :',
    trackingNote: 'Un numero de suivi vous sera envoye par e-mail des l expedition.',
    etaNote: 'Livraison estimee {from} – {to}.',
    trackOrder: 'Suivre ma commande',
    viewOrder: 'Voir ma commande',
    retryPayment: 'Reessayer le paiement',
    loading: 'Chargement…'
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
    },
    care: {
      handWashCold: 'Lavage a la main a froid',
      machineWash30: 'Lavage en machine 30°C',
      doNotWash: 'Ne pas laver',
      doNotBleach: 'Ne pas blanchir',
      bleachOk: 'Blanchir si necessaire',
      tumbleDryLow: 'Sechage en machine doux',
      lineDry: 'Sechage a l air libre',
      doNotTumbleDry: 'Ne pas secher en machine',
      ironLow: 'Repasser a basse temperature',
      steamOnly: 'Vapeur uniquement',
      doNotIron: 'Ne pas repasser',
      dryCleanOnly: 'Nettoyage a sec uniquement',
      doNotDryClean: 'Ne pas nettoyer a sec'
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
  blog: {
    title: 'Le Journal',
    eyebrow: 'Atelier Dreamy',
    description: 'Conseils de planification, guides des tissus et inspiration pour mariages en plein air.',
    empty: 'De nouvelles histoires arrivent — revenez bientot.',
    backToBlog: '← Retour au blog',
    keepReading: 'Continuer a lire',
    previous: 'Precedent',
    next: 'Suivant',
    pageOf: 'Page {page} sur {total}',
    notFound: 'Article introuvable'
  },
  guide: {
    eyebrow: 'Planifiez avec nous',
    title: 'Votre calendrier de tenue de mariage',
    description: 'Du premier reve a la derniere retouche : voici quand preparer chaque element de votre tenue de mariage en plein air.',
    empty: 'Les guides de planification arrivent bientot. Revenez nous voir.',
    startWithDress: 'Commencer par la robe',
    checklist: 'Liste de taches',
    complete: 'terminees'
  },
  inspiration: {
    metaTitle: 'Inspiration mariage et lookbooks',
    metaDescription: 'Inspiration de mariage en plein air, lookbooks et palettes de couleurs pour donner vie a votre vision.',
    heroEyebrow: 'Lookbook',
    heroTitle: 'Inspiration mariage',
    editsEyebrow: 'Selections inspirees',
    editsTitle: 'Explorez par ambiance',
    empty: 'Nous preparons de nouveaux lookbooks. Revenez bientot.',
    paletteEyebrow: 'Outil gratuit',
    paletteTitle: 'Composez votre moodboard',
    paletteDescription: 'Commandez des echantillons de tissu pour voir vos couleurs de mariage en personne. C est offert.',
    paletteCta: 'Voir les couleurs pour demoiselles d honneur',
    weddingsEyebrow: 'Histoires d amour reelles',
    weddingsTitle: 'Vraies mariages Dreamy',
    shopEdit: 'Acheter la selection {title}',
    noStyles: 'Aucun style n est encore lie a ce lookbook.'
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
    notProvided: 'Non renseigne',
    nav: {
      dashboard: 'Tableau de bord',
      orders: 'Commandes',
      addresses: 'Adresses',
      wishlist: 'Favoris',
      myReviews: 'Mes avis',
      weddingPlans: 'Preparatifs du mariage',
      showrooms: 'Showrooms',
      security: 'Connexion et securite',
      settings: 'Parametres'
    },
    weddingPlans: {
      title: 'Preparatifs du mariage',
      subtitle: 'Votre progression enregistree dans les guides de planification.',
      empty: 'Aucun guide pour le moment — commencez par un calendrier.'
    }
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
  },
  unsubscribe: {
    title: 'Se desabonner de la newsletter',
    body: 'Vous etes sur le point de ne plus recevoir les nouvelles et offres privees de Dreamy. Vous pouvez vous reabonner a tout moment.',
    confirm: 'Confirmer le desabonnement',
    confirming: 'Desabonnement…',
    successTitle: 'Vous etes desabonne',
    successBody: 'Vous ne recevrez plus notre newsletter. Nous sommes tristes de vous voir partir.',
    invalidTitle: 'Ce lien n est plus valide',
    invalidBody: 'Le lien de desabonnement est invalide ou a expire. Veuillez utiliser le lien de notre dernier e-mail.',
    errorTitle: 'Une erreur est survenue',
    errorBody: 'Nous n avons pas pu traiter votre demande. Veuillez reessayer.',
    retry: 'Reessayer'
  },
  orders: {
    title: 'Mes commandes',
    all: 'Tout',
    orderNo: 'Commande {no}',
    placed: 'Passe le {date}',
    itemsCount: '{count} articles',
    details: 'Details',
    none: 'Aucune commande pour le moment.',
    noneFiltered: 'Aucune commande {status}.',
    status: {
      pending: 'En attente',
      paid: 'Payee',
      shipped: 'Expediee',
      completed: 'Terminee',
      cancelled: 'Annulee',
      refunding: 'Remboursement en cours',
      refunded: 'Remboursee',
      delivered: 'Livree'
    },
    paymentStatus: {
      created: 'Cree',
      processing: 'En cours',
      succeeded: 'Reussi',
      failed: 'Echoue',
      refunded: 'Rembourse',
      partiallyRefunded: 'Partiellement rembourse'
    },
    refundStatus: {
      pending: 'En attente',
      approved: 'Approuve',
      rejected: 'Rejete'
    },
    productionStage: {
      pendingReview: 'En attente de verification',
      inProduction: 'En production',
      qualityCheck: 'Controle qualite',
      readyToShip: 'Pret a expedier'
    },
    shipmentStatus: {
      pending: 'En attente d enlevement',
      inTransit: 'En transit',
      outForDelivery: 'En cours de livraison',
      delivered: 'Livre',
      exception: 'Incident',
      cancelled: 'Annule'
    },
    card: {
      eta: 'Livraison est. {from} – {to}',
      packages: '{count} colis',
      tracking: 'Suivi {no}'
    },
    detail: {
      somethingWrong: 'Une erreur est survenue',
      notFound: 'Commande introuvable',
      backToOrders: 'Retour aux commandes',
      payNow: 'Payer maintenant',
      cancelOrder: 'Annuler la commande',
      cancelConfirm: 'Annuler cette commande ?',
      cancelling: 'Annulation…',
      yesCancel: 'Oui, annuler',
      keepOrder: 'Garder la commande',
      requestRefund: 'Demander un remboursement',
      refundNo: 'Remboursement {no}',
      items: 'Articles',
      statusLabel: 'Statut :',
      paidAt: 'Paye le {date}',
      timelinePlaced: 'Passee',
      timelinePaid: 'Payee',
      timelineShipped: 'Expediee',
      timelineCompleted: 'Terminee',
      timelineProduction: 'En production',
      timelineDelivered: 'Livree',
      productionTitle: 'Avancement de la production',
      productionBody: 'Votre robe est confectionnee sur commande. Nous mettrons cette section a jour au fil de son passage dans notre atelier.',
      eta: 'Livraison estimee {from} – {to}',
      tax: 'Taxes',
      refunded: 'Rembourse',
      shipments: 'Expeditions',
      shipmentNo: 'Colis {no}',
      trackPackage: 'Suivre le colis',
      contents: 'Contenu',
      trackingHistory: 'Historique de suivi',
      noTrackingEvents: 'Aucune mise a jour de suivi pour le moment.',
      activity: 'Activite de la commande',
      confirmDelivery: 'Confirmer la reception',
      confirmDeliveryQuestion: 'Avez-vous recu tous les articles de cette commande ?',
      confirmDeliveryYes: 'Oui, recu',
      confirmDeliveryNo: 'Pas encore',
      confirmingDelivery: 'Confirmation…',
      buyAgain: 'Racheter',
      buyAgainBusy: 'Ajout…',
      buyAgainAdded: '{count} articles ajoutes a votre panier.',
      buyAgainSkipped: '{count} articles n ont pas pu etre ajoutes (rupture de stock ou indisponibles).',
      viewCart: 'Voir le panier',
      countdown: 'Finalisez le paiement sous {time}',
      countdownExpired: 'Le delai de paiement est expire.',
      refundTitle: 'Demander un remboursement',
      refundBody: 'Dites-nous pourquoi vous souhaitez un remboursement et notre equipe etudiera votre demande.',
      refundReason: 'Motif',
      refundReasonError: 'Indiquez brievement pourquoi vous souhaitez un remboursement (255 caracteres max).',
      refundWindowEnded: 'La fenetre de remboursement s est terminee le {date}.',
      refundSubmitting: 'Envoi…',
      refundSubmit: 'Envoyer la demande'
    }
  },
  trackOrder: {
    eyebrow: 'Statut de commande',
    title: 'Suivre votre commande',
    body: 'Saisissez votre numero de commande et l e-mail utilise lors de l achat pour voir le statut, l avancement de la production et le suivi.',
    orderNo: 'Numero de commande',
    orderNoPlaceholder: 'DRM-20260101-0001',
    email: 'E-mail',
    emailPlaceholder: 'vous@exemple.com',
    submit: 'Suivre la commande',
    searching: 'Recherche…',
    invalid: 'Saisissez votre numero de commande et une adresse e-mail valide.',
    notFound: 'Aucune commande ne correspond a ce numero et cet e-mail.',
    rateLimited: 'Trop de recherches. Patientez un instant puis reessayez.',
    orderLabel: 'Commande {no}',
    placed: 'Passee le {date}',
    shipTo: 'Livrer a',
    eta: 'Livraison estimee {from} – {to}',
    packages: 'Colis',
    activity: 'Activite de la commande',
    searchAgain: 'Suivre une autre commande',
    signInHint: 'Vous avez un compte ?',
    signIn: 'Connectez-vous pour voir tous les details'
  },
  wishlist: {
    title: 'Mes favoris',
    emptyBody: 'Votre liste de favoris est vide. Touchez le coeur d un style pour l enregistrer ici.',
    startBrowsing: 'Commencer a explorer',
    moveToBag: 'Deplacer vers le sac',
    recentlyViewed: 'Vus recemment',
    madeToMeasure: 'Ce style est realise sur mesure — ajoutez vos mesures sur la page du produit.',
    openProduct: 'Ouvrir la page produit',
    moving: 'Deplacement…'
  },
  searchPage: {
    placeholder: 'Rechercher robes, couleurs, occasions...',
    trySearching: 'Essayez de rechercher',
    somethingWrong: 'Une erreur est survenue',
    noResultsTitle: 'Aucun resultat pour "{q}"',
    noResultsBody: 'Essayez une couleur, une silhouette ou une occasion — ou parcourrez nos collections.',
    browseDresses: 'Voir les robes',
    resultFor: '{count} resultat pour "{q}"',
    resultsFor: '{count} resultats pour "{q}"'
  },
  flashSale: {
    eyebrow: 'Vente flash',
    endsIn: 'Se termine dans {time}'
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
