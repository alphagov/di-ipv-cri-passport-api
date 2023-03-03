package uk.gov.di.ipv.cri.passport.initialisesession;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.lambda.powertools.logging.CorrelationIdPathConstants;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.metrics.Metrics;
import uk.gov.di.ipv.cri.common.library.util.EventProbe;
import uk.gov.di.ipv.cri.passport.library.annotations.ExcludeFromGeneratedCoverageReport;
import uk.gov.di.ipv.cri.passport.library.auditing.AuditEventTypes;
import uk.gov.di.ipv.cri.passport.library.auditing.AuditEventUser;
import uk.gov.di.ipv.cri.passport.library.config.PassportConfigurationService;
import uk.gov.di.ipv.cri.passport.library.domain.responses.PassportSuccessResponse;
import uk.gov.di.ipv.cri.passport.library.error.ErrorResponse;
import uk.gov.di.ipv.cri.passport.library.error.RedirectErrorResponse;
import uk.gov.di.ipv.cri.passport.library.exceptions.JarValidationException;
import uk.gov.di.ipv.cri.passport.library.exceptions.RecoverableJarValidationException;
import uk.gov.di.ipv.cri.passport.library.exceptions.SqsException;
import uk.gov.di.ipv.cri.passport.library.helpers.ApiGatewayResponseGenerator;
import uk.gov.di.ipv.cri.passport.library.helpers.LogHelper;
import uk.gov.di.ipv.cri.passport.library.persistence.item.PassportSessionItem;
import uk.gov.di.ipv.cri.passport.library.service.AuditService;
import uk.gov.di.ipv.cri.passport.library.service.KmsRsaDecrypter;
import uk.gov.di.ipv.cri.passport.library.service.PassportSessionService;
import uk.gov.di.ipv.cri.passport.library.validation.JarValidator;

import java.text.ParseException;

import static uk.gov.di.ipv.cri.passport.library.config.ConfigurationVariable.JAR_ENCRYPTION_KEY_ID;
import static uk.gov.di.ipv.cri.passport.library.metrics.Definitions.LAMBDA_INITIALISE_SESSION_COMPLETED_ERROR;
import static uk.gov.di.ipv.cri.passport.library.metrics.Definitions.LAMBDA_INITIALISE_SESSION_COMPLETED_OK;

