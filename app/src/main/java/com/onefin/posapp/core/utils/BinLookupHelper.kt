package com.onefin.posapp.core.utils

class BinLookupHelper {
    companion object {
        private val BIN_DATABASE = mapOf(
            // ==================== MAILINH ====================
            "120790" to "MAILINH",
            "120791" to "MAILINH",
            "120792" to "MAILINH",
            "120793" to "MAILINH",
            "120794" to "MAILINH",
            "120795" to "MAILINH",
            "120796" to "MAILINH",
            "120797" to "MAILINH",
            "120798" to "MAILINH",
            "120799" to "MAILINH",

            // ==================== VIETCOMBANK ====================
            "970436" to "Vietcombank",
            "532591" to "Vietcombank",
            "448963" to "Vietcombank",
            "418558" to "Vietcombank",
            "540534" to "Vietcombank",
            "557368" to "Vietcombank",
            "413346" to "Vietcombank",
            "485832" to "Vietcombank",
            "522134" to "Vietcombank",
            "531976" to "Vietcombank",
            "435581" to "Vietcombank",
            "489167" to "Vietcombank",
            "545844" to "Vietcombank",
            "970454" to "Vietcombank", // ATM
            "411235" to "Vietcombank",
            "454780" to "Vietcombank",

            // ==================== TECHCOMBANK ====================
            "970407" to "Techcombank",
            "542382" to "Techcombank",
            "543404" to "Techcombank",
            "411936" to "Techcombank",
            "546791" to "Techcombank",
            "525364" to "Techcombank",
            "537203" to "Techcombank",
            "559027" to "Techcombank", // JCB
            "549217" to "Techcombank",
            "533630" to "Techcombank",
            "489452" to "Techcombank",
            "554180" to "Techcombank",
            "535918" to "Techcombank",

            // ==================== BIDV ====================
            "970418" to "BIDV",
            "532050" to "BIDV",
            "452659" to "BIDV",
            "411203" to "BIDV",
            "489919" to "BIDV",
            "545481" to "BIDV",
            "400952" to "BIDV",
            "461501" to "BIDV",
            "532661" to "BIDV",
            "520332" to "BIDV",
            "545842" to "BIDV",
            "545913" to "BIDV",
            "418885" to "BIDV",

            // ==================== VIETINBANK ====================
            "970415" to "Vietinbank",
            "546034" to "Vietinbank",
            "415686" to "Vietinbank",
            "532869" to "Vietinbank",
            "455126" to "Vietinbank",
            "533106" to "Vietinbank",
            "411571" to "Vietinbank",
            "545866" to "Vietinbank",
            "520332" to "Vietinbank",

            // ==================== MB BANK ====================
            "970422" to "MB Bank",
            "546993" to "MB Bank",
            "535844" to "MB Bank",
            "546707" to "MB Bank",
            "411205" to "MB Bank",
            "522384" to "MB Bank",
            "535860" to "MB Bank",
            "549156" to "MB Bank",
            "532924" to "MB Bank",
            "545340" to "MB Bank",
            "489509" to "MB Bank",

            // ==================== ACB ====================
            "970416" to "ACB",
            "543348" to "ACB",
            "413823" to "ACB",
            "532935" to "ACB",
            "546890" to "ACB",
            "489167" to "ACB",
            "522346" to "ACB",
            "545616" to "ACB",
            "411936" to "ACB",
            "549118" to "ACB",

            // ==================== VPBANK ====================
            "970432" to "VPBank",
            "546762" to "VPBank",
            "455766" to "VPBank",
            "548024" to "VPBank",
            "533429" to "VPBank",
            "522361" to "VPBank",
            "535579" to "VPBank",
            "545699" to "VPBank",
            "549394" to "VPBank",
            "532815" to "VPBank",

            // ==================== TPBANK ====================
            "970423" to "TPBank",
            "548362" to "TPBank",
            "544180" to "TPBank",
            "532670" to "TPBank",
            "418885" to "TPBank",
            "535906" to "TPBank",
            "554180" to "TPBank",
            "549761" to "TPBank",
            "545392" to "TPBank",

            // ==================== SACOMBANK ====================
            "970403" to "Sacombank",
            "554893" to "Sacombank",
            "546973" to "Sacombank",
            "532612" to "Sacombank",
            "451879" to "Sacombank",
            "454433" to "Sacombank",
            "522348" to "Sacombank",
            "545847" to "Sacombank",
            "549178" to "Sacombank",
            "411204" to "Sacombank",

            // ==================== VIB ====================
            "970441" to "VIB",
            "543881" to "VIB",
            "413636" to "VIB",
            "489452" to "VIB",
            "536755" to "VIB",
            "522312" to "VIB",
            "535433" to "VIB",
            "549233" to "VIB",
            "545724" to "VIB",

            // ==================== SHB ====================
            "970443" to "SHB",
            "545340" to "SHB",
            "532748" to "SHB",
            "411200" to "SHB",
            "545913" to "SHB",
            "549459" to "SHB",
            "546368" to "SHB",

            // ==================== HDBANK ====================
            "970437" to "HDBank",
            "548910" to "HDBank",
            "532924" to "HDBank",
            "547232" to "HDBank",
            "418068" to "HDBank",
            "535907" to "HDBank",
            "554910" to "HDBank",
            "545708" to "HDBank",
            "548632" to "HDBank",

            // ==================== AGRIBANK ====================
            "970405" to "Agribank",
            "533106" to "Agribank",
            "545842" to "Agribank",
            "411571" to "Agribank",
            "520332" to "Agribank",
            "525386" to "Agribank",
            "532661" to "Agribank",
            "489919" to "Agribank",

            // ==================== OCB ====================
            "970448" to "OCB",
            "549118" to "OCB",
            "532861" to "OCB",
            "418236" to "OCB",
            "545919" to "OCB",
            "548674" to "OCB",

            // ==================== MSB ====================
            "970426" to "MSB",
            "546400" to "MSB",
            "535274" to "MSB",
            "549761" to "MSB",
            "548508" to "MSB",
            "545641" to "MSB",

            // ==================== SEABANK ====================
            "970440" to "SeABank",
            "548211" to "SeABank",
            "549156" to "SeABank",
            "535388" to "SeABank",
            "545490" to "SeABank",

            // ==================== VIETCAPITALBANK ====================
            "970454" to "VietCapitalBank",
            "411210" to "VietCapitalBank",
            "548674" to "VietCapitalBank",
            "549442" to "VietCapitalBank",

            // ==================== SCB ====================
            "970429" to "SCB",
            "411204" to "SCB",
            "545392" to "SCB",
            "548359" to "SCB",

            // ==================== EXIMBANK ====================
            "970431" to "Eximbank",
            "549317" to "Eximbank",
            "532815" to "Eximbank",
            "413740" to "Eximbank",
            "545866" to "Eximbank",

            // ==================== ABBANK ====================
            "970425" to "ABBank",
            "545708" to "ABBank",
            "546368" to "ABBank",
            "418600" to "ABBank",
            "549394" to "ABBank",

            // ==================== NAMABANK ====================
            "970428" to "NamABank",
            "548359" to "NamABank",
            "535918" to "NamABank",
            "549233" to "NamABank",

            // ==================== PVCOMBANK ====================
            "970412" to "PVcomBank",
            "548508" to "PVcomBank",
            "545699" to "PVcomBank",
            "549178" to "PVcomBank",

            // ==================== LIENVIETPOSTBANK ====================
            "970449" to "LienVietPostBank",
            "549394" to "LienVietPostBank",
            "535929" to "LienVietPostBank",
            "545932" to "LienVietPostBank",

            // ==================== BACABANK ====================
            "970409" to "BacABank",
            "545866" to "BacABank",
            "533183" to "BacABank",
            "549317" to "BacABank",

            // ==================== NCB ====================
            "970419" to "NCB",
            "548632" to "NCB",
            "545913" to "NCB",

            // ==================== SAIGONBANK ====================
            "970400" to "SaigonBank",
            "545919" to "SaigonBank",
            "548674" to "SaigonBank",

            // ==================== VIETABANK ====================
            "970427" to "VietABank",
            "545641" to "VietABank",
            "549459" to "VietABank",

            // ==================== KIENLONGBANK ====================
            "970452" to "Kienlongbank",
            "549233" to "Kienlongbank",
            "545724" to "Kienlongbank",

            // ==================== VIETBANK ====================
            "970433" to "VietBank",
            "545724" to "VietBank",
            "548910" to "VietBank",

            // ==================== GPBANK ====================
            "970408" to "GPBank",
            "549459" to "GPBank",
            "545708" to "GPBank",

            // ==================== DONGA BANK ====================
            "970406" to "DongA Bank",
            "549178" to "DongA Bank",
            "545847" to "DongA Bank",

            // ==================== BAOVIETBANK ====================
            "970438" to "BaoVietBank",
            "549442" to "BaoVietBank",
            "548674" to "BaoVietBank",

            // ==================== VRB ====================
            "970421" to "VRB",
            "411202" to "VRB",
            "545913" to "VRB",

            // ==================== WOORI BANK ====================
            "970457" to "Woori Bank",
            "545490" to "Woori Bank",

            // ==================== SHINHAN BANK ====================
            "970424" to "Shinhan Bank",
            "548364" to "Shinhan Bank",
            "535929" to "Shinhan Bank",

            // ==================== HSBC ====================
            "970442" to "HSBC",
            "545490" to "HSBC",
            "489509" to "HSBC",

            // ==================== HONG LEONG BANK ====================
            "970444" to "Hong Leong Bank",
            "545616" to "Hong Leong Bank",

            // ==================== PUBLIC BANK ====================
            "970439" to "Public Bank",
            "549118" to "Public Bank",

            // ==================== CIMB BANK ====================
            "422589" to "CIMB Bank",
            "545699" to "CIMB Bank",

            // ==================== ANZ ====================
            "970434" to "ANZ",
            "545490" to "ANZ",

            // ==================== UOB ====================
            "970458" to "UOB",
            "545616" to "UOB",

            // ==================== CITIBANK ====================
            "545616" to "Citibank",
            "489509" to "Citibank",
            "411936" to "Citibank",

            // ==================== OCBC ====================
            "970430" to "OCBC",
            "545490" to "OCBC",

            // ==================== CO-OPBANK ====================
            "970446" to "Co-opBank",
            "545932" to "Co-opBank",
            "549394" to "Co-opBank",

            // ==================== OCEANBANK ====================
            "970414" to "OceanBank",
            "545847" to "OceanBank",
            "549178" to "OceanBank",

            // ==================== ADDITIONAL COMMON BINS ====================
            // Visa Debit/Credit ranges for Vietnam
            "456789" to "Unknown Bank (Visa)",
            "434567" to "Unknown Bank (Visa)",
            "465432" to "Unknown Bank (Visa)",

            // Mastercard ranges
            "512345" to "Unknown Bank (Mastercard)",
            "523456" to "Unknown Bank (Mastercard)",
            "534567" to "Unknown Bank (Mastercard)",
            "545678" to "Unknown Bank (Mastercard)",

            // JCB ranges
            "352812" to "Unknown Bank (JCB)",
            "358901" to "Unknown Bank (JCB)"
        )

        fun lookupIssuer(pan: String): String? {
            if (pan.length < 6) return null
            val bin6 = pan.substring(0, 6)
            return BIN_DATABASE[bin6]
        }
    }
}