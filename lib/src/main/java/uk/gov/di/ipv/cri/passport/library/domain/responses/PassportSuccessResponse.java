package uk.gov.di.ipv.cri.passport.library.domain.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.di.ipv.cri.passport.library.annotations.ExcludeFromGeneratedCoverageReport;

@ExcludeFromGeneratedCoverageReport
public class PassportSuccessResponse {
    @JsonProperty("session_id")
    private final String passportSessionId;

    @JsonProperty("state")
    private final String state;

    @JsonProperty("redirect_uri")
    private final String redirectURI;

    public PassportSuccessResponse(
            @JsonProperty(value = "session_id", required = true) String passportSessionId,
            @JsonProperty(value = "state", required = true) String state,
            @JsonProperty(value = "redirect_uri", required = true) String redirectURI) {
        this.passportSessionId = passportSessionId;
        this.state = state;
        this.redirectURI = redirectURI;
    }

    public String getPassportSessionId() {
        return passportSessionId;
    }

    public String getState() {
        return state;
    }

    public String getRedirectURI() {
        return redirectURI;
    }
}
