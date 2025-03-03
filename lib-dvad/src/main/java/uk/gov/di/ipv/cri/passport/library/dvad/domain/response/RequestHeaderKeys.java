package uk.gov.di.ipv.cri.passport.library.dvad.domain.response;

import uk.gov.di.ipv.cri.common.library.annotations.ExcludeFromGeneratedCoverageReport;

@ExcludeFromGeneratedCoverageReport
public class RequestHeaderKeys {
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_REQ_ID = "X-REQUEST-ID";
    public static final String HEADER_X_API_KEY = "X-API-Key"; // pragma: allowlist secret
    public static final String HEADER_USER_AGENT = "User-Agent";
    public static final String HEADER_DVAD_NETWORK_TYPE = "X-DVAD-NETWORK-TYPE";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    private RequestHeaderKeys() {
        // Intended
    }
}
