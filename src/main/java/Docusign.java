import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.xpath.*;

import org.xml.sax.InputSource;
import spark.utils.IOUtils;

/**
 * Created by sroy on 1/11/16.
 */
public class Docusign {
    private String fullName;
    private String memberNumber;
    private String returnURL;

    public Docusign(String fullName, String memberNumber, String returnURL) {
        this.fullName = fullName;
        this.memberNumber = memberNumber;
        this.returnURL = returnURL;
    }

    private String getAuthHeader() {
        //------------------------------------------------------------------------------------
        // These are fixed values that are part of the USHPA Docusign account:
        //------------------------------------------------------------------------------------

        String integratorKey = "USHP-fc0436e3-1aae-4202-8d3b-fa954c6fb3ba"; // integrator key (found on Preferences -> API page)
        String username = "saptarshir@gmail.com";                           // account email (or your API userId)
        String password = "yiM0M14RpDwT8rVwT8";                             // account password

        String authenticationHeader =
                "<DocuSignCredentials>" +
                        "<Username>" + username + "</Username>" +
                        "<Password>" + password + "</Password>" +
                        "<IntegratorKey>" + integratorKey + "</IntegratorKey>" +
                        "</DocuSignCredentials>";
        return authenticationHeader;
    }

    public String getCertificate(EnvelopeInfo einfo) throws IOException {
        LoginInfo loginInfo = login();

        String url = loginInfo.baseURL + "/envelopes/" + einfo.envelopeId;
        HttpURLConnection conn = initializeRequest(url, "GET", "");
        String response = getResponseBody(conn);
        String certificateURI = parseXMLBody(response, "certificateUri");

        url = loginInfo.baseURL + certificateURI;
        return url;
    }

    public EnvelopeInfo getEnvelopeInfo() throws IOException {

        //------------------------------------------------------------------------------------
        // Specific properties of the template we have setup via the UI
        //------------------------------------------------------------------------------------

        String templateId = "0c65f68f-8453-4c30-ad47-a345207a331f";         // template ID copied from a template in your DocuSign account
        String roleName = "Member";                                          // name of the template role for above account template

        //------------------------------------------------------------------------------------
        // These are values specific to each USHPA member, and should come from the web site
        // login session
        //------------------------------------------------------------------------------------
        String recipientName = this.fullName;                            // recipient (signer) name
        String recipientEmail = "saptarshir@yahoo.com";                  // recipient (signer) email
        String ushpaMemberNumber = this.memberNumber;

        //============================================================================
        // STEP 1 - Make the Login API call to retrieve your baseUrl and accountId
        //============================================================================

        // additional variable declarations
        HttpURLConnection conn = null;        // connection object used for each request
        String url = "";            // end-point for each api call
        String body = "";            // request body
        String response = "";            // response body
        int status;                // response status

        LoginInfo loginInfo = login();

        //============================================================================
        // STEP 2 - Signature Request from Template API Call
        //============================================================================

        url = loginInfo.baseURL + "/envelopes";    // append "/envelopes" to baseUrl for signature request call

        // this example uses XML formatted requests, JSON format is also accepted
        body = "<envelopeDefinition xmlns=\"http://www.docusign.com/restapi\">" +
                "<accountId>" + loginInfo.accountId + "</accountId>" +
                "<status>sent</status>" +
                "<emailSubject>USHPA Website Waiver Signature</emailSubject>" +
                "<templateId>" + templateId + "</templateId>" +
                "<templateRoles>" +
                "<templateRole>" +
                "<email>" + recipientEmail + "</email>" +
                "<name>" + recipientName + "</name>" +
                "<roleName>" + roleName + "</roleName>" +
                "<clientUserId>1001</clientUserId>" +    // required for embedded sending (value is user-defined)
                "<tabs>" +
                "<numberTabs>" +
                "<number>" +
                "<tabLabel>memberNumber</tabLabel><value>" + ushpaMemberNumber + "</value>" +
                "</number>" +
                "</numberTabs>" +
                "</tabs>" +
                "</templateRole>" +
                "</templateRoles>" +
                "</envelopeDefinition>";

        System.out.printf("Request Body to URL: %s\n", url);
        System.out.println("-----------------");
        System.out.println(prettyFormat(body, 2));
        System.out.println("-----------------");

        // re-use connection object for second request...
        conn = initializeRequest(url, "POST", body);

        System.out.println("Step 2:  Creating envelope from template...\n");
        status = conn.getResponseCode(); // triggers the request
        if (status != 201)    // 201 = Created
        {
            errorParse(conn, status);
            return null;
        }

        // obtain envelope uri from response body
        response = getResponseBody(conn);
        String uri = parseXMLBody(response, "uri");
        String envelopeId = parseXMLBody(response, "envelopeId");
        System.out.println("-- Envelope Creation response --\n\n" + prettyFormat(response, 2));

        //============================================================================
        // STEP 3 - Get the Embedded Signing View
        //============================================================================

        url = loginInfo.baseURL + uri + "/views/recipient";    // append envelope uri + "views/recipient" to url

        // this example uses XML formatted requests, JSON format is also accepted
        body = "<recipientViewRequest xmlns=\"http://www.docusign.com/restapi\">" +
                "<authenticationMethod>email</authenticationMethod>" +
                "<email>" + recipientEmail + "</email>" +
                "<returnUrl>" + this.returnURL + "</returnUrl>" +
                "<clientUserId>1001</clientUserId>" +    //*** must match clientUserId set in Step 2!
                "<userName>" + recipientName + "</userName>" +
                "</recipientViewRequest>";

        System.out.print("Step 3:  Generating URL token for embedded signing... ");
        conn = initializeRequest(url, "POST", body);
        status = conn.getResponseCode(); // triggers the request
        if (status != 201)    // 201 = Created
        {
            errorParse(conn, status);
            return null;
        }
        System.out.println("done.");

        response = getResponseBody(conn);
        String urlToken = parseXMLBody(response, "url");
        System.out.println("\nEmbedded signing token created:\n\t" + urlToken);

        EnvelopeInfo einfo = new EnvelopeInfo();
        einfo.signatureUrl = urlToken;
        einfo.envelopeId = envelopeId;

        return einfo;
    }

