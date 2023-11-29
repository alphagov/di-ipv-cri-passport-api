package uk.gov.di.ipv.cri.passport.issuecredential.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.di.ipv.cri.common.library.domain.personidentity.PersonIdentityDetailed;
import uk.gov.di.ipv.cri.passport.library.DocumentCheckTestDataGenerator;
import uk.gov.di.ipv.cri.passport.library.PassportFormTestDataGenerator;
import uk.gov.di.ipv.cri.passport.library.VerifiableCredentialServiceTestFixtures;
import uk.gov.di.ipv.cri.passport.library.domain.PassportFormData;
import uk.gov.di.ipv.cri.passport.library.helpers.PersonIdentityDetailedHelperMapper;
import uk.gov.di.ipv.cri.passport.library.persistence.DocumentCheckResultItem;
import uk.gov.di.ipv.cri.passport.library.service.PassportConfigurationService;
import uk.gov.di.ipv.cri.passport.library.service.ServiceFactory;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static uk.gov.di.ipv.cri.passport.issuecredential.domain.VerifiableCredentialConstants.VC_BIRTHDATE_KEY;
import static uk.gov.di.ipv.cri.passport.issuecredential.domain.VerifiableCredentialConstants.VC_CLAIM_KEY;
import static uk.gov.di.ipv.cri.passport.issuecredential.domain.VerifiableCredentialConstants.VC_CLAIM_TYPE_KEY;
import static uk.gov.di.ipv.cri.passport.issuecredential.domain.VerifiableCredentialConstants.VC_CREDENTIAL_TYPE_ICC;
import static uk.gov.di.ipv.cri.passport.issuecredential.domain.VerifiableCredentialConstants.VC_CREDENTIAL_TYPE_VC;
import static uk.gov.di.ipv.cri.passport.issuecredential.domain.VerifiableCredentialConstants.VC_EVIDENCE_KEY;
import static uk.gov.di.ipv.cri.passport.issuecredential.domain.VerifiableCredentialConstants.VC_NAME_KEY;
import static uk.gov.di.ipv.cri.passport.issuecredential.domain.VerifiableCredentialConstants.VC_PASSPORT_KEY;
import static uk.gov.di.ipv.cri.passport.issuecredential.domain.VerifiableCredentialConstants.VC_SUBJECT_KEY;
import static uk.gov.di.ipv.cri.passport.library.config.GlobalConstants.UK_ICAO_ISSUER_CODE;
import static uk.gov.di.ipv.cri.passport.library.config.ParameterStoreParameters.MAX_JWT_TTL_UNIT;

@ExtendWith(MockitoExtension.class)
class VerifiableCredentialServiceTest implements VerifiableCredentialServiceTestFixtures {
    private static final Logger LOGGER = LogManager.getLogger();

    private final String UNIT_TEST_VC_ISSUER = "UNIT_TEST_VC_ISSUER";
    private final String UNIT_TEST_SUBJECT = "urn:fdc:12345678";

    @Mock private ServiceFactory mockServiceFactory;

