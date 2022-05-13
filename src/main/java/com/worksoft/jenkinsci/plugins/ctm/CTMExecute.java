/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * CTMExecute
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm;

import com.worksoft.jenkinsci.plugins.ctm.config.CTMConfig;
import com.worksoft.jenkinsci.plugins.ctm.model.*;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CTMExecute extends Builder implements SimpleBuildStep {
  private static final Logger log = Logger.getLogger("jenkins.wsCTMServer.Execute");

  public class ConsoleStream extends PrintStream {
    public ConsoleStream (OutputStream out) {
      super(out);
    }

    @Override
    public void println (String string) {
      Date now = new Date();
      DateFormat dateFormatter = DateFormat.getDateTimeInstance(
              DateFormat.SHORT,
              DateFormat.MEDIUM,
              Locale.getDefault());
      Scanner scanner = new Scanner(string);
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        super.println("[" + (dateFormatter.format(now)) + "] " + line);
      }
      scanner.close();
    }

    public void printlnIndented (String indent, String string) {
      Scanner scanner = new Scanner(string);
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        println(indent + line);
      }
      scanner.close();
    }

    public void printlnIndented (String indent, Object[] objects) {
      for (Object obj : objects) {
        printlnIndented(indent, obj.toString());
      }
    }
  }

  // The following instance variables are those provided by the GUI
  private String requestType;
  private ExecuteSuite request;
  //private ExecuteTenant executeTenant;
  private ExecuteRequestPostExecute postExecute;
  private ExecuteRequestCTMConfig altCTMConfig;
  private ExecuteWaitConfig waitConfig;
  private ExecuteRequestParameters execParams;

  // These instance variables are those used during execution
  private ExecuteRequestCTMConfig ctmConfig; // CTM config used during run

  private CTMServer server;
  private Run<?, ?> run;
  private FilePath workspace;
  private Launcher launcher;
  private TaskListener listener;
  private ConsoleStream consoleOut; // Console output stream

  @DataBoundConstructor
  public CTMExecute (String requestType) {
    this.requestType = requestType;

    // When we get here Jenkins is saving our form values, so we can invalidate
    // this session's itemsCache.
    CTMItemCache.invalidateItemsCache();
    //TenantCache.invalidateTenantsCache();
  }

  public boolean getExecParamsEnabled () {
    return getExecParams() != null;
  }

  public ExecuteRequestParameters getExecParams () {
    return execParams;
  }

  public boolean getPostExecuteEnabled () {
    return getPostExecute() != null;
  }

  public ExecuteRequestPostExecute getPostExecute () {
    return postExecute;
  }

  public boolean getWaitConfigEnabled () {
    return getWaitConfig() != null;
  }

  public ExecuteWaitConfig getWaitConfig () {
    return waitConfig;
  }

  public boolean getAltEMConfigEnabled () {
    return getAltCTMConfig() != null;
  }

  public ExecuteRequestCTMConfig getAltCTMConfig () {
    return altCTMConfig;
  }

  public String getRequestType () {
    if(requestType == null
            || StringUtils.isEmpty(requestType)) {
      // When we get here Jenkins is loading our form values, so we can invalidate
      // this session's itemsCache.
      CTMItemCache.invalidateItemsCache();
    }

    return requestType;
  }

  public ExecuteSuite getRequest () {
    return request;
  }
  //public ExecuteTenant getExecuteTenant() {
    //return executeTenant;
  //}
  @DataBoundSetter
  public void setRequestType (@Nonnull String requestType) {
    System.out.println("\n----------- databound setter - requestType: " + requestType);
    this.requestType = requestType;
  }
  @DataBoundSetter
  public void setRequest (ExecuteSuite request) {
    System.out.println("\n----------- databound setter - suite: " + request.getName());
    this.request = request;
  }
