package com.zoomscanner.app

import java.io.File

/** Tous les outils de l'écran « Outils ». `ready = false` : prévu pour une prochaine version. */
enum class Tool(val label: String, val icon: String, val ready: Boolean = true) {
    // Scanner
    SCAN("Scanner", "📄"),
    ID_CARD("Cartes d'identité", "🪪"),
    OCR("Extraire Tx.", "🔤"),
    ID_PHOTO("Créateur de photos d'identité", "🧑", false),
    FORMULA("Formule", "➗", false),
    TRANSLATE("Convertir des Photos", "🌐", false),
    BOOK("Livre", "📖", false),
    SLIDES("Diapositives", "📊", false),
    WHITEBOARD("Tableau blanc", "🧾", false),
    TIMESTAMP("Horodatage", "🕒"),

    // Importer
    IMPORT_IMAGES("Importer des images", "🖼️"),
    IMPORT_FILES("Importer des fichiers", "📂"),

    // Convertir
    TO_WORD("Au Word", "📝", false),
    TO_EXCEL("Au Excel", "📈", false),
    TO_PPT("Au format PPT", "📽️", false),
    PDF_TO_IMAGES("PDF en images", "🏞️"),
    PDF_LONG("PDF en image longue", "📜"),

    // Modifier
    SIGN("Signature", "✍️"),
    WATERMARK("Ajouter un filigrane", "💧"),
    ERASER("Gomme intelligente", "🧽", false),
    CLEAN("Effacer les marques", "🧹", false),
    RESTORE("Restaurer une photo", "🛠️", false),
    MERGE("Fusionner des fichiers", "🧩"),
    EXTRACT("Extraire des pages du PDF", "✂️"),
    REORDER("Réorganiser les pages", "↕️"),
    LOCK("Verrouiller", "🔒"),
    COMPRESS("Compresser", "🗜️"),

    // Utilitaires
    SOLVER("Solver AI", "✨", false),
    COUNT("CountCam", "🔢", false),
    PRINT("Imprimer", "🖨️"),
    QR("Scannez le code", "🔳")
}

data class Section(val title: String, val tools: List<Tool>)

val SECTIONS = listOf(
    Section(
        "Scanner",
        listOf(
            Tool.SCAN, Tool.ID_CARD, Tool.OCR, Tool.ID_PHOTO, Tool.FORMULA,
            Tool.TRANSLATE, Tool.BOOK, Tool.SLIDES, Tool.WHITEBOARD, Tool.TIMESTAMP
        )
    ),
    Section("Importer", listOf(Tool.IMPORT_IMAGES, Tool.IMPORT_FILES)),
    Section(
        "Convertir",
        listOf(Tool.TO_WORD, Tool.TO_EXCEL, Tool.TO_PPT, Tool.PDF_TO_IMAGES, Tool.PDF_LONG)
    ),
    Section(
        "Modifier",
        listOf(
            Tool.SIGN, Tool.WATERMARK, Tool.ERASER, Tool.CLEAN, Tool.RESTORE,
            Tool.MERGE, Tool.EXTRACT, Tool.REORDER, Tool.LOCK, Tool.COMPRESS
        )
    ),
    Section("Utilitaires", listOf(Tool.SOLVER, Tool.COUNT, Tool.PRINT, Tool.QR))
)

sealed interface Screen {
    data object Home : Screen
    data class Viewer(val file: File) : Screen
    data class Pick(val tool: Tool) : Screen
    data class Work(val tool: Tool, val files: List<File>) : Screen
}
