//! ISO-3166-1 国家目录(250 国)+ US/CA/AU 州省字典。
//! 由 Java `support/CountryCatalog.java` 自动转换生成(脚本迁移,逐条对齐)。
//! zone 取值与 shipping_option.zone 逐字一致;别名大写归一匹配(norm=trim+uppercase)。

pub struct Country {
    pub code: &'static str,
    pub name: &'static str,
    pub zone: &'static str,
    /// 英文名 + 别名的 norm 化匹配索引之外,保留原始名
    pub aliases: &'static [&'static str],
}

pub struct Region {
    pub code: &'static str,
    pub name: &'static str,
    pub aliases: &'static [&'static str],
}

pub const ZONES: &[&str] = &[
    "NORTH_AMERICA",
    "EUROPE",
    "UK",
    "OCEANIA",
    "ASIA",
    "LATAM",
    "MEA",
    "REST",
];

pub static COUNTRIES: &[Country] = &[
    Country { code: "US", name: "United States", zone: "NORTH_AMERICA", aliases: &["USA", "UNITED STATES OF AMERICA", "U.S.", "U.S.A.", "AMERICA"] },
    Country { code: "CA", name: "Canada", zone: "NORTH_AMERICA", aliases: &[] },
    Country { code: "MX", name: "Mexico", zone: "NORTH_AMERICA", aliases: &["MÉXICO"] },
    Country { code: "GB", name: "United Kingdom", zone: "UK", aliases: &["UK", "GREAT BRITAIN", "ENGLAND", "SCOTLAND", "WALES", "NORTHERN IRELAND", "BRITAIN"] },
    Country { code: "GG", name: "Guernsey", zone: "UK", aliases: &[] },
    Country { code: "JE", name: "Jersey", zone: "UK", aliases: &[] },
    Country { code: "IM", name: "Isle of Man", zone: "UK", aliases: &[] },
    Country { code: "IE", name: "Ireland", zone: "EUROPE", aliases: &["REPUBLIC OF IRELAND"] },
    Country { code: "FR", name: "France", zone: "EUROPE", aliases: &[] },
    Country { code: "ES", name: "Spain", zone: "EUROPE", aliases: &["ESPAÑA"] },
    Country { code: "DE", name: "Germany", zone: "EUROPE", aliases: &["DEUTSCHLAND"] },
    Country { code: "IT", name: "Italy", zone: "EUROPE", aliases: &["ITALIA"] },
    Country { code: "PT", name: "Portugal", zone: "EUROPE", aliases: &[] },
    Country { code: "NL", name: "Netherlands", zone: "EUROPE", aliases: &["THE NETHERLANDS", "HOLLAND"] },
    Country { code: "BE", name: "Belgium", zone: "EUROPE", aliases: &[] },
    Country { code: "LU", name: "Luxembourg", zone: "EUROPE", aliases: &[] },
    Country { code: "AT", name: "Austria", zone: "EUROPE", aliases: &[] },
    Country { code: "CH", name: "Switzerland", zone: "EUROPE", aliases: &[] },
    Country { code: "SE", name: "Sweden", zone: "EUROPE", aliases: &[] },
    Country { code: "NO", name: "Norway", zone: "EUROPE", aliases: &[] },
    Country { code: "DK", name: "Denmark", zone: "EUROPE", aliases: &[] },
    Country { code: "FI", name: "Finland", zone: "EUROPE", aliases: &[] },
    Country { code: "IS", name: "Iceland", zone: "EUROPE", aliases: &[] },
    Country { code: "PL", name: "Poland", zone: "EUROPE", aliases: &[] },
    Country { code: "CZ", name: "Czechia", zone: "EUROPE", aliases: &["CZECH REPUBLIC"] },
    Country { code: "SK", name: "Slovakia", zone: "EUROPE", aliases: &[] },
    Country { code: "HU", name: "Hungary", zone: "EUROPE", aliases: &[] },
    Country { code: "RO", name: "Romania", zone: "EUROPE", aliases: &[] },
    Country { code: "BG", name: "Bulgaria", zone: "EUROPE", aliases: &[] },
    Country { code: "GR", name: "Greece", zone: "EUROPE", aliases: &[] },
    Country { code: "HR", name: "Croatia", zone: "EUROPE", aliases: &[] },
    Country { code: "SI", name: "Slovenia", zone: "EUROPE", aliases: &[] },
    Country { code: "EE", name: "Estonia", zone: "EUROPE", aliases: &[] },
    Country { code: "LV", name: "Latvia", zone: "EUROPE", aliases: &[] },
    Country { code: "LT", name: "Lithuania", zone: "EUROPE", aliases: &[] },
    Country { code: "MT", name: "Malta", zone: "EUROPE", aliases: &[] },
    Country { code: "CY", name: "Cyprus", zone: "EUROPE", aliases: &[] },
    Country { code: "LI", name: "Liechtenstein", zone: "EUROPE", aliases: &[] },
    Country { code: "MC", name: "Monaco", zone: "EUROPE", aliases: &[] },
    Country { code: "AD", name: "Andorra", zone: "EUROPE", aliases: &[] },
    Country { code: "SM", name: "San Marino", zone: "EUROPE", aliases: &[] },
    Country { code: "VA", name: "Holy See", zone: "EUROPE", aliases: &["VATICAN", "VATICAN CITY"] },
    Country { code: "AL", name: "Albania", zone: "EUROPE", aliases: &[] },
    Country { code: "BA", name: "Bosnia and Herzegovina", zone: "EUROPE", aliases: &["BOSNIA"] },
    Country { code: "ME", name: "Montenegro", zone: "EUROPE", aliases: &[] },
    Country { code: "MK", name: "North Macedonia", zone: "EUROPE", aliases: &["MACEDONIA"] },
    Country { code: "RS", name: "Serbia", zone: "EUROPE", aliases: &[] },
    Country { code: "XK", name: "Kosovo", zone: "EUROPE", aliases: &[] },
    Country { code: "UA", name: "Ukraine", zone: "EUROPE", aliases: &[] },
    Country { code: "MD", name: "Moldova", zone: "EUROPE", aliases: &["REPUBLIC OF MOLDOVA"] },
    Country { code: "BY", name: "Belarus", zone: "EUROPE", aliases: &[] },
    Country { code: "FO", name: "Faroe Islands", zone: "EUROPE", aliases: &[] },
    Country { code: "GI", name: "Gibraltar", zone: "EUROPE", aliases: &[] },
    Country { code: "AX", name: "Åland Islands", zone: "EUROPE", aliases: &["ALAND ISLANDS"] },
    Country { code: "SJ", name: "Svalbard and Jan Mayen", zone: "EUROPE", aliases: &[] },
    Country { code: "GL", name: "Greenland", zone: "EUROPE", aliases: &[] },
    Country { code: "AU", name: "Australia", zone: "OCEANIA", aliases: &[] },
    Country { code: "NZ", name: "New Zealand", zone: "OCEANIA", aliases: &[] },
    Country { code: "FJ", name: "Fiji", zone: "OCEANIA", aliases: &[] },
    Country { code: "PG", name: "Papua New Guinea", zone: "OCEANIA", aliases: &[] },
    Country { code: "WS", name: "Samoa", zone: "OCEANIA", aliases: &[] },
    Country { code: "TO", name: "Tonga", zone: "OCEANIA", aliases: &[] },
    Country { code: "VU", name: "Vanuatu", zone: "OCEANIA", aliases: &[] },
    Country { code: "SB", name: "Solomon Islands", zone: "OCEANIA", aliases: &[] },
    Country { code: "NC", name: "New Caledonia", zone: "OCEANIA", aliases: &[] },
    Country { code: "PF", name: "French Polynesia", zone: "OCEANIA", aliases: &[] },
    Country { code: "GU", name: "Guam", zone: "OCEANIA", aliases: &[] },
    Country { code: "MP", name: "Northern Mariana Islands", zone: "OCEANIA", aliases: &[] },
    Country { code: "AS", name: "American Samoa", zone: "OCEANIA", aliases: &[] },
    Country { code: "CK", name: "Cook Islands", zone: "OCEANIA", aliases: &[] },
    Country { code: "NU", name: "Niue", zone: "OCEANIA", aliases: &[] },
    Country { code: "TK", name: "Tokelau", zone: "OCEANIA", aliases: &[] },
    Country { code: "TV", name: "Tuvalu", zone: "OCEANIA", aliases: &[] },
    Country { code: "KI", name: "Kiribati", zone: "OCEANIA", aliases: &[] },
    Country { code: "NR", name: "Nauru", zone: "OCEANIA", aliases: &[] },
    Country { code: "MH", name: "Marshall Islands", zone: "OCEANIA", aliases: &[] },
    Country { code: "FM", name: "Micronesia", zone: "OCEANIA", aliases: &["FEDERATED STATES OF MICRONESIA"] },
    Country { code: "PW", name: "Palau", zone: "OCEANIA", aliases: &[] },
    Country { code: "WF", name: "Wallis and Futuna", zone: "OCEANIA", aliases: &[] },
    Country { code: "PN", name: "Pitcairn", zone: "OCEANIA", aliases: &["PITCAIRN ISLANDS"] },
    Country { code: "NF", name: "Norfolk Island", zone: "OCEANIA", aliases: &[] },
    Country { code: "CX", name: "Christmas Island", zone: "OCEANIA", aliases: &[] },
    Country { code: "CC", name: "Cocos (Keeling) Islands", zone: "OCEANIA", aliases: &["COCOS ISLANDS"] },
    Country { code: "HM", name: "Heard Island and McDonald Islands", zone: "OCEANIA", aliases: &[] },
    Country { code: "UM", name: "United States Minor Outlying Islands", zone: "OCEANIA", aliases: &[] },
    Country { code: "CN", name: "China", zone: "ASIA", aliases: &["PEOPLE'S REPUBLIC OF CHINA", "PRC"] },
    Country { code: "HK", name: "Hong Kong", zone: "ASIA", aliases: &["HONG KONG SAR"] },
    Country { code: "MO", name: "Macao", zone: "ASIA", aliases: &["MACAU"] },
    Country { code: "TW", name: "Taiwan", zone: "ASIA", aliases: &[] },
    Country { code: "JP", name: "Japan", zone: "ASIA", aliases: &[] },
    Country { code: "KR", name: "South Korea", zone: "ASIA", aliases: &["KOREA", "REPUBLIC OF KOREA", "KOREA, REPUBLIC OF"] },
    Country { code: "KP", name: "North Korea", zone: "ASIA", aliases: &["DEMOCRATIC PEOPLE'S REPUBLIC OF KOREA"] },
    Country { code: "MN", name: "Mongolia", zone: "ASIA", aliases: &[] },
    Country { code: "SG", name: "Singapore", zone: "ASIA", aliases: &[] },
    Country { code: "MY", name: "Malaysia", zone: "ASIA", aliases: &[] },
    Country { code: "TH", name: "Thailand", zone: "ASIA", aliases: &[] },
    Country { code: "VN", name: "Vietnam", zone: "ASIA", aliases: &["VIET NAM"] },
    Country { code: "PH", name: "Philippines", zone: "ASIA", aliases: &["THE PHILIPPINES"] },
    Country { code: "ID", name: "Indonesia", zone: "ASIA", aliases: &[] },
    Country { code: "BN", name: "Brunei", zone: "ASIA", aliases: &["BRUNEI DARUSSALAM"] },
    Country { code: "KH", name: "Cambodia", zone: "ASIA", aliases: &[] },
    Country { code: "LA", name: "Laos", zone: "ASIA", aliases: &["LAO PEOPLE'S DEMOCRATIC REPUBLIC"] },
    Country { code: "MM", name: "Myanmar", zone: "ASIA", aliases: &["BURMA"] },
    Country { code: "TL", name: "Timor-Leste", zone: "ASIA", aliases: &["EAST TIMOR"] },
    Country { code: "IN", name: "India", zone: "ASIA", aliases: &[] },
    Country { code: "PK", name: "Pakistan", zone: "ASIA", aliases: &[] },
    Country { code: "BD", name: "Bangladesh", zone: "ASIA", aliases: &[] },
    Country { code: "LK", name: "Sri Lanka", zone: "ASIA", aliases: &[] },
    Country { code: "NP", name: "Nepal", zone: "ASIA", aliases: &[] },
    Country { code: "BT", name: "Bhutan", zone: "ASIA", aliases: &[] },
    Country { code: "MV", name: "Maldives", zone: "ASIA", aliases: &[] },
    Country { code: "AF", name: "Afghanistan", zone: "ASIA", aliases: &[] },
    Country { code: "KZ", name: "Kazakhstan", zone: "ASIA", aliases: &[] },
    Country { code: "UZ", name: "Uzbekistan", zone: "ASIA", aliases: &[] },
    Country { code: "KG", name: "Kyrgyzstan", zone: "ASIA", aliases: &[] },
    Country { code: "TJ", name: "Tajikistan", zone: "ASIA", aliases: &[] },
    Country { code: "TM", name: "Turkmenistan", zone: "ASIA", aliases: &[] },
    Country { code: "RU", name: "Russia", zone: "ASIA", aliases: &["RUSSIAN FEDERATION"] },
    Country { code: "GE", name: "Georgia", zone: "ASIA", aliases: &[] },
    Country { code: "AM", name: "Armenia", zone: "ASIA", aliases: &[] },
    Country { code: "AZ", name: "Azerbaijan", zone: "ASIA", aliases: &[] },
    Country { code: "IO", name: "British Indian Ocean Territory", zone: "ASIA", aliases: &[] },
    Country { code: "BR", name: "Brazil", zone: "LATAM", aliases: &["BRASIL"] },
    Country { code: "AR", name: "Argentina", zone: "LATAM", aliases: &[] },
    Country { code: "CL", name: "Chile", zone: "LATAM", aliases: &[] },
    Country { code: "CO", name: "Colombia", zone: "LATAM", aliases: &[] },
    Country { code: "PE", name: "Peru", zone: "LATAM", aliases: &["PERÚ"] },
    Country { code: "VE", name: "Venezuela", zone: "LATAM", aliases: &[] },
    Country { code: "EC", name: "Ecuador", zone: "LATAM", aliases: &[] },
    Country { code: "BO", name: "Bolivia", zone: "LATAM", aliases: &[] },
    Country { code: "PY", name: "Paraguay", zone: "LATAM", aliases: &[] },
    Country { code: "UY", name: "Uruguay", zone: "LATAM", aliases: &[] },
    Country { code: "GY", name: "Guyana", zone: "LATAM", aliases: &[] },
    Country { code: "SR", name: "Suriname", zone: "LATAM", aliases: &[] },
    Country { code: "GF", name: "French Guiana", zone: "LATAM", aliases: &[] },
    Country { code: "FK", name: "Falkland Islands", zone: "LATAM", aliases: &["MALVINAS"] },
    Country { code: "GS", name: "South Georgia and the South Sandwich Islands", zone: "LATAM", aliases: &[] },
    Country { code: "GT", name: "Guatemala", zone: "LATAM", aliases: &[] },
    Country { code: "BZ", name: "Belize", zone: "LATAM", aliases: &[] },
    Country { code: "HN", name: "Honduras", zone: "LATAM", aliases: &[] },
    Country { code: "SV", name: "El Salvador", zone: "LATAM", aliases: &[] },
    Country { code: "NI", name: "Nicaragua", zone: "LATAM", aliases: &[] },
    Country { code: "CR", name: "Costa Rica", zone: "LATAM", aliases: &[] },
    Country { code: "PA", name: "Panama", zone: "LATAM", aliases: &[] },
    Country { code: "CU", name: "Cuba", zone: "LATAM", aliases: &[] },
    Country { code: "DO", name: "Dominican Republic", zone: "LATAM", aliases: &[] },
    Country { code: "HT", name: "Haiti", zone: "LATAM", aliases: &[] },
    Country { code: "JM", name: "Jamaica", zone: "LATAM", aliases: &[] },
    Country { code: "PR", name: "Puerto Rico", zone: "LATAM", aliases: &[] },
    Country { code: "BS", name: "Bahamas", zone: "LATAM", aliases: &["THE BAHAMAS"] },
    Country { code: "BB", name: "Barbados", zone: "LATAM", aliases: &[] },
    Country { code: "TT", name: "Trinidad and Tobago", zone: "LATAM", aliases: &[] },
    Country { code: "AG", name: "Antigua and Barbuda", zone: "LATAM", aliases: &[] },
    Country { code: "DM", name: "Dominica", zone: "LATAM", aliases: &[] },
    Country { code: "GD", name: "Grenada", zone: "LATAM", aliases: &[] },
    Country { code: "KN", name: "Saint Kitts and Nevis", zone: "LATAM", aliases: &["ST KITTS AND NEVIS"] },
    Country { code: "LC", name: "Saint Lucia", zone: "LATAM", aliases: &["ST LUCIA"] },
    Country { code: "VC", name: "Saint Vincent and the Grenadines", zone: "LATAM", aliases: &["ST VINCENT AND THE GRENADINES"] },
    Country { code: "AI", name: "Anguilla", zone: "LATAM", aliases: &[] },
    Country { code: "AW", name: "Aruba", zone: "LATAM", aliases: &[] },
    Country { code: "BM", name: "Bermuda", zone: "LATAM", aliases: &[] },
    Country { code: "BQ", name: "Bonaire, Sint Eustatius and Saba", zone: "LATAM", aliases: &["CARIBBEAN NETHERLANDS"] },
    Country { code: "CW", name: "Curaçao", zone: "LATAM", aliases: &["CURACAO"] },
    Country { code: "SX", name: "Sint Maarten", zone: "LATAM", aliases: &[] },
    Country { code: "MF", name: "Saint Martin", zone: "LATAM", aliases: &["ST MARTIN"] },
    Country { code: "BL", name: "Saint Barthélemy", zone: "LATAM", aliases: &["SAINT BARTHELEMY", "ST BARTS"] },
    Country { code: "GP", name: "Guadeloupe", zone: "LATAM", aliases: &[] },
    Country { code: "MQ", name: "Martinique", zone: "LATAM", aliases: &[] },
    Country { code: "KY", name: "Cayman Islands", zone: "LATAM", aliases: &[] },
    Country { code: "TC", name: "Turks and Caicos Islands", zone: "LATAM", aliases: &[] },
    Country { code: "VG", name: "British Virgin Islands", zone: "LATAM", aliases: &["VIRGIN ISLANDS, BRITISH"] },
    Country { code: "VI", name: "U.S. Virgin Islands", zone: "LATAM", aliases: &["US VIRGIN ISLANDS", "VIRGIN ISLANDS, U.S."] },
    Country { code: "MS", name: "Montserrat", zone: "LATAM", aliases: &[] },
    Country { code: "PM", name: "Saint Pierre and Miquelon", zone: "LATAM", aliases: &["ST PIERRE AND MIQUELON"] },
    Country { code: "AE", name: "United Arab Emirates", zone: "MEA", aliases: &["UAE"] },
    Country { code: "SA", name: "Saudi Arabia", zone: "MEA", aliases: &[] },
    Country { code: "QA", name: "Qatar", zone: "MEA", aliases: &[] },
    Country { code: "KW", name: "Kuwait", zone: "MEA", aliases: &[] },
    Country { code: "BH", name: "Bahrain", zone: "MEA", aliases: &[] },
    Country { code: "OM", name: "Oman", zone: "MEA", aliases: &[] },
    Country { code: "YE", name: "Yemen", zone: "MEA", aliases: &[] },
    Country { code: "IL", name: "Israel", zone: "MEA", aliases: &[] },
    Country { code: "PS", name: "Palestine", zone: "MEA", aliases: &["STATE OF PALESTINE"] },
    Country { code: "JO", name: "Jordan", zone: "MEA", aliases: &[] },
    Country { code: "LB", name: "Lebanon", zone: "MEA", aliases: &[] },
    Country { code: "SY", name: "Syria", zone: "MEA", aliases: &["SYRIAN ARAB REPUBLIC"] },
    Country { code: "IQ", name: "Iraq", zone: "MEA", aliases: &[] },
    Country { code: "IR", name: "Iran", zone: "MEA", aliases: &["ISLAMIC REPUBLIC OF IRAN"] },
    Country { code: "TR", name: "Türkiye", zone: "MEA", aliases: &["TURKEY", "TURKIYE"] },
    Country { code: "EG", name: "Egypt", zone: "MEA", aliases: &[] },
    Country { code: "LY", name: "Libya", zone: "MEA", aliases: &[] },
    Country { code: "TN", name: "Tunisia", zone: "MEA", aliases: &[] },
    Country { code: "DZ", name: "Algeria", zone: "MEA", aliases: &[] },
    Country { code: "MA", name: "Morocco", zone: "MEA", aliases: &[] },
    Country { code: "EH", name: "Western Sahara", zone: "MEA", aliases: &[] },
    Country { code: "SD", name: "Sudan", zone: "MEA", aliases: &[] },
    Country { code: "SS", name: "South Sudan", zone: "MEA", aliases: &[] },
    Country { code: "ET", name: "Ethiopia", zone: "MEA", aliases: &[] },
    Country { code: "ER", name: "Eritrea", zone: "MEA", aliases: &[] },
    Country { code: "DJ", name: "Djibouti", zone: "MEA", aliases: &[] },
    Country { code: "SO", name: "Somalia", zone: "MEA", aliases: &[] },
    Country { code: "KE", name: "Kenya", zone: "MEA", aliases: &[] },
    Country { code: "UG", name: "Uganda", zone: "MEA", aliases: &[] },
    Country { code: "TZ", name: "Tanzania", zone: "MEA", aliases: &["UNITED REPUBLIC OF TANZANIA"] },
    Country { code: "RW", name: "Rwanda", zone: "MEA", aliases: &[] },
    Country { code: "BI", name: "Burundi", zone: "MEA", aliases: &[] },
    Country { code: "NG", name: "Nigeria", zone: "MEA", aliases: &[] },
    Country { code: "GH", name: "Ghana", zone: "MEA", aliases: &[] },
    Country { code: "CI", name: "Côte d'Ivoire", zone: "MEA", aliases: &["COTE D'IVOIRE", "IVORY COAST"] },
    Country { code: "SN", name: "Senegal", zone: "MEA", aliases: &[] },
    Country { code: "ML", name: "Mali", zone: "MEA", aliases: &[] },
    Country { code: "BF", name: "Burkina Faso", zone: "MEA", aliases: &[] },
    Country { code: "NE", name: "Niger", zone: "MEA", aliases: &[] },
    Country { code: "TD", name: "Chad", zone: "MEA", aliases: &[] },
    Country { code: "CM", name: "Cameroon", zone: "MEA", aliases: &[] },
    Country { code: "BJ", name: "Benin", zone: "MEA", aliases: &[] },
    Country { code: "TG", name: "Togo", zone: "MEA", aliases: &[] },
    Country { code: "GN", name: "Guinea", zone: "MEA", aliases: &[] },
    Country { code: "GW", name: "Guinea-Bissau", zone: "MEA", aliases: &[] },
    Country { code: "SL", name: "Sierra Leone", zone: "MEA", aliases: &[] },
    Country { code: "LR", name: "Liberia", zone: "MEA", aliases: &[] },
    Country { code: "GM", name: "Gambia", zone: "MEA", aliases: &["THE GAMBIA"] },
    Country { code: "MR", name: "Mauritania", zone: "MEA", aliases: &[] },
    Country { code: "CV", name: "Cabo Verde", zone: "MEA", aliases: &["CAPE VERDE"] },
    Country { code: "ST", name: "Sao Tome and Principe", zone: "MEA", aliases: &["SÃO TOMÉ AND PRÍNCIPE"] },
    Country { code: "GQ", name: "Equatorial Guinea", zone: "MEA", aliases: &[] },
    Country { code: "GA", name: "Gabon", zone: "MEA", aliases: &[] },
    Country { code: "CG", name: "Congo", zone: "MEA", aliases: &["REPUBLIC OF THE CONGO", "CONGO-BRAZZAVILLE"] },
    Country { code: "CD", name: "Democratic Republic of the Congo", zone: "MEA", aliases: &["DR CONGO", "CONGO-KINSHASA", "DRC"] },
    Country { code: "CF", name: "Central African Republic", zone: "MEA", aliases: &[] },
    Country { code: "AO", name: "Angola", zone: "MEA", aliases: &[] },
    Country { code: "ZM", name: "Zambia", zone: "MEA", aliases: &[] },
    Country { code: "ZW", name: "Zimbabwe", zone: "MEA", aliases: &[] },
    Country { code: "MW", name: "Malawi", zone: "MEA", aliases: &[] },
    Country { code: "MZ", name: "Mozambique", zone: "MEA", aliases: &[] },
    Country { code: "MG", name: "Madagascar", zone: "MEA", aliases: &[] },
    Country { code: "MU", name: "Mauritius", zone: "MEA", aliases: &[] },
    Country { code: "SC", name: "Seychelles", zone: "MEA", aliases: &[] },
    Country { code: "KM", name: "Comoros", zone: "MEA", aliases: &[] },
    Country { code: "RE", name: "Réunion", zone: "MEA", aliases: &["REUNION"] },
    Country { code: "YT", name: "Mayotte", zone: "MEA", aliases: &[] },
    Country { code: "ZA", name: "South Africa", zone: "MEA", aliases: &[] },
    Country { code: "NA", name: "Namibia", zone: "MEA", aliases: &[] },
    Country { code: "BW", name: "Botswana", zone: "MEA", aliases: &[] },
    Country { code: "LS", name: "Lesotho", zone: "MEA", aliases: &[] },
    Country { code: "SZ", name: "Eswatini", zone: "MEA", aliases: &["SWAZILAND"] },
    Country { code: "SH", name: "Saint Helena, Ascension and Tristan da Cunha", zone: "MEA", aliases: &["SAINT HELENA", "ST HELENA"] },
    Country { code: "TF", name: "French Southern Territories", zone: "MEA", aliases: &[] },
    Country { code: "BV", name: "Bouvet Island", zone: "MEA", aliases: &[] },
    Country { code: "AQ", name: "Antarctica", zone: "REST", aliases: &[] },
];