/*  @DataBoundSetter
  public void setExecuteTenant (ExecuteTenant executeTenant) {
    System.out.println("\n----------- databound setter - executeTenant: " + executeTenant.getName());
    this.executeTenant = executeTenant;
  }*/
  @DataBoundSetter
  public void setPostExecute (ExecuteRequestPostExecute postExecute) {
    this.postExecute = postExecute;
  }

  @DataBoundSetter
  public void setAltCTMConfig (ExecuteRequestCTMConfig altCTMConfig) {
    this.altCTMConfig = altCTMConfig;
  }

  @DataBoundSetter
  public void setWaitConfig (ExecuteWaitConfig waitConfig) {
    this.waitConfig = waitConfig;
  }

  @DataBoundSetter
  public void setExecParams (ExecuteRequestParameters execParams) {

    try {
      this.execParams = execParams;
    } catch (Exception e) {
      e.printStackTrace();
      log.severe("Unable to set exec parameters " + e);
    }
  }

  // Call from the jelly to determine whether radio block is checked
  public String emRequestTypeEquals (String given) {
    return String.valueOf((requestType != null) && (requestType.equals(given)));
  }
/*  public boolean tenantSet() {
    boolean result =  executeTenant != null
            && !StringUtils.isEmpty(executeTenant.name);
    System.out.println("tenantSet ----- " + result);
    return result;
  }*/

  @Symbol("execMan")
  @Extension
  public static final class ExecutionManagerBuilderDescriptor extends BuildStepDescriptor<Builder> {

    @Override
    public boolean isApplicable (Class<? extends AbstractProject> jobType) {
      return true;
    }

    @Override
    @Nonnull
    public String getDisplayName () {
      return "Run Continuous Testing Manager Suite";
    }
  }

  private static ExecuteRequestCTMConfig Configuration(String portalUrl, String credentials) {
    CTMConfig globalConfig = GlobalConfiguration.all().get(CTMConfig.class);
    ExecuteRequestCTMConfig emConfig = globalConfig != null ? globalConfig.getCTMConfig() : null;
    ExecuteRequestCTMConfig altCTMConfig = new ExecuteRequestCTMConfig(portalUrl, credentials);
    if (altCTMConfig != null && altCTMConfig.isValid()) {
      emConfig = altCTMConfig;
    }
    return emConfig;
  }
  private static ConfigureAndAuth AuthenticateOnly(ExecuteRequestCTMConfig ctmConfig) {
    ConfigureAndAuth result = new ConfigureAndAuth();
    if (ctmConfig != null) {
      CTMServer server = new CTMServer(ctmConfig.getUrl(), ctmConfig.lookupCredentials());
      result.Server = server;
      try {
        if (server.login()) {
          System.out.println("tenantsForAuthenticatedUser - logged in");
          HashSet<WorksoftTenant> tenants = null;

          if (server.authenticatedUserInfo()) {
            System.out.println("authenticatedUserInfo - invoked");
            tenants = server.Tenants();
          }
          if (tenants != null) {
            result.Tenants = tenants;
          }
          else {
            result.Error = true;
            result.DisplayErrorMessage = "*** ERROR with tenants ***";
            result.ErrorMessage = "ERROR: Couldn't retrieve Tenants from authenticated user";
          }
        } else {
          // couldn't login
          result.Error = true;
          result.DisplayErrorMessage = "*** ERROR during authentication ***";
          result.ErrorMessage = "ERROR: Couldn't log in";
        }
      } catch (Exception ex) {
        System.out.println("exception: " + ex.getMessage());
        System.out.println("exception: " + ex.getStackTrace());
        result.Error = true;
        result.DisplayErrorMessage = "*** ERROR  *** " + ex.getMessage();
        result.ErrorMessage = "ERROR: Exception while logging in";
      }
    }
    else {
      // No CTM configuration
      result.DisplayErrorMessage = "*** ERROR No CTM Configuration ***";
      result.ErrorMessage = "ERROR: No CTM configuration";
      result.Error = true;
    }
    return result;
  }
  private static ConfigureAndAuth Authenticate(String portalUrl, String credentials) {

    ExecuteRequestCTMConfig ctmConfig = Configuration(portalUrl, credentials);
    return AuthenticateOnly(ctmConfig);

  }
