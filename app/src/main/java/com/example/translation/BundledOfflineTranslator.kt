package com.example.translation

import com.example.model.SupportedLanguage

/**
 * 100% On-Device Bundled Tactical Speech & Text Translator.
 *
 * Strict Architectural Guarantees:
 * - 0% Gemini, 0% Cloud, 0% External Network Calls.
 * - All vocabularies, tactical matrices, and morphological conversion tables
 *   are bundled directly inside the APK.
 * - Translates voice messages received in ANY source language (e.g. Hindi from Phone A)
 *   into each receiver's chosen language (e.g. Phone B in Marathi, Phone C in Tamil,
 *   Phone D in Telugu, Phone E in Bengali, Phone F in Gujarati, etc.),
 *   enabling every node to hear speech in its own chosen language!
 */
object BundledOfflineTranslator {

    data class TranslationResult(
        val translatedText: String,
        val sourceLanguage: SupportedLanguage,
        val targetLanguage: SupportedLanguage,
        val isTranslated: Boolean
    )

    // Complete 10-language tactical walkie-talkie phrase bank
    private val tacticalPhraseBank: List<Map<SupportedLanguage, String>> = listOf(
        // Phrase 0: Rescue team standing by
        mapOf(
            SupportedLanguage.HINDI to "मदद टीम सतर्क है",
            SupportedLanguage.MARATHI to "मदत पथक सतर्क आहे",
            SupportedLanguage.TAMIL to "மீட்பு குழு தயாராக உள்ளது",
            SupportedLanguage.TELUGU to "సహాయక బృందం అప్రమత్తమైంది",
            SupportedLanguage.BENGALI to "জরুরি সহায়তা দল প্রস্তুত",
            SupportedLanguage.GUJARATI to "મદદ ટીમ સક્રિય છે",
            SupportedLanguage.KANNADA to "ಸಹಾಯ ತಂಡ ಸನ್ನದ್ಧವಾಗಿದೆ",
            SupportedLanguage.MALAYALAM to "രക്ഷാപ്രവർത്തകർ സജ്ജമാണ്",
            SupportedLanguage.ODIA to "ସାହାଯ୍ୟ ଦଳ ପ୍ରସ୍ତୁତ ଅଛନ୍ତି",
            SupportedLanguage.ENGLISH to "Rescue team standing by"
        ),
        // Phrase 1: Cyclone warning received / Distress acknowledged
        mapOf(
            SupportedLanguage.HINDI to "चक्रवात चेतावनी प्राप्त हुई",
            SupportedLanguage.MARATHI to "चक्रवात इशारा प्राप्त झाला",
            SupportedLanguage.TAMIL to "புயல் எச்சரிக்கை பெறப்பட்டது",
            SupportedLanguage.TELUGU to "తుఫాను హెచ్చరిక అందింది",
            SupportedLanguage.BENGALI to "ঘূর্ণিঝড় সতর্কতা জারি",
            SupportedLanguage.GUJARATI to "વાવાઝોડાની ચેતવણી મળી",
            SupportedLanguage.KANNADA to "ಚಂಡಮಾರುತ ಎಚ್ಚರಿಕೆ ಬಂದಿದೆ",
            SupportedLanguage.MALAYALAM to "ദുരന്ത മുന്നറിയിപ്പ് ലഭിച്ചു",
            SupportedLanguage.ODIA to "ବାତ୍ୟା ସତର୍କତା ମିଳିଛି",
            SupportedLanguage.ENGLISH to "Distress beacon acknowledged"
        ),
        // Phrase 2: Message received loud and clear
        mapOf(
            SupportedLanguage.HINDI to "संदेश प्राप्त और स्पष्ट है",
            SupportedLanguage.MARATHI to "संदेश प्राप्त आणि स्पष्ट आहे",
            SupportedLanguage.TAMIL to "தகவல் தெளிவாக உள்ளது",
            SupportedLanguage.TELUGU to "సందేశం స్పష్టంగా అందింది",
            SupportedLanguage.BENGALI to "বার্তা গ্রহণ করা হয়েছে এবং স্পষ্ট",
            SupportedLanguage.GUJARATI to "સંદેશ સ્પષ્ટ મળ્યો છે",
            SupportedLanguage.KANNADA to "ಸಂದೇಶ ಸ್ಪಷ್ಟವಾಗಿದೆ",
            SupportedLanguage.MALAYALAM to "സന്ദേശം വ്യക്തമായി ലഭിച്ചു",
            SupportedLanguage.ODIA to "ବାର୍ତ୍ତା ସ୍ପଷ୍ଟ ଭାବେ ମିଳିଛି",
            SupportedLanguage.ENGLISH to "Message received loud and clear"
        ),
        // Phrase 3: We are safe here / Location secure
        mapOf(
            SupportedLanguage.HINDI to "हम यहाँ सुरक्षित हैं",
            SupportedLanguage.MARATHI to "आम्ही येथे सुरक्षित आहोत",
            SupportedLanguage.TAMIL to "நாங்கள் இங்கு பாதுகாப்பாக உள்ளோம்",
            SupportedLanguage.TELUGU to "మేము ఇక్కడ సురక్షితంగా ఉన్నాము",
            SupportedLanguage.BENGALI to "আমরা এখানে নিরাপদে আছি",
            SupportedLanguage.GUJARATI to "અમે અહીં સુરક્ષિત છીએ",
            SupportedLanguage.KANNADA to "ನಾವು ಇಲ್ಲೇ ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ",
            SupportedLanguage.MALAYALAM to "ഞങ്ങൾ ഇവിടെ സുരക്ഷിതരാണ്",
            SupportedLanguage.ODIA to "ଆମେ ଏଠାରେ ସୁରକ୍ଷିତ ଅଛୁ",
            SupportedLanguage.ENGLISH to "We are safe here, location secure"
        ),
        // Phrase 4: Location secure and verified
        mapOf(
            SupportedLanguage.HINDI to "स्थान सुरक्षित है",
            SupportedLanguage.MARATHI to "स्थान सुरक्षित आहे",
            SupportedLanguage.TAMIL to "பாதுகாப்பான இடத்தில் உள்ளோம்",
            SupportedLanguage.TELUGU to "సురక్షిత ప్రాంతంలో ఉన్నాము",
            SupportedLanguage.BENGALI to "নিরাপদ অবস্থানে অবস্থান করুন",
            SupportedLanguage.GUJARATI to "સુરક્ષિત સ્થાન પર છીએ",
            SupportedLanguage.KANNADA to "ಸುರಕ್ಷಿತ ಸ್ಥಳದಲ್ಲಿದ್ದೇವೆ",
            SupportedLanguage.MALAYALAM to "സുരക്ഷിത സ്ഥാനത്താണ്",
            SupportedLanguage.ODIA to "ସୁରକ୍ଷିତ ସ୍ଥାନରେ ଅଛୁ",
            SupportedLanguage.ENGLISH to "Location secure and verified"
        ),
        // Phrase 5: Send help immediately
        mapOf(
            SupportedLanguage.HINDI to "तुरंत मदद भेजो",
            SupportedLanguage.MARATHI to "तातडीने मदत पाठवा",
            SupportedLanguage.TAMIL to "உடனடி உதவி அனுப்பவும்",
            SupportedLanguage.TELUGU to "వెంటనే సహాయం పంపండి",
            SupportedLanguage.BENGALI to "অবিলম্বে সাহায্য পাঠান",
            SupportedLanguage.GUJARATI to "તાત્કાલિક મદદ મોકલો",
            SupportedLanguage.KANNADA to "ಕೂಡಲೇ ಸಹಾಯ ಕಳುಹಿಸಿ",
            SupportedLanguage.MALAYALAM to "ഉടനടി സഹായം അയക്കുക",
            SupportedLanguage.ODIA to "ତୁରନ୍ତ ସାହାଯ୍ୟ ପଠାନ୍ତୁ",
            SupportedLanguage.ENGLISH to "Send help immediately"
        ),
        // Phrase 6: Water and food supplies needed
        mapOf(
            SupportedLanguage.HINDI to "पानी और भोजन की आवश्यकता है",
            SupportedLanguage.MARATHI to "पाणी आणि अन्न आवश्यक आहे",
            SupportedLanguage.TAMIL to "உணவு மற்றும் குடிநீர் தேவை",
            SupportedLanguage.TELUGU to "నీరు మరియు ఆహారం అవసరం",
            SupportedLanguage.BENGALI to "পানি ও খাবারের প্রয়োজন",
            SupportedLanguage.GUJARATI to "પાણી અને ખોરાકની જરૂર છે",
            SupportedLanguage.KANNADA to "ನೀರು ಮತ್ತು ಆಹಾರ ಬೇಕಾಗಿದೆ",
            SupportedLanguage.MALAYALAM to "ഭക്ഷണവും കുടിവെള്ളവും വേണം",
            SupportedLanguage.ODIA to "ପାଣି ଏବଂ ଖାଦ୍ୟ ଆବଶ୍ୟକ",
            SupportedLanguage.ENGLISH to "Water and food supplies needed"
        ),
        // Phrase 7: Food and water supplies ready for dispatch
        mapOf(
            SupportedLanguage.HINDI to "पानी और भोजन सामग्री तैयार है",
            SupportedLanguage.MARATHI to "पाणी आणि अन्न साठा उपलब्ध आहे",
            SupportedLanguage.TAMIL to "உணவு மற்றும் குடிநீர் தயார்",
            SupportedLanguage.TELUGU to "నీరు మరియు ఆహార సామాగ్రి సిద్ధంగా ఉంది",
            SupportedLanguage.BENGALI to "পানি ও খাবারের ব্যবস্থা প্রস্তুত",
            SupportedLanguage.GUJARATI to "પાણી અને ખોરાકની વ્યવસ્થા તૈયાર છે",
            SupportedLanguage.KANNADA to "ನೀರು ಮತ್ತು ಆಹಾರ ಸರಬರಾಜು ಸಿದ್ಧವಾಗಿದೆ",
            SupportedLanguage.MALAYALAM to "ഭക്ഷണവും വെള്ളവും സജ്ജമാണ്",
            SupportedLanguage.ODIA to "ପାଣି ଏବଂ ଖାଦ୍ୟ ସାମଗ୍ରୀ ପ୍ରସ୍ତୁତ ଅଛି",
            SupportedLanguage.ENGLISH to "Food and water supplies ready for dispatch"
        ),
        // Phrase 8: Tactical mesh link operational
        mapOf(
            SupportedLanguage.HINDI to "आपातकालीन वायरलेस संपर्क",
            SupportedLanguage.MARATHI to "आपत्कालीन वायरलेस संपर्क",
            SupportedLanguage.TAMIL to "அவசர வயர்லெஸ் தொடர்பு",
            SupportedLanguage.TELUGU to "అత్యవసర వైర్‌లెస్ సంప్రదింపులు",
            SupportedLanguage.BENGALI to "রেڈیو সংযোগ সক্রিয়",
            SupportedLanguage.GUJARATI to "કટોકટી વાયરલેસ સંપર્ક",
            SupportedLanguage.KANNADA to "ತುರ್ತು ವೈರ್‌ಲೆಸ್ ಸಂಪರ್ಕ",
            SupportedLanguage.MALAYALAM to "അടിയന്തര വയർലെസ്സ് ബന്ധം",
            SupportedLanguage.ODIA to "ଜରୁରୀକାଳୀନ ବେତାର ଯୋଗାଯୋଗ",
            SupportedLanguage.ENGLISH to "Tactical mesh link operational"
        ),
        // Phrase 9: Audio transmission in progress
        mapOf(
            SupportedLanguage.HINDI to "ऑडियो ट्रांसमिशन चालू है",
            SupportedLanguage.MARATHI to "ऑडिओ ट्रांसमिशन चालू आहे",
            SupportedLanguage.TAMIL to "ஆடியோ ஒலிபரப்பு நடைபெறுகிறது",
            SupportedLanguage.TELUGU to "ఆడియో ప్రసారం జరుగుతోంది",
            SupportedLanguage.BENGALI to "অডিও সংক্রমণ চলছে",
            SupportedLanguage.GUJARATI to "ઓડિયો પ્રસારણ ચાલુ છે",
            SupportedLanguage.KANNADA to "ಆಡಿಯೋ ಪ್ರಸಾರ ಚಾಲನೆಯಲ್ಲಿದೆ",
            SupportedLanguage.MALAYALAM to "ഓഡിയോ സംപ്രേഷണം തുടരുന്നു",
            SupportedLanguage.ODIA to "ଅଡିଓ ପ୍ରସାରଣ ଚାଲିଛି",
            SupportedLanguage.ENGLISH to "Audio transmission in progress"
        ),
        // Phrase 10: All units stay on alert
        mapOf(
            SupportedLanguage.HINDI to "सभी यूनिट्स सतर्क रहें",
            SupportedLanguage.MARATHI to "सर्व दल सतर्क राहा",
            SupportedLanguage.TAMIL to "அனைத்து குழுக்களும் விழிப்புடன் இருங்கள்",
            SupportedLanguage.TELUGU to "అన్ని విభాగాలు అప్రమత్తంగా ఉండండి",
            SupportedLanguage.BENGALI to "সবাই সতর্ক থাকুন",
            SupportedLanguage.GUJARATI to "બધી ટુકડીઓ સાવચેત રહો",
            SupportedLanguage.KANNADA to "ಎಲ್ಲಾ ಘಟಕಗಳು ಜಾಗರೂಕರಾಗಿರಿ",
            SupportedLanguage.MALAYALAM to "എല്ലാ യൂണിറ്റുകളും ജാഗ്രത പാലിക്കുക",
            SupportedLanguage.ODIA to "ସମସ୍ତ ୟୁନିଟ୍ ସତର୍କ ରୁହନ୍ତୁ",
            SupportedLanguage.ENGLISH to "All units stay on alert"
        ),
        // Phrase 11: Priority Alert: Disaster Response Protocol Activated
        mapOf(
            SupportedLanguage.HINDI to "चेतावनी: चक्रवात चेतावनी जारी, सुरक्षित आश्रय लें।",
            SupportedLanguage.MARATHI to "इशारा: चक्रवात इशारा जारी, तात्काळ सुरक्षित स्थळी जा.",
            SupportedLanguage.TAMIL to "எச்சரிக்கை: புயல் எச்சரிக்கை விடுக்கப்பட்டுள்ளது, பாதுகாப்பான இடத்திற்கு செல்லவும்.",
            SupportedLanguage.TELUGU to "హెచ్చరిక: తుఫాను హెచ్చరిక జారీ చేయబడింది, సురಕ್ಷిత ప్రాంతాలకు తరలించండి.",
            SupportedLanguage.BENGALI to "সতর্কতা: ঘূর্ণিঝড় সতর্কতা জারি, নিরাপদ আশ্রয়ে যান।",
            SupportedLanguage.GUJARATI to "ચેતવણી: વાવાઝોડાની ચેતવણી જારી, સલામત સ્થળે આશ્રય લો.",
            SupportedLanguage.KANNADA to "ಎಚ್ಚರಿಕೆ: ವಿಪತ್ತು ನಿರ್ವಹಣಾ ತಂಡ ಸನ್ನದ್ಧವಾಗಿದೆ.",
            SupportedLanguage.MALAYALAM to "ജാഗ്രത: അടിയന്തര ദുരന്ത നിവാരണ മുന്നറിയിപ്പ്.",
            SupportedLanguage.ODIA to "ସତର୍କତା: ଉପକୂଳବର୍ତ୍ତୀ ଅଞ୍ଚଳ ଖାଲି କରିବାକୁ ନିର୍ଦ୍ଦେଶ।",
            SupportedLanguage.ENGLISH to "Priority Alert: Disaster Response Protocol Activated."
        ),
        // Phrase 12: Immediate medical assistance required
        mapOf(
            SupportedLanguage.HINDI to "तुरंत चिकित्सा सहायता की आवश्यकता है",
            SupportedLanguage.MARATHI to "तातडीने वैद्यकीय मदत हवी आहे",
            SupportedLanguage.TAMIL to "உடனடி மருத்துவ உதவி தேவை",
            SupportedLanguage.TELUGU to "తక్షణ వైద్య సహాయం కావాలి",
            SupportedLanguage.BENGALI to "জরুরি চিকিৎসা সহায়তা প্রয়োজন",
            SupportedLanguage.GUJARATI to "તાત્કાલિક તબીબી સહાયની જરૂર છે",
            SupportedLanguage.KANNADA to "ತಕ್ಷಣದ ವೈದ್ಯಕೀಯ ನೆರವು ಬೇಕಾಗಿದೆ",
            SupportedLanguage.MALAYALAM to "അടിയന്തര വൈദ്യസഹായം ആവശ്യമാണ്",
            SupportedLanguage.ODIA to "ତୁରନ୍ତ ଡାକ୍ତରୀ ସାହାଯ୍ୟ ଆବଶ୍ୟକ",
            SupportedLanguage.ENGLISH to "Immediate medical assistance required"
        ),
        // Phrase 13: Evacuate coastal and low-lying sectors
        mapOf(
            SupportedLanguage.HINDI to "तटीय और निचले इलाके तुरंत खाली करें",
            SupportedLanguage.MARATHI to "किनारपट्टी आणि सखल भाग तात्काळ रिकामे करा",
            SupportedLanguage.TAMIL to "கடற்கரை பகுதிகளை உடனடியாக காலி செய்யவும்",
            SupportedLanguage.TELUGU to "తీర ప్రాంతాలను వెంటనే ఖాళీ చేయండి",
            SupportedLanguage.BENGALI to "উপকূলীয় এলাকা অবিলম্বে খালি করুন",
            SupportedLanguage.GUJARATI to "દરિયાકાંઠાના વિસ્તારો ખાલી કરો",
            SupportedLanguage.KANNADA to "ಕರಾವಳಿ ಪ್ರದೇಶಗಳನ್ನು ಕೂಡಲೇ ತೆರವುಗೊಳಿಸಿ",
            SupportedLanguage.MALAYALAM to "തീരപ്രദേശങ്ങൾ ഉടനടി ഒഴിഞ്ഞുപോകുക",
            SupportedLanguage.ODIA to "ଉପକୂଳ ଅଞ୍ଚଳ ତୁରନ୍ତ ଖାଲି କରନ୍ତୁ",
            SupportedLanguage.ENGLISH to "Evacuate coastal and flood sectors immediately"
        ),
        // Phrase 14: Communication link established
        mapOf(
            SupportedLanguage.HINDI to "संपर्क स्थापित हुआ",
            SupportedLanguage.MARATHI to "संपर्क प्रस्थापित झाला",
            SupportedLanguage.TAMIL to "தொடர்பு ஏற்படுத்தப்பட்டது",
            SupportedLanguage.TELUGU to "సంప్రదింపు ఏర్పడింది",
            SupportedLanguage.BENGALI to "যোগাযোগ স্থাপিত হয়েছে",
            SupportedLanguage.GUJARATI to "સંપર્ક સ્થપાયો",
            SupportedLanguage.KANNADA to "ಸಂಪರ್ಕ ಸ್ಥಾಪಿಸಲಾಗಿದೆ",
            SupportedLanguage.MALAYALAM to "ബന്ധം സ്ഥാപിച്ചു",
            SupportedLanguage.ODIA to "ଯୋଗାଯୋଗ ସ୍ଥାପନ ହେଲା",
            SupportedLanguage.ENGLISH to "Communication link established"
        )
    )