/// US/CA/AU 州省字典(code + name + 别名)
pub static REGIONS: &[(&str, &[Region])] = &[
    ("US", &[Region { code: "AL", name: "Alabama", aliases: &[] }, Region { code: "AK", name: "Alaska", aliases: &[] }, Region { code: "AZ", name: "Arizona", aliases: &[] }, Region { code: "AR", name: "Arkansas", aliases: &[] }, Region { code: "CA", name: "California", aliases: &["CALIF"] }, Region { code: "CO", name: "Colorado", aliases: &[] }, Region { code: "CT", name: "Connecticut", aliases: &[] }, Region { code: "DE", name: "Delaware", aliases: &[] }, Region { code: "DC", name: "District of Columbia", aliases: &["WASHINGTON DC", "WASHINGTON D.C.", "D.C."] }, Region { code: "FL", name: "Florida", aliases: &[] }, Region { code: "GA", name: "Georgia", aliases: &[] }, Region { code: "HI", name: "Hawaii", aliases: &[] }, Region { code: "ID", name: "Idaho", aliases: &[] }, Region { code: "IL", name: "Illinois", aliases: &[] }, Region { code: "IN", name: "Indiana", aliases: &[] }, Region { code: "IA", name: "Iowa", aliases: &[] }, Region { code: "KS", name: "Kansas", aliases: &[] }, Region { code: "KY", name: "Kentucky", aliases: &[] }, Region { code: "LA", name: "Louisiana", aliases: &[] }, Region { code: "ME", name: "Maine", aliases: &[] }, Region { code: "MD", name: "Maryland", aliases: &[] }, Region { code: "MA", name: "Massachusetts", aliases: &["MASS"] }, Region { code: "MI", name: "Michigan", aliases: &[] }, Region { code: "MN", name: "Minnesota", aliases: &[] }, Region { code: "MS", name: "Mississippi", aliases: &[] }, Region { code: "MO", name: "Missouri", aliases: &[] }, Region { code: "MT", name: "Montana", aliases: &[] }, Region { code: "NE", name: "Nebraska", aliases: &[] }, Region { code: "NV", name: "Nevada", aliases: &[] }, Region { code: "NH", name: "New Hampshire", aliases: &[] }, Region { code: "NJ", name: "New Jersey", aliases: &[] }, Region { code: "NM", name: "New Mexico", aliases: &[] }, Region { code: "NY", name: "New York", aliases: &[] }, Region { code: "NC", name: "North Carolina", aliases: &[] }, Region { code: "ND", name: "North Dakota", aliases: &[] }, Region { code: "OH", name: "Ohio", aliases: &[] }, Region { code: "OK", name: "Oklahoma", aliases: &[] }, Region { code: "OR", name: "Oregon", aliases: &[] }, Region { code: "PA", name: "Pennsylvania", aliases: &["PENN"] }, Region { code: "RI", name: "Rhode Island", aliases: &[] }, Region { code: "SC", name: "South Carolina", aliases: &[] }, Region { code: "SD", name: "South Dakota", aliases: &[] }, Region { code: "TN", name: "Tennessee", aliases: &[] }, Region { code: "TX", name: "Texas", aliases: &[] }, Region { code: "UT", name: "Utah", aliases: &[] }, Region { code: "VT", name: "Vermont", aliases: &[] }, Region { code: "VA", name: "Virginia", aliases: &[] }, Region { code: "WA", name: "Washington", aliases: &[] }, Region { code: "WV", name: "West Virginia", aliases: &[] }, Region { code: "WI", name: "Wisconsin", aliases: &[] }, Region { code: "WY", name: "Wyoming", aliases: &[] }, Region { code: "PR", name: "Puerto Rico", aliases: &[] }, Region { code: "GU", name: "Guam", aliases: &[] }, Region { code: "VI", name: "U.S. Virgin Islands", aliases: &["VIRGIN ISLANDS"] }, Region { code: "AS", name: "American Samoa", aliases: &[] }, Region { code: "MP", name: "Northern Mariana Islands", aliases: &[] }]),
    ("CA", &[Region { code: "AB", name: "Alberta", aliases: &[] }, Region { code: "BC", name: "British Columbia", aliases: &[] }, Region { code: "MB", name: "Manitoba", aliases: &[] }, Region { code: "NB", name: "New Brunswick", aliases: &[] }, Region { code: "NL", name: "Newfoundland and Labrador", aliases: &["NEWFOUNDLAND"] }, Region { code: "NS", name: "Nova Scotia", aliases: &[] }, Region { code: "NT", name: "Northwest Territories", aliases: &[] }, Region { code: "NU", name: "Nunavut", aliases: &[] }, Region { code: "ON", name: "Ontario", aliases: &[] }, Region { code: "PE", name: "Prince Edward Island", aliases: &["PEI"] }, Region { code: "QC", name: "Quebec", aliases: &["QUÉBEC", "PQ"] }, Region { code: "SK", name: "Saskatchewan", aliases: &[] }, Region { code: "YT", name: "Yukon", aliases: &["YUKON TERRITORY"] }]),
    ("AU", &[Region { code: "NSW", name: "New South Wales", aliases: &[] }, Region { code: "VIC", name: "Victoria", aliases: &[] }, Region { code: "QLD", name: "Queensland", aliases: &[] }, Region { code: "SA", name: "South Australia", aliases: &[] }, Region { code: "WA", name: "Western Australia", aliases: &[] }, Region { code: "TAS", name: "Tasmania", aliases: &[] }, Region { code: "NT", name: "Northern Territory", aliases: &[] }, Region { code: "ACT", name: "Australian Capital Territory", aliases: &["CANBERRA"] }]),
];

