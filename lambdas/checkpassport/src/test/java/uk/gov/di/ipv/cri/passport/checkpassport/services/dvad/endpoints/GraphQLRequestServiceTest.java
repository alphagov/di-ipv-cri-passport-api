package uk.gov.di.ipv.cri.passport.checkpassport.services.dvad.endpoints;

import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.di.ipv.cri.common.library.util.EventProbe;
import uk.gov.di.ipv.cri.passport.checkpassport.domain.request.dvad.GraphQLRequest;
import uk.gov.di.ipv.cri.passport.checkpassport.domain.response.dvad.APIResponse;
import uk.gov.di.ipv.cri.passport.checkpassport.domain.response.dvad.AccessTokenResponse;
import uk.gov.di.ipv.cri.passport.checkpassport.services.dvad.DvadAPIHeaderValues;
import uk.gov.di.ipv.cri.passport.checkpassport.services.dvad.util.dvad.responses.DVADResponseFixtures;
import uk.gov.di.ipv.cri.passport.library.PassportFormTestDataGenerator;
import uk.gov.di.ipv.cri.passport.library.domain.PassportFormData;
import uk.gov.di.ipv.cri.passport.library.error.ErrorResponse;
import uk.gov.di.ipv.cri.passport.library.exceptions.OAuthErrorResponseException;
import uk.gov.di.ipv.cri.passport.library.service.PassportConfigurationService;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.di.ipv.cri.passport.library.config.ParameterStoreParameters.HMPO_API_HEADER_API_KEY;
import static uk.gov.di.ipv.cri.passport.library.config.ParameterStoreParameters.HMPO_API_HEADER_AUDIENCE;
import static uk.gov.di.ipv.cri.passport.library.config.ParameterStoreParameters.HMPO_API_HEADER_CLIENT_ID;
import static uk.gov.di.ipv.cri.passport.library.config.ParameterStoreParameters.HMPO_API_HEADER_GRANT_TYPE;
import static uk.gov.di.ipv.cri.passport.library.config.ParameterStoreParameters.HMPO_API_HEADER_SECRET;
import static uk.gov.di.ipv.cri.passport.library.config.ParameterStoreParameters.HMPO_API_HEADER_USER_AGENT;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_REQUEST_CREATED;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_REQUEST_SEND_ERROR;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_REQUEST_SEND_OK;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_RESPONSE_TYPE_EXPECTED_HTTP_STATUS;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_RESPONSE_TYPE_UNEXPECTED_HTTP_STATUS;

@ExtendWith(MockitoExtension.class)
class GraphQLRequestServiceTest {

    private static final String TEST_END_POINT = "http://127.0.0.1";
    @Mock private RequestConfig mockRequestConfig;
    @Mock private CloseableHttpClient mockCloseableHttpClient;
    @Mock private EventProbe mockEventProbe;

    // Used in most tests
    private ObjectMapper realObjectMapper;

    private GraphQLRequestService graphQLRequestService;

    @Mock PassportConfigurationService mockPassportConfigurationService;

    private DvadAPIHeaderValues realDvadAPIHeaderValues;

    @BeforeEach
    void setUp() {
        realObjectMapper = new ObjectMapper();

        graphQLRequestService =
                new GraphQLRequestService(
                        TEST_END_POINT,
                        mockCloseableHttpClient,
                        mockRequestConfig,
                        realObjectMapper,
                        mockEventProbe);

        // Mock Parameter store fetches in DvadAPIHeaderValues
        when(mockPassportConfigurationService.getEncryptedSsmParameter(HMPO_API_HEADER_API_KEY))
                .thenReturn("TEST_KEY");
        when(mockPassportConfigurationService.getParameterValue(HMPO_API_HEADER_USER_AGENT))
                .thenReturn("TEST_USER_AGENT");
        when(mockPassportConfigurationService.getParameterValue(HMPO_API_HEADER_CLIENT_ID))
                .thenReturn("TEST_CLIENT_ID");
        when(mockPassportConfigurationService.getParameterValue(HMPO_API_HEADER_SECRET))
                .thenReturn("TEST_SECRET");
        when(mockPassportConfigurationService.getParameterValue(HMPO_API_HEADER_GRANT_TYPE))
                .thenReturn("TEST_GRANT_TYPE");
        when(mockPassportConfigurationService.getParameterValue(HMPO_API_HEADER_AUDIENCE))
                .thenReturn("TEST_AUDIENCE");

        realDvadAPIHeaderValues = new DvadAPIHeaderValues(mockPassportConfigurationService);
    }

