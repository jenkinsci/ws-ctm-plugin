/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * CTMResult
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm.model;

import jodd.http.HttpResponse;
import jodd.http.HttpStatus;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

public class CTMResult {

  private static final char OPEN_CURLY = '{';
  private static final char CLOSE_CURLY = '}';
  private static final char OPEN_BRACKET = '[';
  private final HttpResponse response;
  private JSONObject jsonData = null;
  private static HttpResponse nullResponse;

  static {
    nullResponse = new HttpResponse();
    nullResponse.statusCode(-1);
    nullResponse.statusPhrase("null response");
  }

  public CTMResult (HttpResponse response) {
    if (response != null) {

      this.response = response;
      if (isJson(response.bodyText())) {
        String jsonText=response.bodyText();
        if (jsonText.startsWith("[")) {
          jsonText = OPEN_CURLY + "\"objects\": " + jsonText + CLOSE_CURLY;
        }
        jsonData = (JSONObject) JSONSerializer.toJSON(jsonText);
      }
    } else {
      this.response = nullResponse;
    }
  }

  public HttpResponse getResponse() {
    return response;
  }

  public JSONObject getJsonData () {
    return jsonData;
  }

  public boolean is200 () {
    return response.statusCode() == HttpStatus.HTTP_OK;
  }

  public String getResponseData () {
    return response.bodyText();
  }

  public boolean isOkAndHasResponse() {
    return response.statusCode() == HttpStatus.HTTP_OK && response.bodyText() != null && response.bodyText().length() >0;
  }

  private boolean isJson (String text) {
    boolean json = false;
    if (text != null && text.length() > 0) {
      char token = text.charAt(0);
      if (token == OPEN_CURLY || token == OPEN_BRACKET) {
        json = true;
      }
    }
    return json;
  }

  public int statusCode () {
    return response.statusCode();
  }

  public boolean isUnauthorized () {
   return response.statusCode() == HttpStatus.HTTP_UNAUTHORIZED;
  }

  public String dumpDebug() {
    return response.toString(true);
  }
}
