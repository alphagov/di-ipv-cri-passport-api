package uk.gov.di.ipv.cri.passport.checkpassport.services.dcs;

import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.HttpStatusCode;
import uk.gov.di.ipv.cri.common.library.util.EventProbe;
import uk.gov.di.ipv.cri.passport.checkpassport.domain.response.dcs.DcsResponse;
import uk.gov.di.ipv.cri.passport.checkpassport.exception.dcs.IpvCryptoException;
import uk.gov.di.ipv.cri.passport.library.PassportFormTestDataGenerator;
import uk.gov.di.ipv.cri.passport.library.domain.PassportFormData;
import uk.gov.di.ipv.cri.passport.library.domain.result.ThirdPartyAPIResult;
import uk.gov.di.ipv.cri.passport.library.error.ErrorResponse;
import uk.gov.di.ipv.cri.passport.library.exceptions.OAuthErrorResponseException;
import uk.gov.di.ipv.cri.passport.library.service.ParameterStoreService;
import uk.gov.di.ipv.cri.passport.library.service.ThirdPartyAPIService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.di.ipv.cri.passport.library.config.ParameterStoreParameters.DCS_POST_URL;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DCS_REQUEST_CREATED;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DCS_REQUEST_SEND_ERROR;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DCS_REQUEST_SEND_OK;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DCS_RESPONSE_TYPE_ERROR;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DCS_RESPONSE_TYPE_EXPECTED_HTTP_STATUS;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DCS_RESPONSE_TYPE_INVALID;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DCS_RESPONSE_TYPE_UNEXPECTED_HTTP_STATUS;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIEndpointMetric.DCS_RESPONSE_TYPE_VALID;

@ExtendWith(MockitoExtension.class)
class DcsThirdPartyAPIServiceTest {

    private final String TEST_ENDPOINT_URL = "http://localhost/test";
    private static final String TEST_THIRD_PARTY_RESPONSE_REQUEST_ID = "RID_1234";

    @Mock private EventProbe mockEventProbe;

    @Mock private DcsCryptographyService mockDcsCryptographyService;

    @Mock private ParameterStoreService mockParameterStoreService;

    @Mock private CloseableHttpClient mockCloseableHttpClient;

    @Mock private CloseableHttpResponse mockCloseableHttpResponse;
    @Mock private HttpEntity mockHttpEntity;

    private ThirdPartyAPIService dcsThirdPartyAPIService;

    @BeforeEach
    void setUp() {
        dcsThirdPartyAPIService =
                new DcsThirdPartyAPIService(
                        mockParameterStoreService,
                        mockEventProbe,
                        mockDcsCryptographyService,
                        mockCloseableHttpClient);
    }

