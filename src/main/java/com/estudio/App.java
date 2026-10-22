package com.estudio;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

public class App {
    public static void main(String[] args) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8080);
        tomcat.getConnector();
        String docBase = new File(".").getAbsolutePath();
        Context ctx = tomcat.addContext("", docBase);

        HttpServlet miServlet = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
                res.setStatus(200);
                res.setContentType("application/json");
                res.getWriter().write("{ \"mensaje\": \"Servidor construido desde cero\" }");
            }
        };

	Tomcat.addServlet(ctx, "MiServletBase", miServlet);
        ctx.addServletMappingDecoded("/api", "MiServletBase");

        tomcat.start();
        System.out.println("Servidor escuchando en http://localhost:8080/api");
        tomcat.getServer().await();
    }
}
