import spark.ModelAndView;
import spark.Request;
import spark.template.freemarker.FreeMarkerEngine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static spark.Spark.*;

public class Main {

    private static Docusign getDocusign(Request request) {
        String fullName = request.session().attribute("fullName");
        String memberNumber = request.session().attribute("memberNumber");
        String returnURL = request.url().substring(0, request.url().lastIndexOf("/")) + "/confirm";
        returnURL = returnURL.replaceAll("http:", "https:");
        System.out.println("Return URL = " + returnURL);
        Docusign docusign = new Docusign(fullName, memberNumber, returnURL);
        return docusign;
    }

    public static void main(String[] args) {

        port(Integer.valueOf(System.getenv("PORT")));
        staticFileLocation("/public");

        get("/ushpa", (request, response) -> {
            Map<String, Object> attributes = new HashMap<>();

            request.session().attribute("fullName", "Julie Thermalhunter");
            request.session().attribute("memberNumber", "80468");

            attributes.put("fullName", request.session().attribute("fullName"));
            attributes.put("memberNumber", request.session().attribute("memberNumber"));

            return new ModelAndView(attributes, "ushpa.ftl");
        }, new FreeMarkerEngine());

        get("/sign", (request, response) -> {
            Map<String, Object> attributes = new HashMap<>();
            Docusign docusign = getDocusign(request);
            try {
                EnvelopeInfo einfo = docusign.getEnvelopeInfo();
                request.session().attribute("envelopeInfo", einfo);
                attributes.put("signatureURL", einfo.signatureUrl);
                return new ModelAndView(attributes, "sign.ftl");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, new FreeMarkerEngine());

        get("/confirm", (request, response) -> {
            Map<String, Object> attributes = new HashMap<>();
            try {
                if ("signing_complete".equals(request.queryParams("event"))) {
                    System.out.println("Signature successful");
                    Docusign docusign = getDocusign(request);
                    String certURL = docusign.getCertificate(request.session().attribute("envelopeInfo"));
                    System.out.println("Certificate URL = " + certURL);
                    attributes.put("message", "Saved certificate URL to database: " + certURL);
                } else {
                    StringBuilder sbuf = new StringBuilder();
                    sbuf.append("\n");
                    for (String param : request.queryParams()) {
                        sbuf.append(param + "=" + request.queryParams(param) + "\n");
                    }

                    attributes.put("message", sbuf.toString());
                }

                return new ModelAndView(attributes, "confirm.ftl");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }, new FreeMarkerEngine());

        exception(Exception.class, (e, request, response) -> {
            response.status(500);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            response.body("<html></head><body><pre>" + sw.toString() + "</pre></body>");
        });
    }

}