    // Returned via the ServiceFactory
    private final ObjectMapper realObjectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());
    @Mock private PassportConfigurationService mockPassportConfigurationService;

    private VerifiableCredentialService verifiableCredentialService;

    @BeforeEach
    void setup() throws InvalidKeySpecException, NoSuchAlgorithmException, JOSEException {

        JWSSigner jwsSigner = new ECDSASigner(getPrivateKey());

        mockServiceFactoryBehaviour();

        verifiableCredentialService =
                new VerifiableCredentialService(mockServiceFactory, jwsSigner);
    }

    @ParameterizedTest
    @CsvSource({
        "3600, SECONDS, true", // 1 hour, Verified VC
        "1814400, SECONDS, true", // 3 weeks, Verified VC
        "15780000, SECONDS, true", // 6 months, Verified VC
        "3600, SECONDS, false", // 1 hour, Unverified VC
        "1814400, SECONDS, false", // 3 weeks, Unverified VC
        "15780000, SECONDS, false", // 6 months, Unverified VC
    })
    void shouldGenerateSignedVerifiableCredentialJWTWithMaxTTL(
            String maxJwtTtl, String maxJwtTtlUnit, boolean verified)
            throws JOSEException, JsonProcessingException, ParseException {

        final long TTL = Long.parseLong(maxJwtTtl);
        final String JWT_TTL_UNIT = maxJwtTtlUnit;

        UUID sessionID = UUID.randomUUID();

        PassportFormData passportFormData = PassportFormTestDataGenerator.generate();

        PersonIdentityDetailed personIdentityDetailed =
                PersonIdentityDetailedHelperMapper.passportFormDataToAuditRestrictedFormat(
                        passportFormData);

        DocumentCheckResultItem documentCheckResultItem;
        if (verified) {
            documentCheckResultItem =
                    DocumentCheckTestDataGenerator.generateVerifiedResultItem(
                            sessionID, passportFormData.getPassportNumber());
        } else {
            documentCheckResultItem =
                    DocumentCheckTestDataGenerator.generateUnverifiedResultItem(
                            sessionID, passportFormData.getPassportNumber());
        }

        when(mockPassportConfigurationService.getMaxJwtTtl()).thenReturn(TTL);
        when(mockPassportConfigurationService.getStackParameterValue(MAX_JWT_TTL_UNIT))
                .thenReturn(JWT_TTL_UNIT);
        when(mockPassportConfigurationService.getVerifiableCredentialIssuer())
                .thenReturn(UNIT_TEST_VC_ISSUER);

        SignedJWT signedJWT =
                verifiableCredentialService.generateSignedVerifiableCredentialJwt(
                        UNIT_TEST_SUBJECT, documentCheckResultItem, personIdentityDetailed);

        JWTClaimsSet generatedClaims = signedJWT.getJWTClaimsSet();
        assertTrue(
                signedJWT.verify(
                        new ECDSAVerifier(
                                ECKey.parse(
                                        VerifiableCredentialServiceTestFixtures.EC_PUBLIC_JWK_1))));

        String jsonGeneratedClaims =
                realObjectMapper
                        .writer()
                        .withDefaultPrettyPrinter()
                        .writeValueAsString(generatedClaims);
        LOGGER.info(jsonGeneratedClaims);

        JsonNode claimsSet = realObjectMapper.readTree(generatedClaims.toString());
        assertNotNull(claimsSet);
        assertEquals(5, claimsSet.size());

        assertEquals(UNIT_TEST_SUBJECT, claimsSet.get("sub").textValue());
        assertEquals(UNIT_TEST_VC_ISSUER, claimsSet.get("iss").textValue());
        long notBeforeTime = claimsSet.get("nbf").asLong();
        final long expirationTime = claimsSet.get("exp").asLong();
        assertEquals(TTL, expirationTime - notBeforeTime);

        // VC Type
        assertEquals(
                String.valueOf(List.of(VC_CREDENTIAL_TYPE_VC, VC_CREDENTIAL_TYPE_ICC))
                        .replaceAll("\\[", "\\[\"") // Add quotes to match json array
                        .replaceAll("\\]", "\"\\]")
                        .replaceAll(", ", "\",\""),
                claimsSet.get(VC_CLAIM_KEY).get(VC_CLAIM_TYPE_KEY).toString());

        // Subject
        JsonNode subject = claimsSet.get(VC_CLAIM_KEY).get(VC_SUBJECT_KEY);
        assertNotNull(subject);

        // Dob
        assertEquals(
                personIdentityDetailed.getBirthDates().get(0).getValue().toString(),
                subject.get(VC_BIRTHDATE_KEY).get(0).get("value").asText());

        // Names
        assertEquals(
                realObjectMapper.writeValueAsString(personIdentityDetailed.getNames()),
                subject.get(VC_NAME_KEY).toString());

        // Passport (0)
        JsonNode passport = subject.get(VC_PASSPORT_KEY).get(0);
        assertNotNull(passport);

        assertEquals(
                documentCheckResultItem.getDocumentNumber(),
                passport.get("documentNumber").asText());
        assertEquals(UK_ICAO_ISSUER_CODE, passport.get("icaoIssuerCode").asText());
        assertEquals(documentCheckResultItem.getExpiryDate(), passport.get("expiryDate").asText());

        // Evidence (0)
        JsonNode evidence = claimsSet.get(VC_CLAIM_KEY).get(VC_EVIDENCE_KEY).get(0);
        assertNotNull(evidence);

        assertEquals("IdentityCheck", evidence.get("type").asText());
        assertEquals(documentCheckResultItem.getTransactionId(), evidence.get("txn").asText());
        assertEquals(
                documentCheckResultItem.getStrengthScore(), evidence.get("strengthScore").asInt());
        assertEquals(
                documentCheckResultItem.getValidityScore(), evidence.get("validityScore").asInt());

        if (verified) {
            // Verified VC has no CI
            assertNull(evidence.get("ci"));
            assertEquals(
                    "[{\"checkMethod\":\"data\",\"dataCheck\":\"verification_check\"}]",
                    evidence.get("checkDetails").toString());

        } else {
            assertEquals(
                    documentCheckResultItem.getContraIndicators().get(0),
                    evidence.get("ci").get(0).asText());
            assertEquals(
                    "[{\"checkMethod\":\"data\",\"dataCheck\":\"verification_check\"}]",
                    evidence.get("failedCheckDetails").toString());
        }

        ECDSAVerifier ecVerifier =
                new ECDSAVerifier(
                        ECKey.parse(VerifiableCredentialServiceTestFixtures.EC_PUBLIC_JWK_1));
        assertTrue(signedJWT.verify(ecVerifier));
    }

    private void mockServiceFactoryBehaviour() {
        when(mockServiceFactory.getObjectMapper()).thenReturn(realObjectMapper);
        when(mockServiceFactory.getPassportConfigurationService())
                .thenReturn(mockPassportConfigurationService);
    }
}
