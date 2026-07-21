package com.orgmemory.core.knowledge;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class SourceCitationUri {

    private SourceCitationUri() {
    }

    static String canonicalize(String value) {
        String sourceUri = value == null || value.isBlank() ? null : value.trim();
        if (sourceUri == null) {
            return null;
        }
        if (sourceUri.length() > 2048) {
            throw new IllegalArgumentException("sourceUri must not exceed 2048 characters");
        }
        try {
            URI uri = new URI(sourceUri);
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("sourceUri must be absolute");
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http")) {
                throw new IllegalArgumentException("sourceUri must use http or https");
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalArgumentException("sourceUri must not contain credentials");
            }
            int fragmentIndex = sourceUri.indexOf('#');
            String withoutFragment = fragmentIndex < 0 ? sourceUri : sourceUri.substring(0, fragmentIndex);
            int queryIndex = withoutFragment.indexOf('?');
            return queryIndex < 0 ? withoutFragment : withoutFragment.substring(0, queryIndex);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("sourceUri is invalid", exception);
        }
    }

    static String safeForOutput(String value) {
        try {
            return canonicalize(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
