package uk.gov.di.ipv.cri.passport.checkpassport.services.dvad.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.http.HttpStatusCode;
import uk.gov.di.ipv.cri.common.library.util.EventProbe;
import uk.gov.di.ipv.cri.passport.checkpassport.domain.request.dvad.GraphQLRequest;
import uk.gov.di.ipv.cri.passport.checkpassport.domain.request.dvad.Input;
import uk.gov.di.ipv.cri.passport.checkpassport.domain.request.dvad.Variables;
import uk.gov.di.ipv.cri.passport.checkpassport.domain.response.dvad.AccessTokenResponse;
import uk.gov.di.ipv.cri.passport.checkpassport.services.dvad.DvadAPIHeaderValues;
import uk.gov.di.ipv.cri.passport.checkpassport.util.HTTPReply;
import uk.gov.di.ipv.cri.passport.checkpassport.util.HTTPReplyHelper;
import uk.gov.di.ipv.cri.passport.library.domain.PassportFormData;
import uk.gov.di.ipv.cri.passport.library.error.ErrorResponse;
import uk.gov.di.ipv.cri.passport.library.exceptions.OAuthErrorResponseException;

import java.io.IOException;
import java.net.URI;

import static uk.gov.di.ipv.cri.passport.checkpassport.domain.response.dvad.RequestHeaderKeys.HEADER_AUTHORIZATION;
import static uk.gov.di.ipv.cri.passport.checkpassport.domain.response.dvad.RequestHeaderKeys.HEADER_CONTENT_TYPE;
import static uk.gov.di.ipv.cri.passport.checkpassport.domain.response.dvad.RequestHeaderKeys.HEADER_REQ_ID;
import static uk.gov.di.ipv.cri.passport.checkpassport.domain.response.dvad.RequestHeaderKeys.HEADER_USER_AGENT;
import static uk.gov.di.ipv.cri.passport.checkpassport.domain.response.dvad.RequestHeaderKeys.HEADER_X_API_KEY;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_REQUEST_CREATED;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_REQUEST_SEND_ERROR;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_REQUEST_SEND_OK;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_RESPONSE_TYPE_EXPECTED_HTTP_STATUS;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_RESPONSE_TYPE_UNEXPECTED_HTTP_STATUS;

public class GraphQLRequestService {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String ENDPOINT_NAME = "graphql endpoint";

    private final URI requestURI;

    private final CloseableHttpClient closeableHttpClient;
    private final RequestConfig requestConfig;

    private final ObjectMapper objectMapper;

    private final EventProbe eventProbe;

    public GraphQLRequestService(
            String endpoint,
            CloseableHttpClient closeableHttpClient,
            RequestConfig requestConfig,
            ObjectMapper objectMapper,
            EventProbe eventProbe) {
        this.requestURI = URI.create(endpoint);
        this.closeableHttpClient = closeableHttpClient;
        this.requestConfig = requestConfig;
        this.objectMapper = objectMapper;
        this.eventProbe = eventProbe;
    }

    public String performGraphQLQuery(
            String requestId,
            AccessTokenResponse accessTokenResponse,
            DvadAPIHeaderValues dvadAPIHeaderValues,
            String queryString,
            PassportFormData passportFormData)
            throws OAuthErrorResponseException {

        final String accessTokenValue = accessTokenResponse.getAccessToken();
        final String tokenType = accessTokenResponse.getTokenType();

        // GraphQL Request is posted as if JSON
        final HttpPost request = new HttpPost();
        request.setURI(requestURI);
        request.addHeader(HEADER_CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        request.addHeader(HEADER_REQ_ID, requestId);
        request.addHeader(HEADER_X_API_KEY, dvadAPIHeaderValues.apiKey);
        request.addHeader(HEADER_USER_AGENT, dvadAPIHeaderValues.userAgent);
        request.addHeader(
                HEADER_AUTHORIZATION, String.format("%s %s", tokenType, accessTokenValue));

        // Enforce connection timeout values
        request.setConfig(requestConfig);

        // Body Params
        String requestBody;
        try {
            Variables variables = new Variables(new Input(passportFormData));

            // TODO Remove commented outcode, after confirming
            //  variables is not inside a string in updated API spec
            // String variablesAsString = objectMapper.writeValueAsString(variables);

            GraphQLRequest graphQLRequest =
                    GraphQLRequest.builder()
                            .query(queryString)
                            .variables(variables /* variablesAsString */)
                            .build();

            requestBody = objectMapper.writeValueAsString(graphQLRequest);
        } catch (JsonProcessingException e) {
            // PII in variables
            LOGGER.error("JsonProcessingException creating request body");
            LOGGER.debug(e.getMessage());
            throw new OAuthErrorResponseException(
                    HttpStatusCode.INTERNAL_SERVER_ERROR,
                    ErrorResponse.FAILED_TO_PREPARE_GRAPHQL_REQUEST_PAYLOAD);
        }

        LOGGER.debug("GraphQL request body : {}", requestBody);

        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        eventProbe.counterMetric(DVAD_GRAPHQL_REQUEST_CREATED.withEndpointPrefix());

        final HTTPReply httpReply;
        String requestURIString = requestURI.toString();
        LOGGER.debug("GraphQL request endpoint is {}", requestURIString);
        LOGGER.info("Submitting GraphQL request to third party...");
        try (CloseableHttpResponse response = closeableHttpClient.execute(request)) {

            eventProbe.counterMetric(DVAD_GRAPHQL_REQUEST_SEND_OK.withEndpointPrefix());

            // throws OAuthErrorResponseException on error
            httpReply =
                    HTTPReplyHelper.retrieveStatusCodeAndBodyFromResponse(response, ENDPOINT_NAME);
        } catch (IOException e) {
            LOGGER.error("IOException executing GraphQL request - {}", e.getMessage());

            eventProbe.counterMetric(
                    DVAD_GRAPHQL_REQUEST_SEND_ERROR.withEndpointPrefixAndExceptionName(e));

            throw new OAuthErrorResponseException(
                    HttpStatusCode.INTERNAL_SERVER_ERROR,
                    ErrorResponse.ERROR_INVOKING_THIRD_PARTY_API_GRAPHQL_ENDPOINT);
        }

        if (httpReply.statusCode == 200) {

            LOGGER.info("GraphQL status code {}", httpReply.statusCode);

            eventProbe.counterMetric(
                    DVAD_GRAPHQL_RESPONSE_TYPE_EXPECTED_HTTP_STATUS.withEndpointPrefix());

            return httpReply.responseBody;
        } else {
            // GraphQL endpoint responded but with an expected status code
            LOGGER.error(
                    "GraphQL request response status code was : {} - body {}",
                    httpReply.statusCode,
                    httpReply.responseBody);

            eventProbe.counterMetric(
                    DVAD_GRAPHQL_RESPONSE_TYPE_UNEXPECTED_HTTP_STATUS.withEndpointPrefix());

            throw new OAuthErrorResponseException(
                    HttpStatusCode.INTERNAL_SERVER_ERROR,
                    ErrorResponse.ERROR_GRAPHQL_ENDPOINT_RETURNED_UNEXPECTED_HTTP_STATUS_CODE);
        }
    }
}
