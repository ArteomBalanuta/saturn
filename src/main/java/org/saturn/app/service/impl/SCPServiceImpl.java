package org.saturn.app.service.impl;


import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.saturn.app.service.SCPService;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;

public class SCPServiceImpl extends OutService implements SCPService {
    
    public SCPServiceImpl(BlockingQueue<String> queue) {
        super(queue);
    }
    
    public void executeRandomSCP(String author) {
        // http://www.scpwiki.com/scp-XXX
        int randomScpId = RandomUtils.nextInt(1, 5500);
        String scpDescription = this.getSCPDescription(randomScpId);
        
        enqueueMessageForSending("","```Text \\n" + scpDescription.trim() + " \\n```\\n " + "@" + author, false);
    }
    
    @Override
    public String getSCPDescription(int scpId) {
        String wikiPage = this.getSCPWiki(scpId);
        String description = StringUtils.substringBetween(wikiPage, "<strong>Description:</strong>", "</p>");
        description = StringEscapeUtils.escapeHtml3(description);
        System.out.println("SCP Service: Description - " + description);
        return description;
    }
    
    private String getSCPWiki(int scpId) {

        CloseableHttpClient httpClient = HttpClients.createDefault();

        String uri = String.format("http://www.scpwiki.com/scp-%d", scpId);
        HttpGet request = new HttpGet(uri);

        // add request headers
        request.addHeader(HttpHeaders.USER_AGENT, "Firefox 59.9.0-custom-branch, HC SCP Community");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            // Get HttpResponse Status
            System.out.println(response.getProtocolVersion());              // HTTP/1.1
            System.out.println(response.getStatusLine().getStatusCode());   // 200
            System.out.println(response.getStatusLine().getReasonPhrase()); // OK
            System.out.println(response.getStatusLine().toString());        // HTTP/1.1 200 OK

            String result = null;
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                // return it as a String
                result = EntityUtils.toString(entity);
            }

            if (response.getStatusLine().getStatusCode() != 200) {
                result = "Classified.";
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
