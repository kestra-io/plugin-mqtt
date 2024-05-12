package io.kestra.plugin.mqtt.services;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class CustomSSLSocketFactory {

	public static SSLSocketFactory createSSLSocketFactory(String certificateFilePath) throws Exception {
		try (InputStream certificateInputStream = new FileInputStream(certificateFilePath)) {
			// Load the certificate file

            // Load certificate into KeyStore
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            keyStore.setCertificateEntry("ca-certificate", (X509Certificate) certificateFactory.generateCertificate(certificateInputStream));

            // Create TrustManagerFactory with the loaded KeyStore
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            // Create SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            return sslContext.getSocketFactory();
		}
	}

}