    @Test
    void shouldReturnApiResponseAsStringWhenPerformGraphQLQuerySucceeds()
            throws OAuthErrorResponseException, IOException {

        ArgumentCaptor<HttpEntityEnclosingRequestBase> httpRequestCaptor =
                ArgumentCaptor.forClass(HttpPost.class);

        CloseableHttpResponse graphQLAPIResponse =
                DVADResponseFixtures.mockGraphQLAPIResponse(200, true);

        // HttpClient response
        when(mockCloseableHttpClient.execute(httpRequestCaptor.capture()))
                .thenReturn(graphQLAPIResponse);

        // Method args
        String requestId = UUID.randomUUID().toString();
        AccessTokenResponse accessTokenResponse =
                AccessTokenResponse.builder().accessToken("TOKEN VALUE").expiresIn(1800).build();
        String queryString = "Select * from PassportDB where passport.id=";
        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        String apiResponseAsString =
                graphQLRequestService.performGraphQLQuery(
                        requestId,
                        accessTokenResponse,
                        realDvadAPIHeaderValues,
                        queryString,
                        passportFormData);

        // (POST) Graphql
        InOrder inOrderMockCloseableHttpClient = inOrder(mockCloseableHttpClient);
        inOrderMockCloseableHttpClient
                .verify(mockCloseableHttpClient, times(1))
                .execute(any(HttpPost.class));
        verifyNoMoreInteractions(mockCloseableHttpClient);

        InOrder inOrderMockEventProbe = inOrder(mockEventProbe);
        inOrderMockEventProbe
                .verify(mockEventProbe)
                .counterMetric(DVAD_GRAPHQL_REQUEST_CREATED.withEndpointPrefix());
        inOrderMockEventProbe
                .verify(mockEventProbe)
                .counterMetric(DVAD_GRAPHQL_REQUEST_SEND_OK.withEndpointPrefix());
        inOrderMockEventProbe
                .verify(mockEventProbe)
                .counterMetric(
                        DVAD_GRAPHQL_RESPONSE_TYPE_EXPECTED_HTTP_STATUS.withEndpointPrefix());
        // Validity is checked later
        verifyNoMoreInteractions(mockEventProbe);

        assertNotNull(apiResponseAsString);
        APIResponse apiResponse =
                realObjectMapper.readValue(apiResponseAsString, APIResponse.class);
        assertNotNull(apiResponse);
    }

    @Test
    void shouldReturnOAuthErrorResponseExceptionWhenFailingToCreateGraphQLRequestBody()
            throws IOException {

        ObjectMapper spyObjectMapper = Mockito.spy(new ObjectMapper());

        // Just for this test so we can inject use a spy to inject exceptions
        GraphQLRequestService thisTestOnlyGraphQLRequestService =
                new GraphQLRequestService(
                        TEST_END_POINT,
                        mockCloseableHttpClient,
                        mockRequestConfig,
                        spyObjectMapper,
                        mockEventProbe);

        OAuthErrorResponseException expectedReturnedException =
                new OAuthErrorResponseException(
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ErrorResponse.FAILED_TO_PREPARE_GRAPHQL_REQUEST_PAYLOAD);

        // Method args
        String requestId = UUID.randomUUID().toString();
        AccessTokenResponse accessTokenResponse =
                AccessTokenResponse.builder().accessToken("TOKEN VALUE").expiresIn(1800).build();
        String queryString = "Select * from PassportDB where passport.id=";
        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        // The above form data is validated and map by field into another object,
        // preventing the JsonProcessingException from occurring.
        // This triggers the exception directly to ensure it is handled should the processing change
        when(spyObjectMapper.writeValueAsString(any(GraphQLRequest.class)))
                .thenThrow(
                        new InputCoercionException(
                                null, "Problem during json mapping", null, null));

        OAuthErrorResponseException thrownException =
                assertThrows(
                        OAuthErrorResponseException.class,
                        () ->
                                thisTestOnlyGraphQLRequestService.performGraphQLQuery(
                                        requestId,
                                        accessTokenResponse,
                                        realDvadAPIHeaderValues,
                                        queryString,
                                        passportFormData),
                        "Expected OAuthErrorResponseException");

        assertEquals(expectedReturnedException.getStatusCode(), thrownException.getStatusCode());
        assertEquals(expectedReturnedException.getErrorReason(), thrownException.getErrorReason());
    }

