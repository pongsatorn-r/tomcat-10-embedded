package com.github.agebhar1;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Server;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.Http11Nio2Protocol;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Tomcat10EmbeddedTests {

    @Test
    public void shouldSucceedWithServletResponse(@TempDir Path catalinaBase) throws LifecycleException {

        withRunningTomcatPort(catalinaBase, port -> {
            try {

                final URL url = new URL("http://localhost:" + port);
                final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Basic dXNlcjpzM2NyM3Q=");

                assertEquals(200, connection.getResponseCode());
                assertEquals("Hello from Embedded Tomcat\n", responseBody(connection));

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

    }

    @Test
    public void shouldFailWith401Unauthorized(@TempDir Path catalinaBase) throws LifecycleException {

        withRunningTomcatPort(catalinaBase, port -> {
            try {

                final URL url = new URL("http://localhost:" + port);
                final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                assertEquals(401, connection.getResponseCode());

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

    }

    private static void withRunningTomcatPort(final Path catalinaBase, final Consumer<Integer> consumer) throws LifecycleException {

        final Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(catalinaBase.toString());
        final Http11Nio2Protocol protocol = new Http11Nio2Protocol();

        final Connector connector = new Connector(protocol);
        connector.setPort(0 /* OS provided */);

        tomcat.getService().addConnector(connector);

        final Context ctx = tomcat.addWebapp("", catalinaBase.toString());
        ctx.getJarScanner().setJarScanFilter((jarScanType, jarName) -> false);

        Tomcat.addServlet(ctx, "embedded", new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
                try (final ServletOutputStream os = res.getOutputStream()) {
                    os.print("Hello from Embedded Tomcat\n");
                    os.flush();
                }
            }

        });
        ctx.addServletMappingDecoded("/*", "embedded");

        final SecurityCollection securityCollection = new SecurityCollection("ApplicationContent", "");
        securityCollection.addPattern("/*");

        final SecurityConstraint securityConstraint = new SecurityConstraint();
        securityConstraint.addCollection(securityCollection);
        securityConstraint.addAuthRole("appuser");

        ctx.addConstraint(securityConstraint);
        ctx.addSecurityRole("appuser");
        ctx.setLoginConfig(new LoginConfig("BASIC", "Secured App", null, null));

        tomcat.addUser("user", "s3cr3t");
        tomcat.addRole("user", "appuser");

        tomcat.init();
        tomcat.start();

        try {
            consumer.accept(protocol.getLocalPort());
        } finally {
            final Server server = tomcat.getServer();
            /* server.await(); */
            server.stop();
        }
    }

    private static String responseBody(HttpURLConnection connection) throws IOException {

        final InputStream is = connection.getInputStream();
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];

        for (int length; (length = is.read(buffer)) != -1; ) {
            os.write(buffer, 0, length);
        }

        return os.toString();
    }

}
