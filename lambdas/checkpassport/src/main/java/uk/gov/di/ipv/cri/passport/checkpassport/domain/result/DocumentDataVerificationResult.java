package uk.gov.di.ipv.cri.passport.checkpassport.domain.result;

import uk.gov.di.ipv.cri.passport.checkpassport.domain.result.fields.APIResultSource;

import java.util.ArrayList;
import java.util.List;

public class DocumentDataVerificationResult {

    private APIResultSource apiResultSource;

    private boolean verified;
    private List<String> contraIndicators;
    private int strengthScore;
    private int validityScore;

    private String transactionId;

    private List<String> checksSucceeded = new ArrayList<>();
    private List<String> checksFailed = new ArrayList<>();

    public APIResultSource getAPIResultSource() {
        return apiResultSource;
    }

    public void setAPIResultSource(APIResultSource apiResultSource) {
        this.apiResultSource = apiResultSource;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public List<String> getContraIndicators() {
        return contraIndicators;
    }

    public void setContraIndicators(List<String> contraIndicators) {
        this.contraIndicators = contraIndicators;
    }

    public int getStrengthScore() {
        return strengthScore;
    }

    public void setStrengthScore(int strengthScore) {
        this.strengthScore = strengthScore;
    }

    public int getValidityScore() {
        return validityScore;
    }

    public void setValidityScore(int validityScore) {
        this.validityScore = validityScore;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public List<String> getChecksSucceeded() {
        return checksSucceeded;
    }

    public void setChecksSucceeded(List<String> checksSucceeded) {
        this.checksSucceeded = checksSucceeded;
    }

    public List<String> getChecksFailed() {
        return checksFailed;
    }

    public void setChecksFailed(List<String> checksFailed) {
        this.checksFailed = checksFailed;
    }
}
