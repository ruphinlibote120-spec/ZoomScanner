package com.zoomscanner.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Icônes dessinées au trait (grille 24 x 24), une liste de tracés par icône. */
object Ic {
    val paths: Map<String, List<String>> = mapOf(
        "Scanner" to listOf("M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M7 12h10"),
        "Scan en lot" to listOf("M20 7h-3a2 2 0 0 1-2-2V2", "M9 18a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h7l4 4v10a2 2 0 0 1-2 2z", "M3 7.6v12.8A1.6 1.6 0 0 0 4.6 22h9.8"),
        "Cartes d'identité" to listOf("M4 5H20A2 2 0 0 1 22 7V17A2 2 0 0 1 20 19H4A2 2 0 0 1 2 17V7A2 2 0 0 1 4 5z", "M7 11a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", "M16 10h2", "M16 14h2", "M5.5 16c.8-1.700 2-2.500 3.500-2.500s2.700.800 3.500 2.500"),
        "Photo d'identité" to listOf("M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3z", "M9 10a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z", "M7 21v-2a4 4 0 0 1 4-4h2a4 4 0 0 1 4 4v2"),
        "Horodatage" to listOf("M4 14a8 8 0 1 0 16 0a8 8 0 1 0 -16 0z", "M12 14l3-3", "M10 2h4"),
        "Livre" to listOf("M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z", "M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"),
        "Diapositives" to listOf("M2 3h20", "M21 3v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V3", "M7 21l5-5 5 5"),
        "Tableau blanc" to listOf("M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7", "M18.400 2.600a2.100 2.100 0 0 1 3 3L12 15l-4 1 1-4z"),
        "Scan QR code" to listOf("M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M17 12v4a1 1 0 0 1-1 1h-4", "M17 8V7", "M7 17h.01", "M8 7H11A1 1 0 0 1 12 8V11A1 1 0 0 1 11 12H8A1 1 0 0 1 7 11V8A1 1 0 0 1 8 7z"),
        "Créer un QR code" to listOf("M4 3H9A1 1 0 0 1 10 4V9A1 1 0 0 1 9 10H4A1 1 0 0 1 3 9V4A1 1 0 0 1 4 3z", "M15 3H20A1 1 0 0 1 21 4V9A1 1 0 0 1 20 10H15A1 1 0 0 1 14 9V4A1 1 0 0 1 15 3z", "M4 14H9A1 1 0 0 1 10 15V20A1 1 0 0 1 9 21H4A1 1 0 0 1 3 20V15A1 1 0 0 1 4 14z", "M14 14h3v3", "M21 14v.01", "M14 21h.01", "M17.500 21H21v-4"),
        "Importer des images" to listOf("M16 5h6", "M19 2v6", "M21 11.500V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.500", "M21 15l-3.086-3.086a2 2 0 0 0-2.828 0L6 21", "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z"),
        "Importer des fichiers" to listOf("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.900a2 2 0 0 1-1.690-.900L9.600 3.900A2 2 0 0 0 7.930 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z", "M12 10v6", "M9 13l3 3 3-3"),
        "PDF en images" to listOf("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v4a2 2 0 0 0 2 2h4", "M20 17l-1.100-1.100a2 2 0 0 0-2.800 0L10 22", "M8 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z"),
        "PDF en image longue" to listOf("M7 2h10", "M5 6h14", "M5 10H19A2 2 0 0 1 21 12V20A2 2 0 0 1 19 22H5A2 2 0 0 1 3 20V12A2 2 0 0 1 5 10z"),
        "Convertir des photos" to listOf("M18 22H4a2 2 0 0 1-2-2V6", "M22 13l-1.296-1.296a2.410 2.410 0 0 0-3.408 0L11 18", "M10 8a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", "M8 2H20A2 2 0 0 1 22 4V16A2 2 0 0 1 20 18H8A2 2 0 0 1 6 16V4A2 2 0 0 1 8 2z"),
        "Redimensionner l'image" to listOf("M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7", "M14 15H9v-5", "M16 3h5v5", "M14 10l7-7"),
        "Texte en PDF" to listOf("M4 22h14a2 2 0 0 0 2-2V7l-5-5H6a2 2 0 0 0-2 2v4", "M14 2v4a2 2 0 0 0 2 2h4", "M2 13v-1h6v1", "M5 12v6", "M4 18h2"),
        "Extraire le texte" to listOf("M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M7 8h8", "M7 12h10", "M7 16h6"),
        "Traduire" to listOf("M5 8l6 6", "M4 14l6-6 2-3", "M2 5h12", "M7 2h1", "M22 22l-5-10-5 10", "M14 18h6"),
        "Lire à voix haute" to listOf("M11 4.700a.705.705 0 0 0-1.203-.498L6.413 7.587A1.400 1.400 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.400 1.400 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z", "M16 9a5 5 0 0 1 0 6", "M19.364 18.364a9 9 0 0 0 0-12.728"),
        "Exporter en .txt" to listOf("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v4a2 2 0 0 0 2 2h4", "M12 18v-6", "M9 15l3 3 3-3"),
        "Au Word" to listOf("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v4a2 2 0 0 0 2 2h4", "M10 9H8", "M16 13H8", "M16 17H8"),
        "Annoter" to listOf("M9 11l-6 6v3h9l3-3", "M22 12l-4.600 4.600a2 2 0 0 1-2.800 0l-5.200-5.200a2 2 0 0 1 0-2.800L14 4"),
        "Signature" to listOf("M12 20h9", "M16.376 3.622a1 1 0 0 1 3.002 3.002L7.368 18.635a2 2 0 0 1-.855.506l-2.872.838a.5.5 0 0 1-.62-.62l.838-2.872a2 2 0 0 1 .506-.854z"),
        "Filigrane" to listOf("M12 22a7 7 0 0 0 7-7c0-2-1-3.900-3-5.500s-3.500-4-4-6.500c-.5 2.500-2 4.900-4 6.500C6 11.100 5 13 5 15a7 7 0 0 0 7 7z"),
        "Caviarder" to listOf("M9.880 9.880a3 3 0 1 0 4.240 4.240", "M10.730 5.080A10.430 10.430 0 0 1 12 5c7 0 10 7 10 7a13.160 13.160 0 0 1-1.670 2.680", "M6.610 6.610A13.530 13.530 0 0 0 2 12s3 7 10 7a9.740 9.740 0 0 0 5.390-1.610", "M2 2l20 20"),
        "Numéroter les pages" to listOf("M10 12h11", "M10 18h11", "M10 6h11", "M4 10h2", "M4 6h1v4", "M6 18H4c0-1 2-2 2-3s-1-1.500-2-1"),
        "Fusionner" to listOf("M8 6l4-4 4 4", "M12 2v10.300a4 4 0 0 1-1.172 2.872L4 22", "M20 22l-5-5"),
        "Diviser un PDF" to listOf("M16 3h5v5", "M8 3H3v5", "M12 22v-8.300a4 4 0 0 0-1.172-2.872L3 3", "M15 9l6-6"),
        "Extraire des pages" to listOf("M4.226 20.925A2 2 0 0 0 6 22h12a2 2 0 0 0 2-2V8a2.400 2.400 0 0 0-.706-1.706l-3.588-3.588A2.400 2.400 0 0 0 14 2H6a2 2 0 0 0-2 2v3.127", "M14 2v5a1 1 0 0 0 1 1h5", "M5 12l-3 3 3 3", "M2 15h10"),
        "Supprimer des pages" to listOf("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v4a2 2 0 0 0 2 2h4", "M14.500 12.500l-5 5", "M9.500 12.500l5 5"),
        "Réorganiser" to listOf("M7 3v18", "M3 7l4-4 4 4", "M17 21V3", "M13 17l4 4 4-4"),
        "Verrouiller" to listOf("M5 11H19A2 2 0 0 1 21 13V20A2 2 0 0 1 19 22H5A2 2 0 0 1 3 20V13A2 2 0 0 1 5 11z", "M7 11V7a5 5 0 0 1 10 0v4"),
        "Déverrouiller" to listOf("M5 11H19A2 2 0 0 1 21 13V20A2 2 0 0 1 19 22H5A2 2 0 0 1 3 20V13A2 2 0 0 1 5 11z", "M7 11V7a5 5 0 0 1 9.900-1"),
        "Compresser" to listOf("M4 14h6v6", "M20 10h-6V4", "M14 10l7-7", "M3 21l7-7"),
        "Imprimer" to listOf("M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2", "M6 9V3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v6", "M7 14H17A1 1 0 0 1 18 15V21A1 1 0 0 1 17 22H7A1 1 0 0 1 6 21V15A1 1 0 0 1 7 14z"),
        "search" to listOf("M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0z", "M21 21l-4.3-4.3"),
        "sliders" to listOf("M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6"),
        "cam" to listOf("M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z", "M9 13a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z"),
        "file" to listOf("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6", "M8 13h8", "M8 17h5"),
        "grid" to listOf("M4 3H9A1 1 0 0 1 10 4V9A1 1 0 0 1 9 10H4A1 1 0 0 1 3 9V4A1 1 0 0 1 4 3z", "M15 3H20A1 1 0 0 1 21 4V9A1 1 0 0 1 20 10H15A1 1 0 0 1 14 9V4A1 1 0 0 1 15 3z", "M15 14H20A1 1 0 0 1 21 15V20A1 1 0 0 1 20 21H15A1 1 0 0 1 14 20V15A1 1 0 0 1 15 14z", "M4 14H9A1 1 0 0 1 10 15V20A1 1 0 0 1 9 21H4A1 1 0 0 1 3 20V15A1 1 0 0 1 4 14z"),
        "left" to listOf("M19 12H5", "M12 19l-7-7 7-7"),
        "x" to listOf("M18 6L6 18", "M6 6l12 12"),
        "bolt" to listOf("M13 2L3 14h8l-1 8 10-12h-8z"),
        "check" to listOf("M20 6L9 17l-5-5"),
        "image" to listOf("M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3z", "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z", "M21 15l-3.1-3.1a2 2 0 0 0-2.8 0L6 21"),
        "copy" to listOf("M10 8H20A2 2 0 0 1 22 10V20A2 2 0 0 1 20 22H10A2 2 0 0 1 8 20V10A2 2 0 0 1 10 8z", "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"),
        "stamp" to listOf("M5 22h14", "M19.27 13.73A2.5 2.5 0 0 0 17.5 13h-11A2.5 2.5 0 0 0 4 15.5V17a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-1.5c0-.66-.26-1.3-.73-1.77", "M14 13V8.5C14 7 15 7 15 5a3 3 0 0 0-6 0c0 2 1 2 1 3.5V13"),
        "pages" to listOf("M4.5 3H9.5A1.5 1.5 0 0 1 11 4.5V11.5A1.5 1.5 0 0 1 9.5 13H4.5A1.5 1.5 0 0 1 3 11.5V4.5A1.5 1.5 0 0 1 4.5 3z", "M14.5 3H19.5A1.5 1.5 0 0 1 21 4.5V7.5A1.5 1.5 0 0 1 19.5 9H14.5A1.5 1.5 0 0 1 13 7.5V4.5A1.5 1.5 0 0 1 14.5 3z", "M4.5 15H9.5A1.5 1.5 0 0 1 11 16.5V19.5A1.5 1.5 0 0 1 9.5 21H4.5A1.5 1.5 0 0 1 3 19.5V16.5A1.5 1.5 0 0 1 4.5 15z", "M14.5 11H19.5A1.5 1.5 0 0 1 21 12.5V19.5A1.5 1.5 0 0 1 19.5 21H14.5A1.5 1.5 0 0 1 13 19.5V12.5A1.5 1.5 0 0 1 14.5 11z"),
        "star" to listOf("M12 2l3.1 6.3 6.9 1-5 4.9 1.2 6.8L12 17.8 5.8 21l1.2-6.8-5-4.9 6.9-1z"),
        "folder" to listOf("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"),
        "trash" to listOf("M3 6h18", "M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"),
        "plus" to listOf("M12 5v14", "M5 12h14"),
        "right" to listOf("M9 6l6 6-6 6"),
        "dots" to listOf("M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z", "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z", "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z"),
        "share" to listOf("M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8", "M16 6l-4-4-4 4", "M12 2v13"),
        "download" to listOf("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3"),
        "edit" to listOf("M12 20h9", "M16.4 3.6a2.1 2.1 0 0 1 3 3L7.4 18.6l-4 1 1-4z"),
        "refresh" to listOf("M3 12a9 9 0 0 1 15-6.7L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-15 6.7L3 16", "M3 21v-5h5"),
        "info" to listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z", "M12 16v-4", "M12 8h.01"),
        "importer" to listOf("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.900a2 2 0 0 1-1.690-.900L9.600 3.900A2 2 0 0 0 7.930 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z", "M12 10v6", "M9 13l3 3 3-3"),
    )
}

@Composable
fun Glyph(name: String, color: Color, size: Dp = 24.dp, stroke: Float = 1.8f, filled: Boolean = false) {
    val shapes = remember(name) {
        (Ic.paths[name] ?: emptyList()).map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            for (p in shapes) {
                if (filled) {
                    drawPath(p, color, style = Fill)
                }
                drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