    @Test
    void shouldInvokeThirdPartyAPI()
            throws IOException, CertificateException, ParseException, JOSEException,
                    OAuthErrorResponseException, NoSuchAlgorithmException, InvalidKeySpecException {

        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        JWSObject jwsPayloadObject =
                new JWSObject(new JWSHeader(JWSAlgorithm.EdDSA), new Payload("TEST"));
        jwsPayloadObject.sign(new UnitTestJWSSigner());

        when(mockDcsCryptographyService.preparePayload(any(PassportFormData.class)))
                .thenReturn(jwsPayloadObject);

        when(mockParameterStoreService.getParameterValue(DCS_POST_URL))
                .thenReturn(TEST_ENDPOINT_URL);

        ArgumentCaptor<HttpPost> httpRequestCaptor = ArgumentCaptor.forClass(HttpPost.class);
        CloseableHttpResponse httpResponse = createHttpResponse(200);

        when(mockCloseableHttpClient.execute(httpRequestCaptor.capture())).thenReturn(httpResponse);

        when(mockDcsCryptographyService.unwrapDcsResponse(anyString()))
                .thenReturn(createSuccessDcsResponse());

        ThirdPartyAPIResult thirdPartyAPIResult =
                dcsThirdPartyAPIService.performCheck(passportFormData);

        InOrder inOrder = inOrder(mockEventProbe);
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_CREATED.withEndpointPrefix());
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_SEND_OK.withEndpointPrefix());
        inOrder.verify(mockEventProbe)
                .counterMetric(DCS_RESPONSE_TYPE_EXPECTED_HTTP_STATUS.withEndpointPrefix());

        inOrder.verify(mockEventProbe).counterMetric(DCS_RESPONSE_TYPE_VALID.withEndpointPrefix());
        verifyNoMoreInteractions(mockEventProbe);

        assertEquals(TEST_ENDPOINT_URL, httpRequestCaptor.getValue().getURI().toString());
        assertEquals("POST", httpRequestCaptor.getValue().getMethod());
        assertEquals(
                "application/jose",
                httpRequestCaptor.getValue().getFirstHeader("Content-Type").getValue());

        assertNotNull(thirdPartyAPIResult);
        assertEquals(TEST_THIRD_PARTY_RESPONSE_REQUEST_ID, thirdPartyAPIResult.getTransactionId());
        assertTrue(thirdPartyAPIResult.isValid());
    }

    @ParameterizedTest
    @CsvSource({
        "CertificateException",
        "NoSuchAlgorithmException",
        "InvalidKeySpecException",
        "JOSEException",
        "JsonProcessingException",
        "IpvCryptoException",
    })
    void shouldReturnOauthInternalServerForThrowableExceptionsWhenPreparingPayload(
            String exceptionName)
            throws JOSEException, CertificateException, NoSuchAlgorithmException,
                    InvalidKeySpecException, IOException {

        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        JWSObject jwsPayloadObject =
                new JWSObject(new JWSHeader(JWSAlgorithm.EdDSA), new Payload("TEST"));
        jwsPayloadObject.sign(new UnitTestJWSSigner());

        // Determine throwable
        Exception exceptionCaught = null;
        OAuthErrorResponseException expectedReturnedException =
                new OAuthErrorResponseException(
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ErrorResponse.FAILED_TO_PREPARE_DCS_PAYLOAD);
        switch (exceptionName) {
            case "CertificateException":
                exceptionCaught = new CertificateException("Problem with certs");
                break;
            case "NoSuchAlgorithmException":
                exceptionCaught = new NoSuchAlgorithmException("Problem with algorithm");
                break;
            case "InvalidKeySpecException":
                exceptionCaught = new InvalidKeySpecException("Problem with Key");
                break;
            case "JOSEException":
                exceptionCaught = new JOSEException("Problem during signing");
                break;
            case "JsonProcessingException":
                exceptionCaught =
                        new InputCoercionException(null, "Problem during json mapping", null, null);
                break;
            case "IpvCryptoException":
                exceptionCaught = new IpvCryptoException("Problem during payload encryption");
                break;
        }

        doThrow(exceptionCaught)
                .when(mockDcsCryptographyService)
                .preparePayload(any(PassportFormData.class));

        OAuthErrorResponseException thrownException =
                assertThrows(
                        OAuthErrorResponseException.class,
                        () -> dcsThirdPartyAPIService.performCheck(passportFormData),
                        "Expected OAuthErrorResponseException due to " + exceptionName);

        InOrder inOrder = inOrder(mockEventProbe);
        inOrder.verify(mockEventProbe, never())
                .counterMetric(DCS_REQUEST_CREATED.withEndpointPrefix());
        inOrder.verify(mockEventProbe, never())
                .counterMetric(DCS_REQUEST_SEND_OK.withEndpointPrefix());
        inOrder.verify(mockEventProbe, never())
                .counterMetric(DCS_RESPONSE_TYPE_EXPECTED_HTTP_STATUS.withEndpointPrefix());
        inOrder.verify(mockEventProbe, never())
                .counterMetric(DCS_RESPONSE_TYPE_VALID.withEndpointPrefix());
        verifyNoMoreInteractions(mockEventProbe);

        assertEquals(expectedReturnedException.getStatusCode(), thrownException.getStatusCode());
        assertEquals(expectedReturnedException.getErrorReason(), thrownException.getErrorReason());
    }

    @Test
    void shouldReturnOauthInternalServerForIOExceptionExecutingHTTPRequest()
            throws IOException, CertificateException, JOSEException, NoSuchAlgorithmException,
                    InvalidKeySpecException {

        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        JWSObject jwsPayloadObject =
                new JWSObject(new JWSHeader(JWSAlgorithm.EdDSA), new Payload("TEST"));
        jwsPayloadObject.sign(new UnitTestJWSSigner());

        when(mockDcsCryptographyService.preparePayload(any(PassportFormData.class)))
                .thenReturn(jwsPayloadObject);

        when(mockParameterStoreService.getParameterValue(DCS_POST_URL))
                .thenReturn(TEST_ENDPOINT_URL);

        ArgumentCaptor<HttpPost> httpRequestCaptor = ArgumentCaptor.forClass(HttpPost.class);

        // Determine throwable
        Exception exceptionCaught = new IOException("Error during HTTP client execute");
        OAuthErrorResponseException expectedReturnedException =
                new OAuthErrorResponseException(
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ErrorResponse.ERROR_INVOKING_LEGACY_THIRD_PARTY_API);

        doThrow(exceptionCaught).when(mockCloseableHttpClient).execute(httpRequestCaptor.capture());

        OAuthErrorResponseException thrownException =
                assertThrows(
                        OAuthErrorResponseException.class,
                        () -> dcsThirdPartyAPIService.performCheck(passportFormData),
                        "Expected OAuthErrorResponseException due to "
                                + exceptionCaught.getCause());

        InOrder inOrder = inOrder(mockEventProbe);
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_CREATED.withEndpointPrefix());
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_SEND_ERROR.withEndpointPrefix());
        verifyNoMoreInteractions(mockEventProbe);

        assertEquals(expectedReturnedException.getStatusCode(), thrownException.getStatusCode());
        assertEquals(expectedReturnedException.getErrorReason(), thrownException.getErrorReason());
    }

    @Test
    void shouldReturnOauthInternalServerForIOExceptionMappingHTTPResponse()
            throws IOException, CertificateException, JOSEException, NoSuchAlgorithmException,
                    InvalidKeySpecException {

        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        JWSObject jwsPayloadObject =
                new JWSObject(new JWSHeader(JWSAlgorithm.EdDSA), new Payload("TEST"));
        jwsPayloadObject.sign(new UnitTestJWSSigner());

        when(mockDcsCryptographyService.preparePayload(any(PassportFormData.class)))
                .thenReturn(jwsPayloadObject);

        when(mockParameterStoreService.getParameterValue(DCS_POST_URL))
                .thenReturn(TEST_ENDPOINT_URL);

        ArgumentCaptor<HttpPost> httpRequestCaptor = ArgumentCaptor.forClass(HttpPost.class);

        // Determine throwable
        Exception exceptionCaught = new IOException("Error during EntityUtils.getEntity()");
        OAuthErrorResponseException expectedReturnedException =
                new OAuthErrorResponseException(
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ErrorResponse.FAILED_TO_RETRIEVE_HTTP_RESPONSE_BODY);

        // Mocks the entire response handling sequence
        when(mockCloseableHttpClient.execute(httpRequestCaptor.capture()))
                .thenReturn(mockCloseableHttpResponse);
        when(mockCloseableHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        // IOException in the input stream
        doThrow(exceptionCaught).when(mockHttpEntity).getContent();

        OAuthErrorResponseException thrownException =
                assertThrows(
                        OAuthErrorResponseException.class,
                        () -> dcsThirdPartyAPIService.performCheck(passportFormData),
                        "Expected OAuthErrorResponseException due to "
                                + exceptionCaught.getCause());

        InOrder inOrder = inOrder(mockEventProbe);
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_CREATED.withEndpointPrefix());
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_SEND_OK.withEndpointPrefix());
        verifyNoMoreInteractions(mockEventProbe);

        assertEquals(expectedReturnedException.getStatusCode(), thrownException.getStatusCode());
        assertEquals(expectedReturnedException.getErrorReason(), thrownException.getErrorReason());
    }

    @ParameterizedTest
    @CsvSource({
        "IpvCryptoException",
        "CertificateException",
        "ParseException",
        "JOSEException",
    })
    void shouldReturnOauthInternalServerForThrowableExceptionsWhenUnwrappingResponse(
            String exceptionName)
            throws IOException, CertificateException, ParseException, JOSEException,
                    NoSuchAlgorithmException, InvalidKeySpecException {

        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        JWSObject jwsPayloadObject =
                new JWSObject(new JWSHeader(JWSAlgorithm.EdDSA), new Payload("TEST"));
        jwsPayloadObject.sign(new UnitTestJWSSigner());

        when(mockDcsCryptographyService.preparePayload(any(PassportFormData.class)))
                .thenReturn(jwsPayloadObject);

        when(mockParameterStoreService.getParameterValue(DCS_POST_URL))
                .thenReturn(TEST_ENDPOINT_URL);

        ArgumentCaptor<HttpPost> httpRequestCaptor = ArgumentCaptor.forClass(HttpPost.class);
        CloseableHttpResponse httpResponse = createHttpResponse(200);

        when(mockCloseableHttpClient.execute(httpRequestCaptor.capture())).thenReturn(httpResponse);

        // Determine throwable
        Exception exceptionCaught = null;
        OAuthErrorResponseException expectedReturnedException =
                new OAuthErrorResponseException(
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ErrorResponse.FAILED_TO_UNWRAP_DCS_RESPONSE);
        switch (exceptionName) {
            case "IpvCryptoException":
                exceptionCaught = new IpvCryptoException("A signature was invalid");
                break;
            case "CertificateException":
                exceptionCaught =
                        new CertificateException(
                                "Problem with Cert used for signature verification");
                break;
            case "ParseException":
                exceptionCaught = new ParseException("Error parsing payload to JWS", 0);
                break;
            case "InvalidKeySpecException":
                exceptionCaught = new InvalidKeySpecException("Problem with Key");
                break;
            case "JOSEException":
                exceptionCaught =
                        new JOSEException(
                                "Verification of signature using provided certs had an error");
                break;
        }

        doThrow(exceptionCaught).when(mockDcsCryptographyService).unwrapDcsResponse(anyString());

        OAuthErrorResponseException thrownException =
                assertThrows(
                        OAuthErrorResponseException.class,
                        () -> dcsThirdPartyAPIService.performCheck(passportFormData),
                        "Expected OAuthErrorResponseException due to " + exceptionName);

        InOrder inOrder = inOrder(mockEventProbe);
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_CREATED.withEndpointPrefix());
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_SEND_OK.withEndpointPrefix());
        inOrder.verify(mockEventProbe)
                .counterMetric(DCS_RESPONSE_TYPE_EXPECTED_HTTP_STATUS.withEndpointPrefix());
        inOrder.verify(mockEventProbe)
                .counterMetric(DCS_RESPONSE_TYPE_INVALID.withEndpointPrefix());
        verifyNoMoreInteractions(mockEventProbe);

        assertEquals(expectedReturnedException.getStatusCode(), thrownException.getStatusCode());
        assertEquals(expectedReturnedException.getErrorReason(), thrownException.getErrorReason());
    }

    @Test
    void shouldReturnOauthInternalServerWhenDCSReturnsErrorResponse()
            throws IOException, CertificateException, ParseException, JOSEException,
                    NoSuchAlgorithmException, InvalidKeySpecException {

        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        JWSObject jwsPayloadObject =
                new JWSObject(new JWSHeader(JWSAlgorithm.EdDSA), new Payload("TEST"));
        jwsPayloadObject.sign(new UnitTestJWSSigner());

        when(mockDcsCryptographyService.preparePayload(any(PassportFormData.class)))
                .thenReturn(jwsPayloadObject);

        when(mockParameterStoreService.getParameterValue(DCS_POST_URL))
                .thenReturn(TEST_ENDPOINT_URL);

        ArgumentCaptor<HttpPost> httpRequestCaptor = ArgumentCaptor.forClass(HttpPost.class);
        CloseableHttpResponse httpResponse = createHttpResponse(200);

        when(mockCloseableHttpClient.execute(httpRequestCaptor.capture())).thenReturn(httpResponse);

        DcsResponse dscResponse = createSuccessDcsResponse();
        dscResponse.setError(true);
        dscResponse.setErrorMessage(
                List.of("The data sent was not possible on a document in the real world"));

        when(mockDcsCryptographyService.unwrapDcsResponse(anyString())).thenReturn(dscResponse);

        OAuthErrorResponseException expectedReturnedException =
                new OAuthErrorResponseException(
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ErrorResponse.DCS_RETURNED_AN_ERROR_RESPONSE);

        OAuthErrorResponseException thrownException =
                assertThrows(
                        OAuthErrorResponseException.class,
                        () -> dcsThirdPartyAPIService.performCheck(passportFormData),
                        "Expected OAuthErrorResponseException");

        InOrder inOrder = inOrder(mockEventProbe);
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_CREATED.withEndpointPrefix());
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_SEND_OK.withEndpointPrefix());
        inOrder.verify(mockEventProbe)
                .counterMetric(DCS_RESPONSE_TYPE_EXPECTED_HTTP_STATUS.withEndpointPrefix());

        inOrder.verify(mockEventProbe).counterMetric(DCS_RESPONSE_TYPE_ERROR.withEndpointPrefix());
        verifyNoMoreInteractions(mockEventProbe);

        assertEquals(TEST_ENDPOINT_URL, httpRequestCaptor.getValue().getURI().toString());
        assertEquals("POST", httpRequestCaptor.getValue().getMethod());
        assertEquals(
                "application/jose",
                httpRequestCaptor.getValue().getFirstHeader("Content-Type").getValue());

        assertEquals(expectedReturnedException.getStatusCode(), thrownException.getStatusCode());
        assertEquals(expectedReturnedException.getErrorReason(), thrownException.getErrorReason());
    }

    @ParameterizedTest
    @MethodSource("getUnexpectedHTTPStatusCode")
    void shouldReturnOauthInternalServerWhenDCSReturnsUnexpectedHTTPStatusCode(int httpStatusCode)
            throws IOException, CertificateException, JOSEException, NoSuchAlgorithmException,
                    InvalidKeySpecException {

        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        JWSObject jwsPayloadObject =
                new JWSObject(new JWSHeader(JWSAlgorithm.EdDSA), new Payload("TEST"));
        jwsPayloadObject.sign(new UnitTestJWSSigner());

        when(mockDcsCryptographyService.preparePayload(any(PassportFormData.class)))
                .thenReturn(jwsPayloadObject);

        when(mockParameterStoreService.getParameterValue(DCS_POST_URL))
                .thenReturn(TEST_ENDPOINT_URL);

        ArgumentCaptor<HttpPost> httpRequestCaptor = ArgumentCaptor.forClass(HttpPost.class);
        CloseableHttpResponse httpResponse = createHttpResponse(httpStatusCode);

        when(mockCloseableHttpClient.execute(httpRequestCaptor.capture())).thenReturn(httpResponse);

        OAuthErrorResponseException expectedReturnedException =
                new OAuthErrorResponseException(
                        HttpStatusCode.INTERNAL_SERVER_ERROR,
                        ErrorResponse.ERROR_DCS_RETURNED_UNEXPECTED_HTTP_STATUS_CODE);
        ;

        OAuthErrorResponseException thrownException =
                assertThrows(
                        OAuthErrorResponseException.class,
                        () -> dcsThirdPartyAPIService.performCheck(passportFormData),
                        "Expected OAuthErrorResponseException");

        InOrder inOrder = inOrder(mockEventProbe);
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_CREATED.withEndpointPrefix());
        inOrder.verify(mockEventProbe).counterMetric(DCS_REQUEST_SEND_OK.withEndpointPrefix());
        inOrder.verify(mockEventProbe)
                .counterMetric(DCS_RESPONSE_TYPE_UNEXPECTED_HTTP_STATUS.withEndpointPrefix());
        verifyNoMoreInteractions(mockEventProbe);

        assertEquals(TEST_ENDPOINT_URL, httpRequestCaptor.getValue().getURI().toString());
        assertEquals("POST", httpRequestCaptor.getValue().getMethod());
        assertEquals(
                "application/jose",
                httpRequestCaptor.getValue().getFirstHeader("Content-Type").getValue());

        assertEquals(expectedReturnedException.getStatusCode(), thrownException.getStatusCode());
        assertEquals(expectedReturnedException.getErrorReason(), thrownException.getErrorReason());
    }

    private static DcsResponse createSuccessDcsResponse() {
        DcsResponse dcsResponse = new DcsResponse();
        dcsResponse.setCorrelationId("CID_1234");
        dcsResponse.setRequestId(TEST_THIRD_PARTY_RESPONSE_REQUEST_ID);
        dcsResponse.setValid(true);
        return dcsResponse;
    }

    private CloseableHttpResponse createHttpResponse(int statusCode) {
        return new CloseableHttpResponse() {
            @Override
            public ProtocolVersion getProtocolVersion() {
                return null;
            }

            @Override
            public boolean containsHeader(String name) {
                return false;
            }

            @Override
            public Header[] getHeaders(String name) {
                return new Header[0];
            }

            @Override
            public Header getFirstHeader(String name) {
                if ("Accept".equals(name)) {
                    return new BasicHeader(name, "application/jose");
                } else {
                    return new BasicHeader(name, "application/jose");
                }
            }

            @Override
            public Header getLastHeader(String name) {
                return null;
            }

            @Override
            public Header[] getAllHeaders() {
                return new Header[0];
            }

            @Override
            public void addHeader(Header header) {}

            @Override
            public void addHeader(String name, String value) {}

            @Override
            public void setHeader(Header header) {}

            @Override
            public void setHeader(String name, String value) {}

            @Override
            public void setHeaders(Header[] headers) {}

            @Override
            public void removeHeader(Header header) {}

            @Override
            public void removeHeaders(String name) {}

            @Override
            public HeaderIterator headerIterator() {
                return null;
            }

            @Override
            public HeaderIterator headerIterator(String name) {
                return null;
            }

            @Override
            public HttpParams getParams() {
                return null;
            }

            @Override
            public void setParams(HttpParams params) {}

            @Override
            public StatusLine getStatusLine() {
                return new StatusLine() {
                    @Override
                    public ProtocolVersion getProtocolVersion() {
                        return null;
                    }

                    @Override
                    public int getStatusCode() {
                        return statusCode;
                    }

                    @Override
                    public String getReasonPhrase() {
                        return null;
                    }
                };
            }

            @Override
            public void setStatusLine(StatusLine statusline) {}

            @Override
            public void setStatusLine(ProtocolVersion ver, int code) {}

            @Override
            public void setStatusLine(ProtocolVersion ver, int code, String reason) {}

            @Override
            public void setStatusCode(int code) throws IllegalStateException {}

            @Override
            public void setReasonPhrase(String reason) throws IllegalStateException {}

            @Override
            public HttpEntity getEntity() {
                return new HttpEntity() {
                    @Override
                    public boolean isRepeatable() {
                        return false;
                    }

                    @Override
                    public boolean isChunked() {
                        return false;
                    }

                    @Override
                    public long getContentLength() {
                        return 0;
                    }

                    @Override
                    public Header getContentType() {
                        return null;
                    }

                    @Override
                    public Header getContentEncoding() {
                        return null;
                    }

                    @Override
                    public InputStream getContent()
                            throws IOException, UnsupportedOperationException {
                        String initialString = "";
                        InputStream targetStream =
                                new ByteArrayInputStream(initialString.getBytes());
                        return targetStream;
                    }

                    @Override
                    public void writeTo(OutputStream outStream) throws IOException {}

                    @Override
                    public boolean isStreaming() {
                        return false;
                    }

                    @Override
                    public void consumeContent() throws IOException {}
                };
            }

            @Override
            public void setEntity(HttpEntity entity) {}

            @Override
            public Locale getLocale() {
                return null;
            }

            @Override
            public void setLocale(Locale loc) {}

            @Override
            public void close() throws IOException {}
        };
    }

    private static class UnitTestJWSSigner implements JWSSigner {
        @Override
        public Base64URL sign(JWSHeader header, byte[] signingInput) throws JOSEException {
            return new Base64URL("base64Url");
        }

        @Override
        public Set<JWSAlgorithm> supportedJWSAlgorithms() {
            HashSet<JWSAlgorithm> hashSet = new HashSet<>();
            hashSet.add(JWSAlgorithm.EdDSA);
            return hashSet;
        }

        @Override
        public JCAContext getJCAContext() {
            return new JCAContext();
        }
    }

    private static Stream<Integer> getUnexpectedHTTPStatusCode() {
        Stream<Integer> info = IntStream.range(100, 199).boxed();
        Stream<Integer> success = IntStream.range(201, 299).boxed(); // only 200 is expected
        Stream<Integer> redirectCode = IntStream.range(300, 399).boxed();
        Stream<Integer> clientError = IntStream.range(400, 499).boxed();
        Stream<Integer> serverError = IntStream.range(500, 599).boxed();

        return Stream.of(info, success, redirectCode, clientError, serverError).flatMap(s -> s);
    }
}
