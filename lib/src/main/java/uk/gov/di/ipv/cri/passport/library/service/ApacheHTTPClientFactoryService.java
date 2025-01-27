package uk.gov.di.ipv.cri.passport.library.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachehttpclient.v4_3.ApacheHttpClientTelemetry;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import uk.gov.di.ipv.cri.passport.library.helpers.KeyCertHelper;

import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

public class ApacheHTTPClientFactoryService {

    // Used internally at runtime when loading/retrieving keys into/from the SSL Keystore
    private static final char[] RANDOM_RUN_TIME_KEYSTORE_PASSWORD =
            UUID.randomUUID().toString().toCharArray();

    public CloseableHttpClient generatePublicHttpClient() {
        return HttpClients.custom().build();
    }

    public CloseableHttpClient generateHTTPClientFromExternalApacheHttpClient(
            String base64TLSCertString,
            String base64TLSKeyString,
            String base64TLSRootCertString,
            String base64TLSIntCertString)
            throws NoSuchAlgorithmException, InvalidKeySpecException, CertificateException,
                    KeyStoreException, IOException, UnrecoverableKeyException,
                    KeyManagementException {

        Certificate tlsCert = KeyCertHelper.getDecodedX509Certificate(base64TLSCertString);

        PrivateKey tlsKey = KeyCertHelper.getDecodedPrivateRSAKey(base64TLSKeyString);

        KeyStore keystoreTLS = createKeyStore(tlsCert, tlsKey);

        Certificate tlsRootCert = KeyCertHelper.getDecodedX509Certificate(base64TLSRootCertString);

        Certificate tlsIntCert = KeyCertHelper.getDecodedX509Certificate(base64TLSIntCertString);

        KeyStore trustStore = createTrustStore(new Certificate[] {tlsRootCert, tlsIntCert});

        SSLContext sslContext = sslContextSetup(keystoreTLS, trustStore);

        ApacheHttpClientTelemetry apacheHttpClientTelemetry =
                ApacheHttpClientTelemetry.builder(GlobalOpenTelemetry.get()).build();

        return apacheHttpClientTelemetry.newHttpClientBuilder().setSSLContext(sslContext).build();
    }

    private SSLContext sslContextSetup(KeyStore clientTls, KeyStore caBundle)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
                    KeyManagementException {
        return SSLContexts.custom()
                .loadKeyMaterial(clientTls, RANDOM_RUN_TIME_KEYSTORE_PASSWORD)
                .loadTrustMaterial(caBundle, null)
                .build();
    }

    private KeyStore createKeyStore(Certificate cert, Key key)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        final KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, RANDOM_RUN_TIME_KEYSTORE_PASSWORD);

        keyStore.setKeyEntry(
                "TlSKey", key, RANDOM_RUN_TIME_KEYSTORE_PASSWORD, new Certificate[] {cert});
        keyStore.setCertificateEntry("my-ca-1", cert);
        return keyStore;
    }

    private KeyStore createTrustStore(Certificate[] certificates)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        final KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        int k = 0;
        for (Certificate cert : certificates) {
            k++;
            keyStore.setCertificateEntry("my-ca-" + k, cert);
        }
        return keyStore;
    }
}
