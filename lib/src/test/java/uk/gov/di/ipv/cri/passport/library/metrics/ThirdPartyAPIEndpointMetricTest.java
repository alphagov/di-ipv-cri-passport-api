package uk.gov.di.ipv.cri.passport.library.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.di.ipv.cri.passport.library.error.ErrorResponse;
import uk.gov.di.ipv.cri.passport.library.exceptions.MetricException;
import uk.gov.di.ipv.cri.passport.library.exceptions.OAuthErrorResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIMetricEndpointPrefix.DVAD_THIRD_PARTY_API_HEALTH_ENDPOINT;
import static uk.gov.di.ipv.cri.passport.library.metrics.ThirdPartyAPIMetricEndpointPrefix.DVAD_THIRD_PARTY_API_TOKEN_ENDPOINT;

@ExtendWith(MockitoExtension.class)
class ThirdPartyAPIEndpointMetricTest {

    @Test
    void assertEndpointMetricsAreGeneratedCorrectly() {
        // The following test checks that the list of metrics generated by the
        // ThirdPartyAPIEndpointMetric
        // match the expected formats and values

        // Enums used to generate metrics
        List<ThirdPartyAPIMetricEndpointPrefix> endpointPrefixes =
                new ArrayList<>(Arrays.asList(ThirdPartyAPIMetricEndpointPrefix.values()));
        List<ThirdPartyAPIEndpointMetricType> baseMetrics =
                new ArrayList<>(Arrays.asList(ThirdPartyAPIEndpointMetricType.values()));

        // All possible combinations of metrics as enums
        List<ThirdPartyAPIEndpointMetric> enumGeneratedMetrics =
                new ArrayList<>(Arrays.asList(ThirdPartyAPIEndpointMetric.values()));

        // Create a list of the actual values used as metrics using the enums
        List<String> enumGeneratedMetricsStrings = new LinkedList<>();
        for (ThirdPartyAPIEndpointMetric enumGeneratedMetric : enumGeneratedMetrics) {
            String metricString = enumGeneratedMetric.withEndpointPrefix();
            enumGeneratedMetricsStrings.add(metricString);
        }

        // Generate all combinations of metrics from the enums in the expected format
        String expectedFormat = "%s_%s";
        List<String> expectedMetricsCaptureList = new LinkedList<>();
        int prefixSize = endpointPrefixes.size();
        int metricsSize = baseMetrics.size();
        for (int p = 0; p < prefixSize; p++) {
            for (int bm = 0; bm < metricsSize; bm++) {

                String endpointPrefix = endpointPrefixes.get(p).toString();
                String baseMetric = baseMetrics.get(bm).toString();

                String expectedCombinedMetric =
                        String.format(expectedFormat, endpointPrefix, baseMetric).toLowerCase();

                expectedMetricsCaptureList.add(expectedCombinedMetric);
            }
        }

        // Remove the two generate error types not created in ThirdPartyAPIEndpointMetric
        expectedMetricsCaptureList.remove(
                "dvad_third_party_api_health_endpoint_api_response_type_error"); // Not Used
        expectedMetricsCaptureList.remove(
                "dvad_third_party_api_token_endpoint_api_response_type_error"); // Not Used

        // Add the two special case health status metrics added via string in
        // ThirdPartyAPIEndpointMetric
        expectedMetricsCaptureList.add(
                String.format(expectedFormat, DVAD_THIRD_PARTY_API_HEALTH_ENDPOINT, "UP")
                        .toLowerCase());
        expectedMetricsCaptureList.add(
                String.format(expectedFormat, DVAD_THIRD_PARTY_API_HEALTH_ENDPOINT, "DOWN")
                        .toLowerCase());
        // Add Special case token reuse metric
        expectedMetricsCaptureList.add(
                String.format(
                                expectedFormat,
                                DVAD_THIRD_PARTY_API_TOKEN_ENDPOINT,
                                "reusing_cached_token")
                        .toLowerCase());

        // Sort the two lists so the orders are the same
        Collections.sort(expectedMetricsCaptureList);
        Collections.sort(enumGeneratedMetricsStrings);

        int expectedSize = expectedMetricsCaptureList.size();
        int enumGenerated = enumGeneratedMetricsStrings.size();

        // Assert the two lists are same size
        assertEquals(expectedSize, enumGeneratedMetricsStrings.size());

        // Assert the two lists are identical (both sorted)
        for (int m = 0; m < expectedSize; m++) {

            String expected = expectedMetricsCaptureList.get(m);

            String valueFromEnum = enumGeneratedMetricsStrings.get(m);

            assertEquals(expected, valueFromEnum);
        }
    }

    @Test
    void
            assertMetricExceptionIsThrownIfWithEndpointPrefixAndExceptionNameIsSuppliedOAuthErrorResponseException() {

        OAuthErrorResponseException internalExceptionTypeThatShouldNotBeCapturedAsMetric =
                new OAuthErrorResponseException(
                        500, ErrorResponse.FAILED_TO_SEND_AUDIT_MESSAGE_TO_SQS_QUEUE);

        assertThrows(
                MetricException.class,
                () ->
                        ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_REQUEST_SEND_ERROR
                                .withEndpointPrefixAndExceptionName(
                                        internalExceptionTypeThatShouldNotBeCapturedAsMetric));
    }

    @Test
    void
            assertMetricExceptionIsNotThrownIfWithEndpointPrefixAndExceptionNameIsSuppliedAnAcceptableException() {

        Exception ioException = new IOException("Connection Timed Out");

        assertDoesNotThrow(
                () ->
                        ThirdPartyAPIEndpointMetric.DVAD_GRAPHQL_REQUEST_SEND_ERROR
                                .withEndpointPrefixAndExceptionName(ioException));
    }
}