/// norm(Java norm: trim + toUpperCase(Locale.ROOT))
pub fn norm(s: &str) -> String {
    s.trim().to_uppercase()
}

/// code 是否在目录内
pub fn is_known_code(code: &str) -> bool {
    let c = code.trim().to_uppercase();
    COUNTRIES.iter().any(|k| k.code == c)
}

/// 英文名/别名 → code(norm 匹配;对齐 NAME_TO_CODE)
pub fn resolve_code(text: &str) -> Option<&'static str> {
    let n = norm(text);
    COUNTRIES
        .iter()
        .find(|k| norm(k.name) == n || k.aliases.iter().any(|a| norm(a) == n))
        .map(|k| k.code)
}

pub fn by_code(code: &str) -> Option<&'static Country> {
    let c = code.trim().to_uppercase();
    COUNTRIES.iter().find(|k| k.code == c)
}

/// 该国是否有州省字典(仅 US/CA/AU)
pub fn has_regions(code: &str) -> bool {
    REGIONS.iter().any(|(c, _)| *c == code)
}

/// 州/省名/缩写/别名 → 标准码(对齐 resolveRegionCode:code 精确 || norm(name) || norm(alias))
pub fn resolve_region_code(country: &str, input: &str) -> Option<&'static str> {
    let regions = REGIONS.iter().find(|(c, _)| *c == country)?.1;
    let i = input.trim();
    if let Some(r) = regions.iter().find(|r| r.code.eq_ignore_ascii_case(i)) {
        return Some(r.code);
    }
    let n = norm(i);
    regions
        .iter()
        .find(|r| norm(r.name) == n || r.aliases.iter().any(|a| norm(a) == n))
        .map(|r| r.code)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn total_countries_250() {
        assert_eq!(COUNTRIES.len(), 250, "全量 249 国 + 本表 250 条对齐 Java");
    }

    #[test]
    fn zones_exact_eight() {
        assert_eq!(ZONES.len(), 8);
        assert!(COUNTRIES.iter().all(|c| ZONES.contains(&c.zone)));
    }

    #[test]
    fn alias_resolution() {
        assert_eq!(resolve_code("USA"), Some("US"));
        assert_eq!(resolve_code("U.S."), Some("US"));
        assert_eq!(resolve_code("PRC"), Some("CN"));
        assert_eq!(resolve_code("The Netherlands"), Some("NL"));
        assert_eq!(resolve_code("Nowhere Land"), None);
    }

    #[test]
    fn region_resolution_us_ca_au() {
        assert_eq!(resolve_region_code("US", "New York"), Some("NY"));
        assert_eq!(resolve_region_code("US", "new york"), Some("NY"));
        assert_eq!(resolve_region_code("US", "CALIF"), Some("CA"));
        assert_eq!(resolve_region_code("US", "D.C."), Some("DC"));
        assert_eq!(resolve_region_code("CA", "PEI"), Some("PE"));
        assert_eq!(resolve_region_code("CA", "Québec"), Some("QC"));
        assert_eq!(resolve_region_code("AU", "Canberra"), Some("ACT"));
        assert_eq!(resolve_region_code("US", "Atlantis"), None);
        assert!(!has_regions("FR"));
        assert!(has_regions("AU"));
    }
}
