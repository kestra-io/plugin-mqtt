package io.kestra.plugin.mqtt.services;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class CustomSSLSocketFactory {

    private CustomSSLSocketFactory() {
        // utility class
    }

    /**
     * Creates an {@link SSLSocketFactory} that trusts the CA certificate provided as an {@link InputStream}.
     *
     * @param certificateInputStream an input stream containing a PEM-encoded X.509 CA certificate
     * @return an SSLSocketFactory configured to trust the provided CA certificate
     * @throws GeneralSecurityException if the certificate cannot be parsed or the trust store cannot be initialized
     * @throws IOException              if the input stream cannot be read
     */
    public static SSLSocketFactory createSSLSocketFactory(InputStream certificateInputStream) throws GeneralSecurityException, IOException {
        // Load certificate into KeyStore
        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        var certificateFactory = CertificateFactory.getInstance("X.509");
        keyStore.setCertificateEntry("ca-certificate", (X509Certificate) certificateFactory.generateCertificate(certificateInputStream));

        // Create TrustManagerFactory with the loaded KeyStore
        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        // Create SSLContext
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        return sslContext.getSocketFactory();
    }
}
