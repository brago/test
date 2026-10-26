package com.rapmonet.securite.internal;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Map;

/** QR code au format SVG (affiché tel quel par le navigateur, sans image transmise à un tiers). */
final class QrCodeSvg {

    private QrCodeSvg() {
    }

    static String svg(String texte) {
        try {
            BitMatrix m = new QRCodeWriter().encode(texte, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, 2, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            StringBuilder chemin = new StringBuilder();
            for (int y = 0; y < m.getHeight(); y++) {
                for (int x = 0; x < m.getWidth(); x++) {
                    if (m.get(x, y)) {
                        chemin.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                    }
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" shape-rendering=\"crispEdges\">"
                    .formatted(m.getWidth(), m.getHeight())
                    + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\"" + chemin + "\"/></svg>";
        } catch (WriterException e) {
            throw new IllegalStateException(e);
        }
    }
}
