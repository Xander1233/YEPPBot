package de.MCmoderSD.utilities.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import java.security.NoSuchAlgorithmException;
import java.security.KeyStoreException;
import java.security.UnrecoverableKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

@SuppressWarnings("unused")
public class Server {

    // Constants
    private final String hostname;
    private final int port;

    // Attributes
    private final HttpsServer server;

    // Constructor with SSL from JSON
    public Server(JsonNode httpsServerConfig) {
        this(
                httpsServerConfig.get("hostname").asText(),
                httpsServerConfig.get("port").asInt(),
                httpsServerConfig.get("privkey").asText(),
                httpsServerConfig.get("fullchain").asText()
        );
    }

    // Constructor with KeyStore
    public Server(String hostname, int port, String jksPath, JsonNode botConfig) {
        try {

            // Set hostname and port
            this.hostname = hostname;
            this.port = port;

            // Create HTTPS server
            server = HttpsServer.create(new InetSocketAddress(hostname, port), 0);

            // Create SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            KeyStore ks = KeyStore.getInstance("JKS");

            // Get the keystore file
            InputStream keystore = getClass().getResourceAsStream(jksPath);

            // Hash the bot token with SHA-256
            char[] password = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(botConfig.get("botToken").asText().getBytes(StandardCharsets.UTF_8))).toCharArray();

            // Load the keystore
            ks.load(keystore, password);

            // Create key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");

            // Initialize key manager factory with keystore and password
            kmf.init(ks, password);

            // Initialize SSL context with key managers
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Set HTTPS configurator
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters params) {
                    try {

                        // Get default SSL context
                        SSLContext c = SSLContext.getDefault();
                        SSLEngine engine = c.createSSLEngine();
                        params.setNeedClientAuth(false);
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());

                        // Set SSL parameters
                        SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
                        params.setSSLParameters(defaultSSLParameters);
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (CertificateException | IOException | NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException | KeyManagementException e) {
            throw new RuntimeException(e);
        }

        // Start server
        start();

        // Print server info
        System.out.println("Server started on https://" + hostname + ":" + port);
    }

    // Constructor with SSL
    public Server(String hostname, int port, String privKeyPath, String fullChainPath) {
        try {

            // Set hostname and port
            this.hostname = hostname;
            this.port = port;

            // Create HTTPS server
            server = HttpsServer.create(new InetSocketAddress(hostname, port), 0);

            // Create SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            KeyStore ks = KeyStore.getInstance("JKS");

            // Load private key and certificate
            @SuppressWarnings("DataFlowIssue") PrivateKey privKey = (PrivateKey) new FileInputStream(privKeyPath);
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            FileInputStream certInputStream = new FileInputStream(fullChainPath);
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(certInputStream);
            certInputStream.close();

            // Add key pair and certificate to key store
            ks.load(null, null);
            ks.setKeyEntry("alias", privKey, "password".toCharArray(), new Certificate[]{cert});

            // Create key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, "password".toCharArray());

            // Initialize SSL context
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Set HTTPS configurator
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters params) {
                    try {

                        // Get default SSL context
                        SSLContext c = SSLContext.getDefault();
                        SSLEngine engine = c.createSSLEngine();
                        params.setNeedClientAuth(false);
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());

                        // Set SSL parameters
                        SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
                        params.setSSLParameters(defaultSSLParameters);
                    } catch (Exception ex) {
                        System.err.println("Failed to create HTTPS port");
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Start server
        start();

        // Print server info
        System.out.println("Server started on https://" + hostname + ":" + port);
    }

    // Start server
    public void start() {
        server.start();
    }

    public void stop(int delay) {
        server.stop(delay);
    }

    // Getter
    public HttpsServer getHttpServer() {
        return server;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }
}