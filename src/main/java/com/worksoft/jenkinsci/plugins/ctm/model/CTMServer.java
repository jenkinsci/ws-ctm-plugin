/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * CTMServer
 *
 * @author rrinehart
 */
/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * CTMServer
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm.model;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.worksoft.jenkinsci.plugins.ctm.CTMExecute.JobDetails;

import jodd.http.HttpException;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.runtime.StackTraceUtils;

import java.io.UnsupportedEncodingException;
//import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CTMServer {

  private static final Logger log = Logger.getLogger("jenkins.wsCTMServer");

  private final String portalUrl;
  private final UsernamePasswordCredentials credentials;

  private Auth auth;

  private String authenticationUrl = "";
  private String ctmUrl = "";
  private CTMResult lastCTMResult;
  // private static final String MMddyyyy = "MM/dd/yyyy";
  private static final String yyyyMMdd = "yyyy-MM-dd";
  private static final int PAGE_SIZE = 100;

  public CTMResult getLastCTMResult() {
    return lastCTMResult;
  }

  public CTMServer(String url, UsernamePasswordCredentials credentials) {
    if (credentials == null) {
      throw new RuntimeException("Credentials must be provided!");
    }

    try {
      // URL foo = new URL(url);
    } catch (Exception e) {
      throw new RuntimeException("Execution Manager URL is invalid " + e.getMessage());
    }

    if (!url.endsWith("/")) {
      this.portalUrl = url + "/";
    } else {
      this.portalUrl = url;
    }

    this.credentials = credentials;
  }

  // this api call will return all the Worksoft products registered within the
  // Portal
  // what we are attempting to achieve is to identify the URL for CTM
  private boolean retrieveCTMURLConfigurationAsRequired() {
    if (!StringUtils.isEmpty(this.ctmUrl))
      return true;

    HttpRequest httpRequest = HttpRequest.get(this.portalUrl + "api/product");

    CTMResult result = sendRequest(httpRequest);

    if (result.is200()) {

      System.out
          .println("\n----------------------------\nWorksoft products registered in Portal " + result.dumpDebug());

      WorksoftProduct ctmProduct = null;

      JSONObject data = result.getJsonData();
      if (data == null)
        throw new RuntimeException("Failed to identify Worksoft products - namely CTM configuration");
      JSONArray products0 = new JSONArray();
      products0.add(data);
      if (products0 == null
          || products0.size() <= 0)
        throw new RuntimeException(
            "No Worksoft products returned from Portal registration, expected CTM configuration...");
      JSONObject products1 = products0.getJSONObject(0);
      JSONArray products = products1.getJSONArray("objects");
      if (products == null
          || products.size() <= 0)
        throw new RuntimeException(
            "No Worksoft products returned from Portal registration, expected CTM configuration");
      for (int i = 0; i < products.size(); i++) {
        JSONObject jsonProduct = products.getJSONObject(i);
        WorksoftProduct product = new WorksoftProduct(jsonProduct);
        if (!StringUtils.isEmpty(product.ProductName)
            && product.ProductName.equals("Continuous Testing Manager")) {
          ctmProduct = product;
          break;
        }
      }
      if (ctmProduct == null)
        throw new RuntimeException("Worksoft Products registered in Portal did not match CTM");
      if (StringUtils.isEmpty(ctmProduct.BaseUrl))
        throw new RuntimeException("CTM configuration contains missing url");

      this.ctmUrl = ctmProduct.BaseUrl;
      if (!this.ctmUrl.endsWith("/")) {
        this.ctmUrl += "/";
      }
    }
    return result.is200();
  }

  // this api call does not require authentication - it will return the url for
  // the authentication service
  // and nothing else.
  private boolean retrieveAuthenticationConfigurationAsRequired() {
    if (!StringUtils.isEmpty(this.authenticationUrl))
      return true;

    HttpRequest httpRequest = HttpRequest.get(this.portalUrl + "api/configuration");

    CTMResult result = sendRequest(httpRequest);

    if (result.is200()) {
      System.out.println("\n----------------------------\nportal configuration " + result.dumpDebug());

      JSONObject data = result.getJsonData();
      if (!data.containsKey("AuthenticationUrl")) {
        throw new RuntimeException("No authentication url registered within Worksoft Portal");
      }
      this.authenticationUrl = data.getString("AuthenticationUrl");
      if (!this.authenticationUrl.endsWith("/")) {
        this.authenticationUrl += "/";
      }
    }
    return result.is200();
  }

  public boolean login() throws UnsupportedEncodingException {
    boolean success = this.retrieveAuthenticationConfigurationAsRequired();
    if (!success)
      return false;

    String url = this.authenticationUrl + "connect/token";
    System.out.println("authenticate using " + url);
    HttpRequest httpRequest = HttpRequest.post(url)
        .contentType("application/x-www-form-urlencoded")
        .acceptJson()
        .header("Authorization", "OAuth2")
        .form("client_id", "ws.user",
            "grant_type", "password",
            "response_type", "id_token token",
            "username", credentials.getUsername(),
            "password", credentials.getPassword().getPlainText());

    CTMResult result = sendRequest(httpRequest);

    if (result.is200()) {
      auth = new Auth();
      auth.save(result.getJsonData());

      System.out.println("retrieve CTM URL Configuration as required");
      this.retrieveCTMURLConfigurationAsRequired();
    }
    return result.is200();
  }

  public HashSet<WorksoftTenant> Tenants() {
    return this.auth.Tenants();
  }

  public boolean authenticatedUserInfo() throws UnsupportedEncodingException {
    if (!this.retrieveAuthenticationConfigurationAsRequired())
      throw new RuntimeException("failed to retrieve configurations from Portal");

    System.out.println("\n---------------\nauthenticatedUserInfo");

    HttpRequest httpRequest = HttpRequest.get(this.authenticationUrl + "api/user/info");

    CTMResult result = sendRequest(httpRequest);

    if (result.is200()) {
      System.out.println("\nauthenticatedUserInfo 200");

      this.auth.acknowledgeUserDetails(result.getJsonData());

      System.out.println("\n------------------\nuser info " + result.dumpDebug());
    }
    return result.is200();
  }

  private Date TodayAddYear(int deltaYear) {
    Date dt = new Date();
    Calendar cal = Calendar.getInstance();
    cal.setTime(dt);
    cal.add(Calendar.YEAR, deltaYear);
    return cal.getTime();
  }

  private Date TodayAddDay(int deltaDay) {
    Date dt = new Date();
    Calendar cal = Calendar.getInstance();
    cal.setTime(dt);
    cal.add(Calendar.DATE, deltaDay);
    return cal.getTime();
  }

  private String FormattedDate(Date dt) {
    DateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
    return formatter.format(dt);
  }

  private String GetFormattedDate(Date dt, String pattern) {
    DateFormat formatter = new SimpleDateFormat(pattern);
    return formatter.format(dt);
  }

  public List<CTMSuite> suitesForAllTenants() {
    if (this.auth == null)
      throw new RuntimeException("Internal error - auth not set");
    HashSet<WorksoftTenant> tenants = this.auth.Tenants();
    if (tenants == null
        || tenants.size() <= 0)
      throw new RuntimeException("Internal error - auth.tenants not available");

    List<CTMSuite> allSuites = new ArrayList<CTMSuite>();
    for (WorksoftTenant t : tenants) {
      try {
        HashSet<CTMSuite> suitesForTenant = suites(t.TenantId, null);
        if (suitesForTenant != null
            && suitesForTenant.size() > 0) {
          for (CTMSuite s : suitesForTenant) {
            s.Tenant = t;
          }
          allSuites.addAll(suitesForTenant);
        }
      } catch (Exception ex) {
        System.out.println("\n----error retrieving suites for tenant: " + t.TenantId);
      }
    }

    List<CTMSuite> sorted = allSuites.stream().sorted(new Comparator<CTMSuite>() {
      @Override
      public int compare(CTMSuite o1, CTMSuite o2) {
        return (o1.Tenant.TenantName + o1.SuiteName).compareTo(
            o2.Tenant.TenantName + o2.SuiteName);
      }
    }).collect(Collectors.toList());

    return sorted;
  }

  public HashSet<CTMSuite> suites(String tenantId) {
    HashSet<CTMSuite> suitesForTenant = new HashSet<CTMSuite>();
    Date beginDt = this.TodayAddYear(-2);
    Date endDt = this.TodayAddYear(2);
    String begin = this.FormattedDate(beginDt);
    String end = this.FormattedDate(endDt);

    String url = this.ctmUrl +
        "api/Suite/All?tenantID=" + tenantId +
        "&StartDate=" + begin +
        "&EndDate=" + end;

    System.out.println("\n-------------------------CTM Suites\n");
    System.out.println(url);
    HttpRequest httpRequest = HttpRequest.get(url);
    httpRequest.body("");

    CTMResult result = sendRequest(httpRequest);

    if (result.is200()) {
      System.out.println(result.dumpDebug());
      JSONObject data = result.getJsonData();
      if (data == null)
        throw new RuntimeException("Failed to identify CTM Suites for tenantId: " + tenantId);
      JSONArray data0 = new JSONArray();
      data0.add(data);
      if (data0 == null
          || data0.size() <= 0)
        throw new RuntimeException("No CTM suites returned for tenantID: " + tenantId);
      JSONObject data1 = data0.getJSONObject(0);
      JSONArray suites = data1.getJSONArray("objects");
      if (suites == null
          || suites.size() <= 0)
        throw new RuntimeException("No CTM Suites returned with the tenantId: " + tenantId);
      for (int i = 0; i < suites.size(); i++) {
        JSONObject jsonSuite = suites.getJSONObject(i);
        CTMSuite s = new CTMSuite(jsonSuite);
        suitesForTenant.add(s);
      }
      return suitesForTenant;
    }

    return null;
  }

  public HashSet<CTMSuite> suites(String tenantId, String suitename) {
    HashSet<CTMSuite> suitesForTenant = new HashSet<CTMSuite>();
    Date beginDt = this.TodayAddYear(-3);
    Date endDt = this.TodayAddDay(1);
    String begin = this.GetFormattedDate(beginDt, yyyyMMdd) + "T00:00:00.000Z";
    String end = this.GetFormattedDate(endDt, yyyyMMdd) + "T23:59:59.000Z";

    suitesForTenant = getSuites(tenantId, suitename, 1, begin, end);
    if (suitesForTenant == null || suitesForTenant.size() == 0) {
      throw new RuntimeException("No CTM Suites found fot tenantId: " + tenantId);
    } else if (suitesForTenant.size() >= PAGE_SIZE) {
      CTMSuite suite = suitesForTenant.stream().findFirst().get();
      if (suite != null && this.getRecordCount(suite.RecordCount) > PAGE_SIZE) {
        int maxPageNo = this.getMaxPageNumber(suite.RecordCount);
        for (int i = 2; i <= maxPageNo; i++) {
          HashSet<CTMSuite> nextSuitesForTenant = getSuites(tenantId, suitename, i, begin, end);
          if (nextSuitesForTenant != null && nextSuitesForTenant.size() > 0) {
            suitesForTenant.addAll(nextSuitesForTenant);
          }
        }
      }
    }
    return suitesForTenant;
  }

  private HashSet<CTMSuite> getSuites(String tenantId, String suitename, int pageNo, String startDate, String endDate) {
    HashSet<CTMSuite> suitesForTenant = new HashSet<CTMSuite>();
    String url = this.ctmUrl + "api/Suite/All";
    String body = "{\"PageSize\":" + PAGE_SIZE + ",\"PageNumber\":" + pageNo + ",\"TenantId\":\"" + tenantId
        + "\",\"SuiteName\":\""
        + suitename + "\",\"StartDate\":\"" + startDate + "\",\"EndDate\":\"" + endDate + "\"}";

    if (suitename == null || suitename.trim() == "") {
      body = "{\"PageSize\":" + PAGE_SIZE + ",\"PageNumber\":" + pageNo + ",\"TenantId\":\"" + tenantId
          + "\",\"StartDate\":\""
          + startDate + "\",\"EndDate\":\"" + endDate + "\"}";
    }

    System.out.println("\n-------------------------CTM Suites\n");
    System.out.println(url);
    log.info("Get suite url: " + url + " request body:" + body);
    HttpRequest httpRequest = HttpRequest.post(url);
    httpRequest.contentType("application/json");
    httpRequest.body(body);

    CTMResult result = sendRequest(httpRequest);

    if (result != null && result.is200()) {
      System.out.println(result.dumpDebug());
      JSONObject data = result.getJsonData();
      if (data != null) {
        JSONArray data0 = new JSONArray();
        data0.add(data);
        if (data0 != null && data0.size() > 0) {
          JSONObject data1 = data0.getJSONObject(0);
          JSONArray suites = data1.getJSONArray("objects");
          if (suites != null && suites.size() > 0) {
            for (int i = 0; i < suites.size(); i++) {
              JSONObject jsonSuite = suites.getJSONObject(i);
              CTMSuite s = new CTMSuite(jsonSuite);
              suitesForTenant.add(s);
            }
          }
        }
      }
    }
    return suitesForTenant;
  }

  private int getRecordCount(String recordCountString) {
    int recordCount = 0;
    if (recordCountString != null && recordCountString != "") {
      try {
        recordCount = Integer.parseInt(recordCountString);
      } catch (Exception e) {
      }
    }
    ;
    return recordCount;
  }

  private int getMaxPageNumber(String recordCountString) {
    int pageNo = 1;
    int recordCount = this.getRecordCount(recordCountString);
    try {
      pageNo = (int) Math.ceil((double) recordCount / PAGE_SIZE);
    } catch (Exception e) {
    }
    return pageNo;
  }

  public String escapeParameter(String value) {
    // Escape the value... In short, these values are passed to Certify as
    // command line arguments with in double quotes, so we should escape user input
    // double quotes and backslashes appropriately. BTW - This is misplaced
    // logic that should be performed by the EM.
    value = value.replaceAll("\\\\", "\\\\\\\\");
    value = value.replaceAll("\"", "\\\\\"");

    return value;
  }

  public String sanitizeParameter(String value) {
    // Certify's syntax for these parameters is - <key>|<value>, so if the
    // value has a pipe (|), it'll mess up Certify's parsing. BTW - This is
    // misplaced
    // logic that should be performed by the EM.
    value = value.replaceAll("\\|", "");
    // The EM has no way of escaping curly braces, so we'll remove them or it'll
    // mess with the EM's parsing algorithm
    value = value.replaceAll("[{}]", "");

    return value;
  }

  /*
   * // Format the provided hash map into a string format acceptable to the EM API
   * private String formatParameters (Map<String, String> parameters) {
   * String params = "";
   * for (String key : parameters.keySet()) {
   * String value = parameters.get(key);
   * 
   * params += "{" + key + "}";
   * params += "{" + escapeParameter(sanitizeParameter(value)) + "}";
   * }
   * return params;
   * }
   */
  public String executeSuite(String suiteId) {

    System.out.println("\n--------------execute suite " + suiteId);

    HttpRequest httpRequest = HttpRequest.post(this.ctmUrl + "api/SuiteExecution/" + suiteId);
    httpRequest.body("");
    String guid = null;

    CTMResult result = sendRequest(httpRequest);
    System.out.println("\n------execute suite-----------\n" + result.dumpDebug());
    if (result.is200()) {
      JSONObject json = result.getJsonData();
      if (json.containsKey("SuiteExecutionResultId")) {
        guid = json.getString("SuiteExecutionResultId");
      }
    } else {
      String response = result.getResponseData();
      if (response != null
          && StringUtils.isNotEmpty(response)) {
        System.out.println(result.dumpDebug());
        if (response.contains("No machines are available for execution")) {
          throw new RuntimeException("No machines are available for execution");
        } else if (response.contains("No machine credentials are available for execution.")) {
          throw new RuntimeException("No machine credentials are available for execution.");
        } else {
          throw new RuntimeException("Execute suite failure - " + response);
        }
      }
    }

    return guid;
  }

  public String executeSuite(String suiteId, String suiteName, String tenantId, String tenantName,
      Map<String, CTMProcess> processMap, JobDetails details,Integer processIdCntr) {

    details.consoleOut.println("\n----execute suite with execution parameters SuiteId----" + suiteId);
    StringBuilder requestBody = new StringBuilder();
    String requestUrl = this.ctmUrl + "api/SuiteExecution/" + suiteId + "/runtimeparameters";

    requestBody = requestBody.append("{\"SuiteId\":\"").append(suiteId).append("\",\"SuiteName\":\"").append(suiteName)
        .append("\",\"TenantId\":\"").append(tenantId).append("\",\"TenantName\":\"").append(tenantName)
        .append("\",\"Processes\":[");

    if (processIdCntr <= 0) {
      for (CTMProcess ctm : processMap.values()) {
        requestBody = requestBody.append("\",\"ProcessPath\":\"").append(ctm.GetProcessPath())
            .append("\",\"Layout\":\"").append(ctm.GetLayout()).append("\",\"Recordset\":\"").append(ctm.GetRecordset())
            .append("\",\"RecordsetMode\":\"").append(ctm.GetRecordsetMode()).append("\",\"MachineAttributes\":\"")
            .append(ctm.GetMachineAttributes().replace(':', '=')).append("\"},");
      }
    } else {
      for (CTMProcess ctm : processMap.values()) {
        requestBody = requestBody.append("{\"ProcessId\":\"").append(ctm.GetProcessId()).append("\",\"ProcessPath\":\"")
            .append(ctm.GetProcessPath())
            .append("\",\"Layout\":\"").append(ctm.GetLayout()).append("\",\"Recordset\":\"").append(ctm.GetRecordset())
            .append("\",\"RecordsetMode\":\"").append(ctm.GetRecordsetMode()).append("\",\"MachineAttributes\":\"")
            .append(ctm.GetMachineAttributes().replace(':', '=')).append("\"},");
      }
    }
    requestBody = requestBody.deleteCharAt(requestBody.lastIndexOf(","));
    requestBody.append("]}");

    details.consoleOut.println("Request body --->" + requestBody);
    details.consoleOut.println("Get suite url: " + requestUrl);

    HttpRequest execParamsRequest = HttpRequest
        .post(this.ctmUrl + "api/SuiteExecution/" + suiteId + "/runtimeparameters");
    execParamsRequest.contentType("application/json");
    execParamsRequest.body(requestBody.toString());

    String guid = null;

    CTMResult result = sendRequest(execParamsRequest);
    details.consoleOut.println("\n------execute suite-----------\n" + result.dumpDebug());
    if (result.is200()) {
      JSONObject json = result.getJsonData();
      if (json.containsKey("SuiteExecutionResultId")) {
        guid = json.getString("SuiteExecutionResultId");
      }
    } else {
      String response = result.getResponseData();
      if (response != null
          && StringUtils.isNotEmpty(response)) {
        details.consoleOut.println(result.dumpDebug());
        if (response.contains("No machines are available for execution")) {
          throw new RuntimeException("No machines are available for execution");
        } else if (response.contains("No machine credentials are available for execution.")) {
          throw new RuntimeException("No machine credentials are available for execution.");
        } else {
          throw new RuntimeException("Execute suite failure - " + response);
        }
      }
    }

    return guid;

  }

  public CTMExecutionResult executionStatus(String guid) {

    HttpRequest httpRequest = HttpRequest.get(this.ctmUrl + "api/SuiteExecutionResult/" + guid);

    CTMResult result = sendRequest(httpRequest);
    CTMExecutionResult suiteResult = new CTMExecutionResult(null);
    suiteResult.ExecutionResultId = guid;

    if (result.is200()) {
      System.out.println("\n------execute status-----------\n" + result.dumpDebug());

      JSONObject jsonResult = result.getJsonData();
      suiteResult = new CTMExecutionResult(jsonResult);
      suiteResult.FullResponse = result.getResponseData();
    } else {
      suiteResult.ApiFailed = true;
      suiteResult.ApiFailure = result.dumpDebug();
      System.out.println("\n-------------------");
      System.out.println(suiteResult.ApiFailure);
    }

    return suiteResult;
  }

  /*
   * public CTMResult executionAbort (String guid) {
   * 
   * HttpRequest httpRequest = HttpRequest.put(this.portalUrl + "api/Execution/" +
   * guid + "/Abort")
   * .header("id", guid)
   * .header("jsonOrXml", "json");
   * 
   * httpRequest.body("");
   * 
   * CTMResult result = sendRequest(httpRequest);
   * 
   * return result;
   * }
   * 
   */

  private CTMResult sendRequest(HttpRequest request) throws HttpException {

    CTMResult result;

    try {
      if (auth != null) {
        request.tokenAuthentication(auth.getAccess_token());
      }

      HttpResponse response = request.send();
      result = lastCTMResult = new CTMResult(response);
      if (result.is200()) {
        // status = true;

      } else if (result.statusCode() == 401) {
        // status = false;
        throw new Exception("Unauthorized");
        // payload = Unauthorized;
      } else {

        log.warning("CTM/Portal/api failed " + response.toString(true));
        // status = false;
        // if (payload != null) {
        // payload = response.statusPhrase();
        // }
      }

    } catch (Throwable t) {
      result = new CTMResult(null);
      log.severe("ERROR: unexpected error while processing request: " + request);
      log.severe("ERROR: exception: " + t);
      log.severe("ERROR: exception: " + t.getMessage());
      log.severe("ERROR: stack trace:  ");
      StackTraceUtils.printSanitizedStackTrace(t.getCause());
      throw new HttpException(t);
    }

    return result;
  }
}
