package org.saturn.app.service.impl;

import java.io.IOException;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.saturn.app.service.SearchService;

/* TODO: impl the cmd */
public class SearchServiceImpl implements SearchService {
  public SearchServiceImpl() {}

  // https://api.duckduckgo.com/?q=aloha%20wiki&format=json&pretty=1

  @Override
  public String search(String string) {
    CloseableHttpClient httpClient = HttpClients.createDefault();

    String uri =
        String.format(
            "https://api.duckduckgo.com/?q=%s&format=json&pretty=1", string.replace(" ", "%20"));
    HttpGet request = new HttpGet(uri);

    // add request headers
    request.addHeader(HttpHeaders.USER_AGENT, "Firefox 59.9.0, HC");

    try (CloseableHttpResponse response = httpClient.execute(request)) {
      String result = null;
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        // return it as a String
        result = EntityUtils.toString(entity);
      }

      if (response.getStatusLine().getStatusCode() != 200) {
        result = "Please pay for the service requested.";
      }

      return StringEscapeUtils.escapeJson(result);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }
}