/*  public static ListBoxModel tenantsForAuthenticatedUser(String portalUrl, String credentials) {
    ListBoxModel items = new ListBoxModel();

    ConfigureAndAuth authResult = Authenticate(portalUrl, credentials);
    if(!authResult.Error) {
      try {
        items.add("-- Select a tenant --");

        // Lookup all the Suites defined on the CTM and find the one specified
        // by the user

        for(WorksoftTenant tenant : authResult.Tenants) {
          String name = tenant.TenantName;
          items.add(name);
        }
      } catch (Exception ignored) {
        // Bad JSON
        items.add("*** ERROR with tenants ***", "ERROR: (tenants) Bad JSON");
        items.get(items.size() - 1).selected = true;
      }
    }
    else {
      items.add(authResult.DisplayErrorMessage, authResult.ErrorMessage);
      items.get(items.size() - 1).selected = true;
    }

    CTMItemCache.updateItemsCache("executeTenant", items);

    return items;
  }*/

  // Used by doFillRequestItems
  public static ListBoxModel fillItems (String emRequestType, String executeTenant, String portalUrl, String credentials) {
    ListBoxModel items = new ListBoxModel();

    System.out.println("\n----------------------------------\nfillItems--------" + executeTenant);
/*
    if(emRequestType == null
      || !emRequestType.equals("request")) {
      items.add("*** Waiting for user to specify type ***", "ERROR: Waiting for user to specify type");
      items.get(items.size() - 1).selected = true;
      return items;
    }
    if(executeTenant == null
      || StringUtils.isEmpty(executeTenant)) {
      items.add("*** Waiting for Tenant to be specified ***", "ERROR: Waiting for Tenant to be specified");
      items.get(items.size() - 1).selected = true;
      return items;
    }
*/
    System.out.println("\n--------------------fillItems ----------------------------\n");

    ConfigureAndAuth authResult = Authenticate(portalUrl, credentials);
    if(!authResult.Error) {
      try {
        //String tenantId = authResult.MatchingTenantId(executeTenant);
        //String tenantId = authResult.FirstTenant().TenantId;
        //HashSet<CTMSuite> suitesForTenant = authResult.Server.suites(tenantId);

        List<CTMSuite> suites = authResult.Server.suitesForAllTenants();
        if (suites != null) {
          try {
            items.add("-- Select a CTM Suite --"); // Add blank entry first

            // Lookup all the Suites defined on the CTM and find the one specified
            // by the user
            for(CTMSuite suite : suites) {
              String name = suite.Tenant.TenantName + " / " + suite.SuiteName;
              items.add(name, name);
            }
          } catch (Exception ignored) {
            // Bad JSON
            items.add("*** ERROR ***", "ERROR: Bad JSON");
            items.get(items.size() - 1).selected = true;
          }
        } else {
          // couldn't get requests
          items.add("*** ERROR ***", "ERROR: Couldn't retrieve Suite(s) from CTM (or none for tenant)");
          items.get(items.size() - 1).selected = true;
        }

      }
      catch(Exception ex) {
        System.out.println("Error retrieving list of suites - " + ex.getMessage());
        items.add("*** ERROR ***", "ERROR: Couldn't retrieve Suite(s) from CTM " + ex.getMessage());
        items.get(items.size() - 1).selected = true;
      }
    }
    else {
      items.add(authResult.DisplayErrorMessage, authResult.ErrorMessage);
      items.get(items.size() - 1).selected = true;
    }

    CTMItemCache.updateItemsCache("request", items);

    return items;
  }

  // Process the user provided parameters by substituting Jenkins environment
  // variables referenced in a parameter's value.
  private HashMap<String, String> processParameters () throws InterruptedException, IOException {
    HashMap<String, String> ret = new HashMap<String, String>();
    EnvVars envVars = run.getEnvironment(listener);
    if (execParams != null && execParams.getList() != null) {
      for (ExecuteRequestParameter param : execParams.getList()) {
        String value = param.getValue();

        if (StringUtils.isNotEmpty(param.getKey()) &&
                StringUtils.isNotEmpty(value)) {

          // Dereference/expand ALL Jenkins vars within the value string
          Matcher m = Pattern.compile("([^$]*)[$][{]([^}]*)[}]([^$]*)").matcher(value);
          StringBuilder expandedValue = new StringBuilder();
          boolean found = false;
          while (m.find()) {
            found = true;
            for (int i = 1; i <= m.groupCount(); i++) {
              if (i == 2) {
                String envVar = envVars.get(m.group(i));
                if (envVar != null) {
                  expandedValue.append(envVar);
                }
              } else {
                expandedValue.append(m.group(i));
              }
            }
          }
          if (!found) {
            expandedValue = new StringBuilder(value);
          }
          ret.put(param.getKey(), expandedValue.toString());
        }
      }
    }
    return ret;
  }

  private void reportVerboseProcessInformation(ProcessAutomatedExecutionModel[] processes) {
    if (processes == null
            || processes.length == 0) return;

    try {
      // Print the run's status to the build console
      consoleOut.println("Name  Status                     Log Header ID      Resource                            Last Error");
      consoleOut.println("----- -------------------------- ------------------ ----------------------------------- -----------------------------------");
      for (int i = 0; i < processes.length; i++) {
        ProcessAutomatedExecutionModel p = processes[i];

        String name = p.CertifyProcessName;
        String executionStatus = p.CertifyResult;
        String resourceName = p.MachineId;
        String lastReportedError = p.ErrorMessage;
        String logHeaderID = p.LogHeaderId;

        consoleOut.println(name + ":");
        consoleOut.println(String.format("      %-26.26s %-18s %35s %s",
                StringUtils.abbreviate(executionStatus, 26),
                StringUtils.abbreviate(logHeaderID, 18),
                StringUtils.abbreviate(resourceName, 35),
                lastReportedError));
      }
    }
    catch(Exception ex) {
      System.out.println(ex.getMessage());
      System.out.println(ex.getStackTrace());
    }
  }
  private boolean MarkBuildStatusPassed(CTMExecutionResult lastResultInfo) {
    boolean passed = false;
    if(lastResultInfo.ErrorMessage != null
       && StringUtils.isNotEmpty(lastResultInfo.ErrorMessage)) {
      passed = false;
      consoleOut.println("Automation Error acknowledged, marking Jenkins build as failed - " + lastResultInfo.ErrorMessage);
    } else if(StringUtils.isNotEmpty(lastResultInfo.Result)) {
      String result = lastResultInfo.Result.toUpperCase();
      if(result.contains("PASS")
        || result.contains("SUCCESS")) {
        passed = true;
      }
    }
    else {
      passed = false; // inconclusive
      consoleOut.println("Not enough information in CTM response to indicate result information, marking Jenkins build as failed");
    }
    return passed;
  }
  private void OnExecutionComplete(CTMExecutionResult lastResultInfo, boolean aborted, String automationGuid) {
    System.out.println("\nOnExecutionComplete - " + automationGuid);
    if(aborted) {
      // Tell the CTM to abort execution
      // TODO: implement this feature
      //CTMResult result = server.executionAbort(guid);
      //if (!result.is200() && !aborted) {
      //        consoleOut.println("\n*** ERROR: Error aborting execution:");
      //        consoleOut.printlnIndented("*** ERROR:   ", result.dumpDebug());
      //      }
    }

    if(lastResultInfo != null) {
      this.reportVerboseProcessInformation(lastResultInfo.CompletedExecutions);
      if(!aborted) {
        if(this.MarkBuildStatusPassed(lastResultInfo)) {
          run.setResult(Result.SUCCESS);
        } else {
          run.setResult(Result.FAILURE);
        }
      }
    } else {
        run.setResult(Result.FAILURE);
    }

    // Write the response JSON to a file so that it can be processed further by the Jenkins job
    try {
      FilePath resFile = new FilePath(workspace.getChannel(), workspace + "/execMan-result.json");
      //File resFile = new File(workspace + "/execMan-result.json");
      if (lastResultInfo != null
        && StringUtils.isNotEmpty(lastResultInfo.FullResponse)) {
        resFile.write(lastResultInfo.FullResponse, null);
        //FileUtils.writeStringToFile(resFile, response.toString());
        consoleOut.println("\nResults written to " + resFile);
      }
    } catch (Exception e) {
      consoleOut.println("\n*** ERROR: unexpected error while writing results");
      consoleOut.println("*** ERROR: exception: " + e);
      consoleOut.println("*** ERROR: exception: " + e.getMessage());
      consoleOut.println("*** ERROR: stack trace:  ");
      consoleOut.printlnIndented("*** ERROR:    ", e.getStackTrace());
    }


  }
  private void waitForCompletion (String guid, ConfigureAndAuth authResult) {
    boolean aborted = false;
    String abortReason = "";

    // Setup timing variables
    Long maxRunTime = waitConfig == null ? null : waitConfig.maxRunTimeInMillis();
    if (maxRunTime == null) {
      // Default to 1 year maximum run time
      maxRunTime = TimeUnit.MILLISECONDS.convert(365L, TimeUnit.DAYS);
    }
    Long pollInterval = waitConfig == null ? null : waitConfig.pollIntervalInMillis();
    if (pollInterval == null) {
      // Default to 15 second poll interval
      pollInterval = TimeUnit.MILLISECONDS.convert(15L, TimeUnit.SECONDS);
    }

    // Stuff for computing elapsed time
    long startTime = System.currentTimeMillis();
    long currentTime = startTime;
    long endTime = (startTime + maxRunTime);
    SimpleDateFormat elapsedFmt = new SimpleDateFormat("HH:mm:ss.SSS");
    elapsedFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
    String elapsedTime = elapsedFmt.format(new Date(currentTime - startTime));

    // loop until complete/aborted
    consoleOut.println("Waiting for execution to complete...");
    CTMExecutionResult lastResultInfo = null;
    int qtyApiFailures = 5;
    while (true) {
      try {
        Thread.sleep(pollInterval);
        currentTime = System.currentTimeMillis();
        elapsedTime = elapsedFmt.format(new Date(currentTime - startTime));
        if (maxRunTime != null && currentTime >= endTime) {
          if (aborted) {
            // We get here when it's taken too long for the EM to abort execution, so
            // we're abandoning our wait.
            consoleOut.println("\n*** ERROR: Abort timed out!!! - abandoning...");
            abortReason += " (abandoned!)";
          } else {
            consoleOut.println("\n*** ERROR: Execution timed out after " + elapsedTime + " - aborting...");

            aborted = true;
            abortReason = " due to max wait time exceeded";
            run.setResult(Result.ABORTED);
          }
          break;
        }

        CTMExecutionResult statusResult = authResult.Server.executionStatus(guid);
        if (statusResult == null
                || statusResult.ApiFailed) {
          qtyApiFailures--;
          if (qtyApiFailures < 0) {
            consoleOut.println("\n*** ERROR: CTM error while checking execution status:");
            consoleOut.printlnIndented("*** ERROR:   ", statusResult.ApiFailure);
            break;
          }
        } else {
          lastResultInfo = statusResult;
          consoleOut.println("\nElapsed time=" + elapsedTime + " - " +
                  statusResult.MetricProgressInfo()
                  + (aborted ? " *** ABORTING ***" : ""));
          if (!statusResult.StillExecuting()) {
            break;
          }
        }
      } catch (InterruptedException e) {
        if (!aborted) {
          consoleOut.println("\n*** ERROR: User requested abort of execution after " + elapsedTime);

          aborted = true;
          abortReason = " due to user request";

          run.setResult(Result.ABORTED);
          break;
        } else {
          // We'll get here if the user tries to abort an aborting execution, so flag it
          // as such and abandon our wait.
          consoleOut.println("\n*** ERROR: User requested abort of execution (again) after " + elapsedTime);
          aborted = true;
          abortReason += " (forced!)";
          break;
        }
      } catch (Exception ex) {
        consoleOut.println("\n*** ERROR: CTM error :");
        consoleOut.printlnIndented("*** ERROR:   ", ex.getMessage());
        break;
      }
    }
    if(lastResultInfo == null
      && guid != null
      && StringUtils.isNotEmpty(guid)) {
      lastResultInfo = authResult.Server.executionStatus(guid);
    }
    this.OnExecutionComplete(lastResultInfo, aborted, guid);

    consoleOut.println("\n\nExecution " + run.getResult().toString() + " after - " + elapsedTime + abortReason);
  }

  // This method is called by Jenkins to perform the build step. It sets up some instance
  // variables, logs in to the EM and dispatches the execute to methods that follow
  // using reflection.
  @Override
  public void perform (@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
    // Save perform parameters in instance variables for future reference.
    this.run = run;
    this.workspace = workspace;
    this.launcher = launcher;
    this.listener = listener;
    this.consoleOut = new ConsoleStream(listener.getLogger());

    // Delete the result file
    FileUtils.deleteQuietly(new File(workspace + "/execMan-result.json"));

    CTMConfig globalConfig = GlobalConfiguration.all().get(CTMConfig.class);

    // Pick the right EM configuration
    ctmConfig = globalConfig != null ? globalConfig.getCTMConfig() : null;
    if (altCTMConfig != null && altCTMConfig.isValid()) {
      ctmConfig = getAltCTMConfig();
    }

    ConfigureAndAuth authResult = null;
    String guid = null;

    try {
      if (ctmConfig != null && ctmConfig.isValid()) {
        authResult = AuthenticateOnly(ctmConfig);
        if(authResult.Error) throw new RuntimeException(authResult.ErrorMessage);

        System.out.println("\n---------------- begin execute suite--------\n");
        guid = this.execute_REQUEST(authResult);
/*        } else {
          CTMResult result = server.getLastCTMResult();
          consoleOut.println("\n*** ERROR: Can't log in to '" + ctmConfig.getUrl() + "':");
          consoleOut.printlnIndented("*** ERROR:   ", result.getResponseData());
          run.setResult(Result.FAILURE); // Fail this build step.
        }*/
      }
      else {
        throw new RuntimeException("No CTM configuration within Jenkins");
      }
    } catch (Exception ex) {
      consoleOut.println("\n*** ERROR: Unexpected error while processing request type: " + requestType);
      consoleOut.println("*** ERROR: exception: " + ex);
      consoleOut.println("*** ERROR: exception: " + ex.getMessage());
      consoleOut.println("*** ERROR: stack trace:  ");
      consoleOut.printlnIndented("*** ERROR:   ", ex.getStackTrace());

      run.setResult(Result.FAILURE); // Fail this build step.
    }
    if (run.getResult() != Result.FAILURE) {
      if (guid == null
         || StringUtils.isEmpty(guid)) {
        consoleOut.println("\n*** ERROR: An unexpected error occurred while requesting execution!");
        run.setResult(Result.FAILURE); // Fail this build step.
      } else {
        waitForCompletion(guid, authResult);
      }
    }
  }

  // Called via reflection from the dispatcher above to execute a 'request'
  private WorksoftTenant FirstTenantFromServer(ConfigureAndAuth authResult) {
    WorksoftTenant firstTenant = null;
    HashSet<WorksoftTenant> allTenants = authResult.Server.Tenants();
    if(allTenants == null
      || allTenants.size() <= 0) throw new RuntimeException("No tenants in cache to identify first");
      for (WorksoftTenant t : allTenants) {
        firstTenant = t;
        break;
      }
      return firstTenant;
  }
  private WorksoftTenant MatchingTenantFromServer(ConfigureAndAuth authResult, String tenantName) {
    WorksoftTenant match = null;
    HashSet<WorksoftTenant> allTenants = authResult.Server.Tenants();
    if(allTenants == null
            || allTenants.size() <= 0) throw new RuntimeException("No tenants in cache to identify first");
    for (WorksoftTenant t : allTenants) {
      if(t.TenantName.equals(tenantName)) {
        match = t;
        break;
      }
    }
    return match;
  }
  private String MatchingSuiteIdentifier(String suiteName, String tenantId, ConfigureAndAuth authResult) {
    String suiteId = "";
    boolean found = false;

    HashSet<CTMSuite> suitesForTenant = authResult.Server.suites(tenantId);
    if(suitesForTenant == null) throw new RuntimeException("No response for suites for tenant: " + tenantId);
    if(suitesForTenant.size() <= 0) throw new RuntimeException("No suites for tenant: " + tenantId);
    for(CTMSuite suite : suitesForTenant) {
      if(suite.SuiteName.equals(suiteName)) {
        suiteId = suite.SuiteId;
        found = true;
        break;
      }
    }
    if(!found) throw new RuntimeException("No Suite Named '" + suiteName + "' found for tenant, it may have been renamed.");

    return suiteId;
  }
  private TandS TenantAndSuite(String tenantAndSuiteName) {
    if(!tenantAndSuiteName.contains("/")) throw new RuntimeException("Expected delimiter of '/' for tenant and suite");
    int i = tenantAndSuiteName.indexOf("/");
    TandS result = new TandS();
    result.TenantName = tenantAndSuiteName.substring(0, i).trim();
    result.SuiteName = tenantAndSuiteName.substring(i + 1).trim();
    return result;
  }
  public String execute_REQUEST (ConfigureAndAuth authResult) throws InterruptedException, IOException {
    System.out.println("\n-------------------execute_request\n");
    String guid = null;
    /*
    if(StringUtils.isEmpty(executeTenant.getName())) {
      consoleOut.println("\n*** ERROR: A tenant name or ID must be specified!");
      run.setResult(Result.FAILURE); // Fail this build step.
    }*/
    if (StringUtils.isEmpty(request.getName())) {
      consoleOut.println("\n*** ERROR: A CTM suite name or ID must be specified!");
      run.setResult(Result.FAILURE); // Fail this build step.
    } else {
      //String tenantName = executeTenant.getName().trim();
      //String suiteName = request.getName().trim();

      TandS tenantAndSuitePair = TenantAndSuite(request.getName().trim());
      String tenantName = tenantAndSuitePair.TenantName;
      String suiteName = tenantAndSuitePair.SuiteName;

      WorksoftTenant tenant = null;
      String tenantId = "";
      String suiteId = "";

      consoleOut.println("\n    Tenant: " + tenantName);
      consoleOut.println("\n    Suite: " + suiteName);
      try {
        tenant = MatchingTenantFromServer(authResult, tenantName);
        //firstTenant = FirstTenantFromServer(authResult);
        //if(firstTenant == null) throw new RuntimeException("No tenants");
        //tenantId = firstTenant.TenantId;
        if(tenant == null) throw new RuntimeException("Unable to find matching tenant: " + tenantName);
        tenantId = tenant.TenantId;

        consoleOut.println("\n   TenantId: " + tenantId);

        suiteId = MatchingSuiteIdentifier(suiteName, tenantId, authResult);

        consoleOut.println("Invoking execution of CTM Suite '" + suiteName + "'(id=" + suiteId + ")");
        consoleOut.println("   on Continuous Testing Manager @ " + ctmConfig.getUrl());
        consoleOut.println("\n");

        guid = authResult.Server.executeSuite(suiteId);
        if(guid == null
          || StringUtils.isEmpty(guid)) throw new RuntimeException("No execution identifier returned, there was a failure in CTM");

        /*if (guid == null) {
          CTMResult result = server.getLastCTMResult();
          String err = result.dumpDebug();
          if (result.getJsonData() != null) {
            try {
              err = result.getJsonData().getString("Message");
            } catch (Exception ignored) {
            }
          }
          consoleOut.println("\n*** ERROR: Request to execute '" + theReq + "' failed:");
          consoleOut.printlnIndented("   ", err);
        } */

        consoleOut.println("\n    CTM Execution Result Identifier: " + guid);
      }
      catch(Exception ex) {
        consoleOut.println("\n*** ERROR: (during execute suite) " + ex.getMessage());
        run.setResult(Result.FAILURE); // Fail this build step.
      }
    }
    return guid;
  }

  }
