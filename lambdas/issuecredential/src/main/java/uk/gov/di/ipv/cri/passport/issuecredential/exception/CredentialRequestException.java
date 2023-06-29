package uk.gov.di.ipv.cri.passport.issuecredential.exception;

import uk.gov.di.ipv.cri.common.library.error.ErrorResponse;

public class CredentialRequestException extends Exception {
    public CredentialRequestException(ErrorResponse invalidRequestParam) {
        super(invalidRequestParam.getMessage());
    }
}
