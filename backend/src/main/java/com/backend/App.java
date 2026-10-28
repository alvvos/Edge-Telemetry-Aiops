package com.backend;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.servlets.DefaultServlet;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

public class App {

    public static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8080);
        tomcat.getConnector();

        Thread kafkaThread = new Thread(new TelemetryConsumer());

        String docBase = new File(".").getAbsolutePath();
        Context ctx = tomcat.addContext("", docBase);

        Tomcat.addServlet(ctx, "metricsServlet", new MetricsServlet());
        ctx.addServletMappingDecoded("/api/metrics/latest", "metricsServlet");

        tomcat.start();

        kafkaThread.setDaemon(true);
        kafkaThread.start();
        tomcat.getServer().await();
    }
}