public class InitialiseSessionHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final PassportConfigurationService passportConfigurationService;
    private final KmsRsaDecrypter kmsRsaDecrypter;
    private final JarValidator jarValidator;
    private final AuditService auditService;
    private final PassportSessionService passportSessionService;

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Integer OK = 200;
    private static final Integer BAD_REQUEST = 400;
    private static final String CLIENT_ID = "client_id";
    private static final String SHARED_CLAIMS = "shared_claims";

    private final EventProbe eventProbe;

    public InitialiseSessionHandler(
            PassportConfigurationService passportConfigurationService,
            KmsRsaDecrypter kmsRsaDecrypter,
            JarValidator jarValidator,
            AuditService auditService,
            PassportSessionService passportSessionService,
            EventProbe eventProbe) {
        this.passportConfigurationService = passportConfigurationService;
        this.kmsRsaDecrypter = kmsRsaDecrypter;
        this.jarValidator = jarValidator;
        this.auditService = auditService;
        this.passportSessionService = passportSessionService;
        this.eventProbe = eventProbe;
    }

    @ExcludeFromGeneratedCoverageReport
    public InitialiseSessionHandler() {
        this.passportConfigurationService = new PassportConfigurationService();
        this.kmsRsaDecrypter =
                new KmsRsaDecrypter(
                        passportConfigurationService.getSsmParameter(JAR_ENCRYPTION_KEY_ID));
        this.jarValidator = new JarValidator(kmsRsaDecrypter, passportConfigurationService);
        this.auditService =
                new AuditService(AuditService.getDefaultSqsClient(), passportConfigurationService);
        this.passportSessionService = new PassportSessionService(passportConfigurationService);
        eventProbe = new EventProbe();
    }

    @Override
    @Logging(clearState = true, correlationIdPath = CorrelationIdPathConstants.API_GATEWAY_REST)
    @Metrics(captureColdStart = true)
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent input, Context context) {
        LogHelper.attachComponentIdToLogs();
        try {
            ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

            RawSessionRequest rawSessionRequest =
                    objectMapper.readValue(input.getBody(), RawSessionRequest.class);

            // From the body (common-express)
            String clientId = rawSessionRequest.getClientId();
            String requestJWT = rawSessionRequest.getRequestJWT();

            if (StringUtils.isBlank(clientId)) {
                eventProbe.counterMetric(LAMBDA_INITIALISE_SESSION_COMPLETED_ERROR);
                return ApiGatewayResponseGenerator.proxyJsonResponse(
                        BAD_REQUEST, ErrorResponse.MISSING_CLIENT_ID_QUERY_PARAMETER);
            }
            LogHelper.attachClientIdToLogs(clientId);

            if (requestJWT == null) {
                eventProbe.counterMetric(LAMBDA_INITIALISE_SESSION_COMPLETED_ERROR);
                return ApiGatewayResponseGenerator.proxyJsonResponse(
                        BAD_REQUEST, ErrorResponse.MISSING_SHARED_ATTRIBUTES_JWT);
            }

            SignedJWT signedJWT = jarValidator.decryptJWE(JWEObject.parse(requestJWT));

            JWTClaimsSet claimsSet = jarValidator.validateRequestJwt(signedJWT, clientId);

            PassportSessionItem passportSessionItem =
                    passportSessionService.generatePassportSession(claimsSet);

            this.auditService.sendAuditEvent(
                    AuditEventTypes.IPV_PASSPORT_CRI_START,
                    AuditEventUser.fromPassportSessionItem(passportSessionItem));

            PassportSuccessResponse response = generateSessionSuccessResponse(passportSessionItem);

            eventProbe.counterMetric(LAMBDA_INITIALISE_SESSION_COMPLETED_OK);

            return ApiGatewayResponseGenerator.proxyJsonResponse(OK, response);
        } catch (RecoverableJarValidationException e) {
            LOGGER.error("JAR validation failed: {}", e.getErrorObject().getDescription());
            RedirectErrorResponse errorResponse =
                    new RedirectErrorResponse(
                            e.getRedirectUri(), e.getState(), e.getErrorObject().toJSONObject());
            eventProbe.counterMetric(LAMBDA_INITIALISE_SESSION_COMPLETED_ERROR);
            return ApiGatewayResponseGenerator.proxyJsonResponse(
                    e.getErrorObject().getHTTPStatusCode(), errorResponse);
        } catch (JarValidationException e) {
            LOGGER.error("JAR validation failed: {}", e.getErrorObject().getDescription());
            eventProbe.counterMetric(LAMBDA_INITIALISE_SESSION_COMPLETED_ERROR);
            return ApiGatewayResponseGenerator.proxyJsonResponse(
                    e.getErrorObject().getHTTPStatusCode(), e.getErrorObject().toJSONObject());
        } catch (ParseException e) {
            LOGGER.error("Failed to parse claim set when attempting to retrieve JAR claims");
            eventProbe.counterMetric(LAMBDA_INITIALISE_SESSION_COMPLETED_ERROR);
            return ApiGatewayResponseGenerator.proxyJsonResponse(
                    BAD_REQUEST, ErrorResponse.FAILED_TO_PARSE);
        } catch (SqsException e) {
            LOGGER.error("Failed to send audit event to SQS queue because: {}", e.getMessage());
            eventProbe.counterMetric(LAMBDA_INITIALISE_SESSION_COMPLETED_ERROR);
            return ApiGatewayResponseGenerator.proxyJsonResponse(
                    HttpStatus.SC_BAD_REQUEST,
                    ErrorResponse.FAILED_TO_SEND_AUDIT_MESSAGE_TO_SQS_QUEUE);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error mapping client_id and request from request body", e.getMessage());
            eventProbe.counterMetric(LAMBDA_INITIALISE_SESSION_COMPLETED_ERROR);
            return ApiGatewayResponseGenerator.proxyJsonResponse(BAD_REQUEST, e.getMessage());
        }
    }

    private PassportSuccessResponse generateSessionSuccessResponse(
            PassportSessionItem passportSessionItem) {

        String sessionId = passportSessionItem.getPassportSessionId();
        String state = passportSessionItem.getAuthParams().getState();
        String redirectURI = passportSessionItem.getAuthParams().getRedirectUri();

        return new PassportSuccessResponse(sessionId, state, redirectURI);
    }
}
