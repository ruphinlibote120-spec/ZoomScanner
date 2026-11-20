package com.zoomscanner.app

import java.io.File

/** Les 14 outils de l'écran « Outils ». color : couleur de la pastille, icon : nom du dessin (voir Icons.kt). */
enum class Tool(val label: String, val icon: String, val color: Long) {
    SCAN("Scanner", "Scanner", 0xFF2563EB),
    ID_PHOTO("Photo d'identité", "Photo d'identité", 0xFF6366F1),
    QR("QR code", "Scan QR code", 0xFF0EA5E9),
    IMPORT("Importer", "importer", 0xFF0D9488),
    OCR("Extraire le texte", "Extraire le texte", 0xFFDB2777),
    PDF_TO_IMAGES("PDF en images", "PDF en images", 0xFFEA580C),
    IMAGES("Images", "Convertir des photos", 0xFFC2410C),
    COMPRESS("Compresser", "Compresser", 0xFF5B21B6),
    ANNOTATE("Annoter", "Annoter", 0xFF7C3AED),
    STAMPS("Tampons", "stamp", 0xFF9333EA),
    PAGES("Organiser les pages", "pages", 0xFFBE123C),
    MERGE("Fusionner", "Fusionner", 0xFFDC2626),
    PASSWORD("Mot de passe", "Verrouiller", 0xFF475569),
    PRINT("Imprimer", "Imprimer", 0xFF334155)
}

sealed interface Screen {
    data object Home : Screen
    data class Viewer(val file: File) : Screen
    data class Pick(val tool: Tool) : Screen
    data class Work(val tool: Tool, val files: List<File>) : Screen
    data object ScanModes : Screen
    data object QrCreate : Screen
    data object ImagesTool : Screen
    data object Settings : Screen
    data object Favorites : Screen
    data object Folders : Screen
    data class Folder(val name: String) : Screen
    data object Trash : Screen
}
