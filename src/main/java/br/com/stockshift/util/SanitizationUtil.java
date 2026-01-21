package br.com.stockshift.util;

import org.owasp.encoder.Encode;
import org.springframework.stereotype.Component;

/**
 * Utilitário para sanitização de entrada de dados para prevenção de XSS.
 * Usa OWASP Encoder para garantir que dados não contenham código malicioso.
 */
@Component
public class SanitizationUtil {

    /**
     * Sanitiza texto para prevenir XSS quando renderizado em HTML.
     * Converte caracteres especiais para entidades HTML.
     *
     * @param input texto a ser sanitizado
     * @return texto sanitizado, seguro para renderização HTML
     */
    public static String sanitizeForHtml(String input) {
        if (input == null) {
            return null;
        }
        return Encode.forHtml(input);
    }

    /**
     * Sanitiza texto para uso em atributos HTML.
     *
     * @param input texto a ser sanitizado
     * @return texto sanitizado, seguro para atributos HTML
     */
    public static String sanitizeForHtmlAttribute(String input) {
        if (input == null) {
            return null;
        }
        return Encode.forHtmlAttribute(input);
    }

    /**
     * Sanitiza URL para prevenir injeção de JavaScript.
     *
     * @param input URL a ser sanitizada
     * @return URL sanitizada
     */
    public static String sanitizeUrl(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        // Bloqueia URLs com javascript: ou data:
        String lowerInput = input.toLowerCase().trim();
        if (lowerInput.startsWith("javascript:") || lowerInput.startsWith("data:")) {
            return null;
        }
        return Encode.forHtmlAttribute(input);
    }

    /**
     * Remove tags HTML completamente do texto.
     * Útil quando nenhum HTML é permitido.
     *
     * @param input texto com possíveis tags HTML
     * @return texto sem tags HTML
     */
    public static String stripHtmlTags(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("<[^>]*>", "").trim();
    }
}