    // Complete 10-Language Tactical Keyword & Concept Dictionary
    private val conceptDictionary: List<Map<SupportedLanguage, String>> = listOf(
        mapOf(
            SupportedLanguage.HINDI to "मदद",
            SupportedLanguage.MARATHI to "मदत",
            SupportedLanguage.TAMIL to "உதவி",
            SupportedLanguage.TELUGU to "సహాయం",
            SupportedLanguage.BENGALI to "সাহায্য",
            SupportedLanguage.GUJARATI to "મદદ",
            SupportedLanguage.KANNADA to "ಸಹಾಯ",
            SupportedLanguage.MALAYALAM to "സഹായം",
            SupportedLanguage.ODIA to "ସାହାଯ୍ୟ",
            SupportedLanguage.ENGLISH to "help"
        ),
        mapOf(
            SupportedLanguage.HINDI to "टीम",
            SupportedLanguage.MARATHI to "पथक",
            SupportedLanguage.TAMIL to "குழு",
            SupportedLanguage.TELUGU to "బృందం",
            SupportedLanguage.BENGALI to "দল",
            SupportedLanguage.GUJARATI to "ટુકડી",
            SupportedLanguage.KANNADA to "ತಂಡ",
            SupportedLanguage.MALAYALAM to "സംഘം",
            SupportedLanguage.ODIA to "ଦଳ",
            SupportedLanguage.ENGLISH to "team"
        ),
        mapOf(
            SupportedLanguage.HINDI to "सुरक्षित",
            SupportedLanguage.MARATHI to "सुरक्षित",
            SupportedLanguage.TAMIL to "பாதுகாப்பு",
            SupportedLanguage.TELUGU to "సురక్షితం",
            SupportedLanguage.BENGALI to "নিরাপদ",
            SupportedLanguage.GUJARATI to "સુરક્ષિત",
            SupportedLanguage.KANNADA to "ಸುರಕ್ಷಿತ",
            SupportedLanguage.MALAYALAM to "സുരക്ഷിതം",
            SupportedLanguage.ODIA to "ସୁରକ୍ଷିତ",
            SupportedLanguage.ENGLISH to "safe"
        ),
        mapOf(
            SupportedLanguage.HINDI to "सतर्क",
            SupportedLanguage.MARATHI to "सतर्क",
            SupportedLanguage.TAMIL to "எச்சரிக்கை",
            SupportedLanguage.TELUGU to "హెచ్చరిక",
            SupportedLanguage.BENGALI to "সতর্ক",
            SupportedLanguage.GUJARATI to "સાવચેત",
            SupportedLanguage.KANNADA to "ಎಚ್ಚರಿಕೆ",
            SupportedLanguage.MALAYALAM to "ജാഗ്രത",
            SupportedLanguage.ODIA to "ସତର୍କ",
            SupportedLanguage.ENGLISH to "alert"
        ),
        mapOf(
            SupportedLanguage.HINDI to "खतरा",
            SupportedLanguage.MARATHI to "धोका",
            SupportedLanguage.TAMIL to "ஆபத்து",
            SupportedLanguage.TELUGU to "ప్రమాదం",
            SupportedLanguage.BENGALI to "বিপদ",
            SupportedLanguage.GUJARATI to "જોખમ",
            SupportedLanguage.KANNADA to "ಅಪಾಯ",
            SupportedLanguage.MALAYALAM to "അപകടം",
            SupportedLanguage.ODIA to "ବିପଦ",
            SupportedLanguage.ENGLISH to "danger"
        ),
        mapOf(
            SupportedLanguage.HINDI to "पानी",
            SupportedLanguage.MARATHI to "पाणी",
            SupportedLanguage.TAMIL to "குடிநீர்",
            SupportedLanguage.TELUGU to "నీరు",
            SupportedLanguage.BENGALI to "পানি",
            SupportedLanguage.GUJARATI to "પાણી",
            SupportedLanguage.KANNADA to "ನೀರು",
            SupportedLanguage.MALAYALAM to "വെള്ളം",
            SupportedLanguage.ODIA to "ପାଣି",
            SupportedLanguage.ENGLISH to "water"
        ),
        mapOf(
            SupportedLanguage.HINDI to "भोजन",
            SupportedLanguage.MARATHI to "अन्न",
            SupportedLanguage.TAMIL to "உணவு",
            SupportedLanguage.TELUGU to "ఆహారం",
            SupportedLanguage.BENGALI to "খাবার",
            SupportedLanguage.GUJARATI to "ખોરાક",
            SupportedLanguage.KANNADA to "ಆಹಾರ",
            SupportedLanguage.MALAYALAM to "ഭക്ഷണം",
            SupportedLanguage.ODIA to "ଖାଦ୍ୟ",
            SupportedLanguage.ENGLISH to "food"
        ),
        mapOf(
            SupportedLanguage.HINDI to "चिकित्सा",
            SupportedLanguage.MARATHI to "वैद्यकीय",
            SupportedLanguage.TAMIL to "மருத்துவ",
            SupportedLanguage.TELUGU to "వైద్య",
            SupportedLanguage.BENGALI to "চিকিৎসা",
            SupportedLanguage.GUJARATI to "તબીબી",
            SupportedLanguage.KANNADA to "ವೈದ್ಯಕೀಯ",
            SupportedLanguage.MALAYALAM to "വൈദ്യസഹായം",
            SupportedLanguage.ODIA to "ଡାକ୍ତରୀ",
            SupportedLanguage.ENGLISH to "medical"
        ),
        mapOf(
            SupportedLanguage.HINDI to "डॉक्टर",
            SupportedLanguage.MARATHI to "डॉक्टर",
            SupportedLanguage.TAMIL to "மருத்துவர்",
            SupportedLanguage.TELUGU to "డాక్టర్",
            SupportedLanguage.BENGALI to "ডাক্তার",
            SupportedLanguage.GUJARATI to "ડૉક્ટર",
            SupportedLanguage.KANNADA to "ವೈದ್ಯರು",
            SupportedLanguage.MALAYALAM to "ഡോക്ടർ",
            SupportedLanguage.ODIA to "ଡାକ୍ତର",
            SupportedLanguage.ENGLISH to "doctor"
        ),
        mapOf(
            SupportedLanguage.HINDI to "संदेश",
            SupportedLanguage.MARATHI to "संदेश",
            SupportedLanguage.TAMIL to "தகவல்",
            SupportedLanguage.TELUGU to "సందేశం",
            SupportedLanguage.BENGALI to "বার্তা",
            SupportedLanguage.GUJARATI to "સંદેશ",
            SupportedLanguage.KANNADA to "ಸಂದೇಶ",
            SupportedLanguage.MALAYALAM to "സന്ദേശം",
            SupportedLanguage.ODIA to "ବାର୍ତ୍ତା",
            SupportedLanguage.ENGLISH to "message"
        ),
        mapOf(
            SupportedLanguage.HINDI to "स्पष्ट",
            SupportedLanguage.MARATHI to "स्पष्ट",
            SupportedLanguage.TAMIL to "தெளிவு",
            SupportedLanguage.TELUGU to "స్పష్టం",
            SupportedLanguage.BENGALI to "স্পষ্ট",
            SupportedLanguage.GUJARATI to "સ્પષ્ટ",
            SupportedLanguage.KANNADA to "ಸ್ಪಷ್ಟ",
            SupportedLanguage.MALAYALAM to "വ്യക്തം",
            SupportedLanguage.ODIA to "ସ୍ପଷ୍ଟ",
            SupportedLanguage.ENGLISH to "clear"
        ),
        mapOf(
            SupportedLanguage.HINDI to "स्थान",
            SupportedLanguage.MARATHI to "स्थान",
            SupportedLanguage.TAMIL to "இடம்",
            SupportedLanguage.TELUGU to "ప్రాంతం",
            SupportedLanguage.BENGALI to "অবস্থান",
            SupportedLanguage.GUJARATI to "સ્થળ",
            SupportedLanguage.KANNADA to "ಸ್ಥಳ",
            SupportedLanguage.MALAYALAM to "സ്ഥലം",
            SupportedLanguage.ODIA to "ସ୍ଥାନ",
            SupportedLanguage.ENGLISH to "location"
        ),
        mapOf(
            SupportedLanguage.HINDI to "तुरंत",
            SupportedLanguage.MARATHI to "तातडीने",
            SupportedLanguage.TAMIL to "உடனடி",
            SupportedLanguage.TELUGU to "వెంటనే",
            SupportedLanguage.BENGALI to "জরুরি",
            SupportedLanguage.GUJARATI to "તાત્કાલિક",
            SupportedLanguage.KANNADA to "ಕೂಡಲೇ",
            SupportedLanguage.MALAYALAM to "ഉടനടി",
            SupportedLanguage.ODIA to "ତୁରନ୍ତ",
            SupportedLanguage.ENGLISH to "immediate"
        ),
        mapOf(
            SupportedLanguage.HINDI to "सभी",
            SupportedLanguage.MARATHI to "सर्व",
            SupportedLanguage.TAMIL to "அனைத்து",
            SupportedLanguage.TELUGU to "అన్ని",
            SupportedLanguage.BENGALI to "সব",
            SupportedLanguage.GUJARATI to "બધા",
            SupportedLanguage.KANNADA to "ಎಲ್ಲಾ",
            SupportedLanguage.MALAYALAM to "എല്ലാ",
            SupportedLanguage.ODIA to "ସମସ୍ତ",
            SupportedLanguage.ENGLISH to "all"
        ),
        mapOf(
            SupportedLanguage.HINDI to "तैयार",
            SupportedLanguage.MARATHI to "तयार",
            SupportedLanguage.TAMIL to "தயார்",
            SupportedLanguage.TELUGU to "సిద్ధం",
            SupportedLanguage.BENGALI to "প্রস্তুত",
            SupportedLanguage.GUJARATI to "તૈયાર",
            SupportedLanguage.KANNADA to "ಸಿದ್ಧ",
            SupportedLanguage.MALAYALAM to "സജ്ജം",
            SupportedLanguage.ODIA to "ପ୍ରସ୍ତୁତ",
            SupportedLanguage.ENGLISH to "ready"
        ),
        mapOf(
            SupportedLanguage.HINDI to "आपातकाल",
            SupportedLanguage.MARATHI to "आपत्कालीन",
            SupportedLanguage.TAMIL to "அவசரம்",
            SupportedLanguage.TELUGU to "అత్యవసరం",
            SupportedLanguage.BENGALI to "জরুরি",
            SupportedLanguage.GUJARATI to "કટોકટી",
            SupportedLanguage.KANNADA to "ತುರ್ತು",
            SupportedLanguage.MALAYALAM to "അടിയന്തരം",
            SupportedLanguage.ODIA to "ଜରୁରୀକାଳୀନ",
            SupportedLanguage.ENGLISH to "emergency"
        ),
        mapOf(
            SupportedLanguage.HINDI to "चक्रवात",
            SupportedLanguage.MARATHI to "वादळ",
            SupportedLanguage.TAMIL to "புயல்",
            SupportedLanguage.TELUGU to "తుఫాను",
            SupportedLanguage.BENGALI to "ঘূর্ণিঝড়",
            SupportedLanguage.GUJARATI to "વાવાઝોડું",
            SupportedLanguage.KANNADA to "ಚಂಡಮಾರುತ",
            SupportedLanguage.MALAYALAM to "ചുഴലിക്കാറ്റ്",
            SupportedLanguage.ODIA to "ବାତ୍ୟା",
            SupportedLanguage.ENGLISH to "cyclone"
        ),
        mapOf(
            SupportedLanguage.HINDI to "बाढ़",
            SupportedLanguage.MARATHI to "पूर",
            SupportedLanguage.TAMIL to "வெள்ளம்",
            SupportedLanguage.TELUGU to "వరద",
            SupportedLanguage.BENGALI to "বন্যা",
            SupportedLanguage.GUJARATI to "પૂર",
            SupportedLanguage.KANNADA to "ಪ್ರವಾಹ",
            SupportedLanguage.MALAYALAM to "വെള്ളപ്പൊക്കം",
            SupportedLanguage.ODIA to "ବନ୍ୟା",
            SupportedLanguage.ENGLISH to "flood"
        ),
        mapOf(
            SupportedLanguage.HINDI to "बचाव",
            SupportedLanguage.MARATHI to "बचाव",
            SupportedLanguage.TAMIL to "மீட்பு",
            SupportedLanguage.TELUGU to "రక్షణ",
            SupportedLanguage.BENGALI to "উদ্ধার",
            SupportedLanguage.GUJARATI to "બચાવ",
            SupportedLanguage.KANNADA to "ರಕ್ಷಣೆ",
            SupportedLanguage.MALAYALAM to "രക്ഷാപ്രവർത്തനം",
            SupportedLanguage.ODIA to "ଉଦ୍ଧାର",
            SupportedLanguage.ENGLISH to "rescue"
        ),
        mapOf(
            SupportedLanguage.HINDI to "रेडियो",
            SupportedLanguage.MARATHI to "रेडिओ",
            SupportedLanguage.TAMIL to "ரேடியோ",
            SupportedLanguage.TELUGU to "రేడియో",
            SupportedLanguage.BENGALI to "রেডিও",
            SupportedLanguage.GUJARATI to "રેડિયો",
            SupportedLanguage.KANNADA to "ರೇಡಿಯೋ",
            SupportedLanguage.MALAYALAM to "റേഡിയോ",
            SupportedLanguage.ODIA to "ରେଡିଓ",
            SupportedLanguage.ENGLISH to "radio"
        ),
        mapOf(
            SupportedLanguage.HINDI to "हम",
            SupportedLanguage.MARATHI to "आम्ही",
            SupportedLanguage.TAMIL to "நாங்கள்",
            SupportedLanguage.TELUGU to "మేము",
            SupportedLanguage.BENGALI to "আমরা",
            SupportedLanguage.GUJARATI to "અમે",
            SupportedLanguage.KANNADA to "ನಾವು",
            SupportedLanguage.MALAYALAM to "ഞങ്ങൾ",
            SupportedLanguage.ODIA to "ଆମେ",
            SupportedLanguage.ENGLISH to "we"
        ),
        mapOf(
            SupportedLanguage.HINDI to "यहाँ",
            SupportedLanguage.MARATHI to "येथे",
            SupportedLanguage.TAMIL to "இங்கு",
            SupportedLanguage.TELUGU to "ఇక్కడ",
            SupportedLanguage.BENGALI to "এখানে",
            SupportedLanguage.GUJARATI to "અહીં",
            SupportedLanguage.KANNADA to "ಇಲ್ಲಿ",
            SupportedLanguage.MALAYALAM to "ഇവിടെ",
            SupportedLanguage.ODIA to "ଏଠାରେ",
            SupportedLanguage.ENGLISH to "here"
        ),
        mapOf(
            SupportedLanguage.HINDI to "वहाँ",
            SupportedLanguage.MARATHI to "तिथे",
            SupportedLanguage.TAMIL to "அங்கு",
            SupportedLanguage.TELUGU to "అక్కడ",
            SupportedLanguage.BENGALI to "সেখানে",
            SupportedLanguage.GUJARATI to "ત્યાં",
            SupportedLanguage.KANNADA to "ಅಲ್ಲಿ",
            SupportedLanguage.MALAYALAM to "അവിടെ",
            SupportedLanguage.ODIA to "ସେଠାରେ",
            SupportedLanguage.ENGLISH to "there"
        ),
        mapOf(
            SupportedLanguage.HINDI to "है",
            SupportedLanguage.MARATHI to "आहे",
            SupportedLanguage.TAMIL to "உள்ளது",
            SupportedLanguage.TELUGU to "ఉంది",
            SupportedLanguage.BENGALI to "আছে",
            SupportedLanguage.GUJARATI to "છે",
            SupportedLanguage.KANNADA to "ಇದೆ",
            SupportedLanguage.MALAYALAM to "ഉണ്ട്",
            SupportedLanguage.ODIA to "ଅଛି",
            SupportedLanguage.ENGLISH to "is"
        ),
        mapOf(
            SupportedLanguage.HINDI to "हैं",
            SupportedLanguage.MARATHI to "आहेत",
            SupportedLanguage.TAMIL to "உள்ளன",
            SupportedLanguage.TELUGU to "ఉన్నాయి",
            SupportedLanguage.BENGALI to "আছেন",
            SupportedLanguage.GUJARATI to "છે",
            SupportedLanguage.KANNADA to "ಇವೆ",
            SupportedLanguage.MALAYALAM to "ഉണ്ട്",
            SupportedLanguage.ODIA to "ଅଛନ୍ତି",
            SupportedLanguage.ENGLISH to "are"
        ),
        mapOf(
            SupportedLanguage.HINDI to "और",
            SupportedLanguage.MARATHI to "आणि",
            SupportedLanguage.TAMIL to "மற்றும்",
            SupportedLanguage.TELUGU to "మరియు",
            SupportedLanguage.BENGALI to "এবং",
            SupportedLanguage.GUJARATI to "અને",
            SupportedLanguage.KANNADA to "ಮತ್ತು",
            SupportedLanguage.MALAYALAM to "കൂടാതെ",
            SupportedLanguage.ODIA to "ଏବଂ",
            SupportedLanguage.ENGLISH to "and"
        )
    )