    @Test
    void shouldReturnOAuthErrorResponseExceptionWhenGraphQlEndpointDoesNotRespond()
            throws IOException {
        Exception exceptionCaught = new IOException("GraphQl Endpoint Timed out");

        doThrow(exceptionCaught).when(mockCloseableHttpClient).execute(any(HttpPost.class));

        OAuthErrorResponseException expectedReturnedException =
                new OAuthErrorResponseException(
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ErrorResponse.ERROR_INVOKING_THIRD_PARTY_API_GRAPHQL_ENDPOINT);

        // Method args
        String requestId = UUID.randomUUID().toString();
        AccessTokenResponse accessTokenResponse =
                AccessTokenResponse.builder().accessToken("TOKEN VALUE").expiresIn(1800).build();
        String queryString = "Select * from PassportDB where passport.id=";
        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        OAuthErrorResponseException thrownException =
                assertThrows(
                        OAuthErrorResponseException.class,
                        () ->
                                graphQLRequestService.performGraphQLQuery(
                                        requestId,
                                        accessTokenResponse,
                                        realDvadAPIHeaderValues,
                                        queryString,
                                        passportFormData),
                        "Expected OAuthErrorResponseException");

        // (Post) GraphQl
        InOrder inOrderMockHttpClientSequence = inOrder(mockCloseableHttpClient);
        inOrderMockHttpClientSequence
                .verify(mockCloseableHttpClient, times(1))
                .execute(any(HttpPost.class));
        verifyNoMoreInteractions(mockCloseableHttpClient);

        InOrder inOrderMockEventProbeSequence = inOrder(mockEventProbe);
        inOrderMockEventProbeSequence
                .verify(mockEventProbe)
                .counterMetric(DVAD_GRAPHQL_REQUEST_CREATED.withEndpointPrefix());
        inOrderMockEventProbeSequence
                .verify(mockEventProbe)
                .counterMetric(
                        DVAD_GRAPHQL_REQUEST_SEND_ERROR.withEndpointPrefixAndExceptionName(
                                exceptionCaught));
        verifyNoMoreInteractions(mockEventProbe);

        assertEquals(expectedReturnedException.getStatusCode(), thrownException.getStatusCode());
        assertEquals(expectedReturnedException.getErrorReason(), thrownException.getErrorReason());
    }

    @Test
    void shouldReturnOAuthErrorResponseExceptionWhenGraphQLEndpointResponseStatusCodeNot200()
            throws IOException {
        ArgumentCaptor<HttpEntityEnclosingRequestBase> httpRequestCaptor =
                ArgumentCaptor.forClass(HttpPost.class);

        // A GraphQLAPIResponse but status not 200
        CloseableHttpResponse graphQLAPIResponse =
                DVADResponseFixtures.mockGraphQLAPIResponse(500, true);

        // HttpClient response
        when(mockCloseableHttpClient.execute(httpRequestCaptor.capture()))
                .thenReturn(graphQLAPIResponse);

        OAuthErrorResponseException expectedReturnedException =
                new OAuthErrorResponseException(
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ErrorResponse.ERROR_GRAPHQL_ENDPOINT_RETURNED_UNEXPECTED_HTTP_STATUS_CODE);

        // Method args
        String requestId = UUID.randomUUID().toString();
        AccessTokenResponse accessTokenResponse =
                AccessTokenResponse.builder().accessToken("TOKEN VALUE").expiresIn(1800).build();
        String queryString = "Select * from PassportDB where passport.id=";
        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        OAuthErrorResponseException thrownException =
                assertThrows(
                        OAuthErrorResponseException.class,
                        () ->
                                graphQLRequestService.performGraphQLQuery(
                                        requestId,
                                        accessTokenResponse,
                                        realDvadAPIHeaderValues,
                                        queryString,
                                        passportFormData),
                        "Expected OAuthErrorResponseException");

        // (Post) GraphQl
        InOrder inOrderMockHttpClientSequence = inOrder(mockCloseableHttpClient);
        inOrderMockHttpClientSequence
                .verify(mockCloseableHttpClient, times(1))
                .execute(any(HttpPost.class));
        verifyNoMoreInteractions(mockCloseableHttpClient);

        InOrder inOrderMockEventProbeSequence = inOrder(mockEventProbe);
        inOrderMockEventProbeSequence
                .verify(mockEventProbe)
                .counterMetric(DVAD_GRAPHQL_REQUEST_CREATED.withEndpointPrefix());
        inOrderMockEventProbeSequence
                .verify(mockEventProbe)
                .counterMetric(DVAD_GRAPHQL_REQUEST_SEND_OK.withEndpointPrefix());
        inOrderMockEventProbeSequence
                .verify(mockEventProbe)
                .counterMetric(
                        DVAD_GRAPHQL_RESPONSE_TYPE_UNEXPECTED_HTTP_STATUS.withEndpointPrefix());
        verifyNoMoreInteractions(mockEventProbe);

        assertEquals(expectedReturnedException.getStatusCode(), thrownException.getStatusCode());
        assertEquals(expectedReturnedException.getErrorReason(), thrownException.getErrorReason());
    }
}