    private LoginInfo login() throws IOException {
        String url;
        String body;
        HttpURLConnection conn;
        int status;
        String response;
        url = "https://demo.docusign.net/restapi/v2/login_information";
        body = "";    // no request body for the login call

        // create connection object, set request method, add request headers
        conn = initializeRequest(url, "GET", body);

        // send the request
        System.out.println("Step 1:  Sending Login request...\n");
        status = conn.getResponseCode();
        if (status != 200)    // 200 = OK
        {
            errorParse(conn, status);
            return null;
        }

        // obtain baseUrl and accountId values from response body
        response = getResponseBody(conn);
        LoginInfo loginInfo = new LoginInfo();
        loginInfo.baseURL = parseXMLBody(response, "baseUrl");
        loginInfo.accountId = parseXMLBody(response, "accountId");
        System.out.println("-- Login response --\n\n" + prettyFormat(response, 2) + "\n");
        return loginInfo;
    }

    //***********************************************************************************************
    //***********************************************************************************************
    // --- HELPER FUNCTIONS ---
    //***********************************************************************************************
    //***********************************************************************************************
    private HttpURLConnection initializeRequest(String url, String method, String body) {

        String httpAuthHeader = getAuthHeader();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();

            conn.setRequestMethod(method);
            conn.setRequestProperty("X-DocuSign-Authentication", httpAuthHeader);
            conn.setRequestProperty("Content-Type", "application/xml");
            conn.setRequestProperty("Accept", "application/xml");
            if (method.equalsIgnoreCase("POST")) {
                conn.setRequestProperty("Content-Length", Integer.toString(body.length()));
                conn.setDoOutput(true);
                // write body of the POST request
                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                dos.writeBytes(body);
                dos.flush();
                dos.close();
            }
            return conn;

        } catch (Exception e) {
            throw new RuntimeException(e); // simple exception handling, please review it
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    private String parseXMLBody(String body, String searchToken) {
        String xPathExpression;
        try {
            // we use xPath to parse the XML formatted response body
            xPathExpression = String.format("//*[1]/*[local-name()='%s']", searchToken);
            XPath xPath = XPathFactory.newInstance().newXPath();
            return (xPath.evaluate(xPathExpression, new InputSource(new StringReader(body))));
        } catch (Exception e) {
            throw new RuntimeException(e); // simple exception handling, please review it
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    private String getResponseBody(HttpURLConnection conn) {
        BufferedReader br = null;
        StringBuilder body = null;
        String line = "";
        try {
            // we use xPath to get the baseUrl and accountId from the XML response body
            br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            body = new StringBuilder();
            while ((line = br.readLine()) != null)
                body.append(line);
            return body.toString();
        } catch (Exception e) {
            throw new RuntimeException(e); // simple exception handling, please review it
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    private void errorParse(HttpURLConnection conn, int status) {
        BufferedReader br;
        String line;
        StringBuilder responseError;
        try {
            System.out.print("API call failed, status returned was: " + status);
            InputStreamReader isr = new InputStreamReader(conn.getErrorStream());
            br = new BufferedReader(isr);
            responseError = new StringBuilder();
            line = null;
            while ((line = br.readLine()) != null)
                responseError.append(line);
            System.out.println("\nError description:  \n" + prettyFormat(responseError.toString(), 2));
            return;
        } catch (Exception e) {
            throw new RuntimeException(e); // simple exception handling, please review it
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    private String prettyFormat(String input, int indent) {
        try {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", indent);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
        } catch (Exception e) {
            throw new RuntimeException(e); // simple exception handling, please review it
        }
    }
}