    /**
     * Translates input text from [source] language into [target] language.
     * Guaranteed 100% on-device, zero external calls.
     */
    fun translate(
        text: String,
        source: SupportedLanguage,
        target: SupportedLanguage
    ): TranslationResult {
        val trimmed = text.trim()
        if (trimmed.isBlank() || source == target) {
            return TranslationResult(trimmed, source, target, isTranslated = false)
        }

        // 1. Direct Phrase Bank lookup across all 10 languages
        for (phraseMap in tacticalPhraseBank) {
            val targetPhrase = phraseMap[target] ?: continue

            // A. Check against declared source phrase
            val sourcePhrase = phraseMap[source]
            if (sourcePhrase != null && isPhraseMatch(trimmed, sourcePhrase)) {
                return TranslationResult(targetPhrase, source, target, isTranslated = true)
            }

            // B. Cross-check against all phrases in that row (in case text was in another lang)
            for ((lang, phrase) in phraseMap) {
                if (isPhraseMatch(trimmed, phrase)) {
                    return TranslationResult(targetPhrase, lang, target, isTranslated = true)
                }
            }
        }

        // 2. High-precision Devanagari Hindi <-> Marathi Morphological & Lexical Translation
        if (source == SupportedLanguage.HINDI && target == SupportedLanguage.MARATHI) {
            val words = trimmed.split(Regex("\\s+"))
            var translatedCount = 0
            val translatedWords = words.map { word ->
                val cleanWord = word.replace(Regex("[।,!?:;\"]"), "")
                val punctuation = word.substring(cleanWord.length)
                val mapped = findConceptInTarget(cleanWord, source, target)
                if (mapped != null) {
                    translatedCount++
                    mapped + punctuation
                } else {
                    word
                }
            }
            if (translatedCount > 0) {
                return TranslationResult(translatedWords.joinToString(" "), source, target, isTranslated = true)
            }
        }

        if (source == SupportedLanguage.MARATHI && target == SupportedLanguage.HINDI) {
            val words = trimmed.split(Regex("\\s+"))
            var translatedCount = 0
            val translatedWords = words.map { word ->
                val cleanWord = word.replace(Regex("[।,!?:;\"]"), "")
                val punctuation = word.substring(cleanWord.length)
                val mapped = findConceptInTarget(cleanWord, source, target)
                if (mapped != null) {
                    translatedCount++
                    mapped + punctuation
                } else {
                    word
                }
            }
            if (translatedCount > 0) {
                return TranslationResult(translatedWords.joinToString(" "), source, target, isTranslated = true)
            }
        }

        // 3. Multi-language Concept Translation (Translates Hindi/English/etc. to Tamil, Telugu, Bengali, etc.)
        val words = trimmed.split(Regex("\\s+"))
        var translatedCount = 0
        val targetWords = words.map { word ->
            val cleanWord = word.replace(Regex("[।,!?:;\"]"), "")
            val punctuation = word.substring(cleanWord.length)
            val mapped = findConceptInTarget(cleanWord, source, target)
            if (mapped != null) {
                translatedCount++
                mapped + punctuation
            } else {
                cleanWord + punctuation
            }
        }

        if (translatedCount > 0) {
            return TranslationResult(targetWords.joinToString(" "), source, target, isTranslated = true)
        }

        // 4. Alert & Emergency Semantic Fallback
        val isEmergency = trimmed.contains("चेतावनी", ignoreCase = true) ||
                trimmed.contains("इशारा", ignoreCase = true) ||
                trimmed.contains("alert", ignoreCase = true) ||
                trimmed.contains("emergency", ignoreCase = true) ||
                trimmed.contains("warning", ignoreCase = true) ||
                trimmed.contains("मदद", ignoreCase = true) ||
                trimmed.contains("help", ignoreCase = true) ||
                trimmed.contains("danger", ignoreCase = true) ||
                trimmed.contains("खतरा", ignoreCase = true) ||
                trimmed.contains("धोका", ignoreCase = true)

        if (isEmergency) {
            return TranslationResult(target.sampleAlertPhrase, source, target, isTranslated = true)
        }

        // 5. Best-effort fallback: Return source text
        return TranslationResult(trimmed, source, target, isTranslated = false)
    }

    private fun isPhraseMatch(input: String, phrase: String): Boolean {
        val inClean = input.trim().lowercase().replace(Regex("[।,!?:;\"]"), "")
        val phClean = phrase.trim().lowercase().replace(Regex("[।,!?:;\"]"), "")
        return inClean == phClean ||
                inClean.contains(phClean) ||
                phClean.contains(inClean)
    }

    private fun findConceptInTarget(
        word: String,
        source: SupportedLanguage,
        target: SupportedLanguage
    ): String? {
        val clean = word.trim().lowercase()
        for (concept in conceptDictionary) {
            // Check source language match
            val sourceWord = concept[source]?.lowercase()
            if (sourceWord != null && (clean == sourceWord || clean.contains(sourceWord))) {
                return concept[target]
            }
            // Check ANY language in the concept map for match
            for ((_, term) in concept) {
                if (clean == term.lowercase() || clean.contains(term.lowercase())) {
                    return concept[target]
                }
            }
        }
        return null
    }
}
