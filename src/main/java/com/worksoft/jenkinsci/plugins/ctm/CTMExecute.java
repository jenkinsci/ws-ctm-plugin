/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * CTMExecute
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm;

import com.thoughtworks.xstream.mapper.Mapper.Null;
import com.worksoft.jenkinsci.plugins.ctm.CTMExecute.ConsoleStream;
import com.worksoft.jenkinsci.plugins.ctm.CTMExecute.JobDetails;
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
import jnr.ffi.StructLayout.int16_t;

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
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.codehaus.groovy.tools.shell.commands.SetCommand;
import java.util.logging.Logger;
import java.util.logging.Level;

public class CTMExecute extends Builder implements SimpleBuildStep {
  private static final Logger log = Logger.getLogger("jenkins.wsCTMServer.Execute");

  public class ConsoleStream extends PrintStream {
    public ConsoleStream(OutputStream out) {
      super(out);
    }

    @Override
    public void println(String string) {
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

    public void printlnIndented(String indent, String string) {
      Scanner scanner = new Scanner(string);
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        println(indent + line);
      }
      scanner.close();
    }

    public void printlnIndented(String indent, Object[] objects) {
      for (Object obj : objects) {
        printlnIndented(indent, obj.toString());
      }
    }
  }

  public class JobDetails {
    public Run<?, ?> run;
    public FilePath workspace;
    public Launcher launcher;
    public TaskListener listener;
    public ConsoleStream consoleOut; // Console output stream

    public JobDetails(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener,
        ConsoleStream consoleOut) {
      this.run = run;
      this.workspace = workspace;
      this.launcher = launcher;
      this.listener = listener;
      this.consoleOut = consoleOut;
    }
  }

  // The following instance variables are those provided by the GUI
  private String requestType;
  private ExecuteSuite request;
  // private ExecuteTenant executeTenant;
  private ExecuteRequestPostExecute postExecute;
  private ExecuteRequestCTMConfig altCTMConfig;
  private ExecuteWaitConfig waitConfig;
  private ExecuteRequestParameters execParams;
  private ExecuteRequestParameter execParam;

  // These instance variables are those used during execution
  private ExecuteRequestCTMConfig ctmConfig; // CTM config used during run

  // private CTMServer server;
  // private Run<?, ?> run;
  // private FilePath workspace;
  // private Launcher launcher;
  // private TaskListener listener;
  // private ConsoleStream consoleOut; // Console output stream

  @DataBoundConstructor
  public CTMExecute(String requestType) {
    this.requestType = requestType;

    // When we get here Jenkins is saving our form values, so we can invalidate
    // this session's itemsCache.
    CTMItemCache.invalidateItemsCache();
    // TenantCache.invalidateTenantsCache();
  }

  public boolean getExecParameterEnabled() {
    return getExecParameter() != null;
  }

  public ExecuteRequestParameter getExecParameter() {
    return execParam;
  }

  public boolean getExecParamsEnabled() {
    return getExecParams() != null;
  }

  public ExecuteRequestParameters getExecParams() {
    return execParams;
  }

  public boolean getPostExecuteEnabled() {
    return getPostExecute() != null;
  }

  public ExecuteRequestPostExecute getPostExecute() {
    return postExecute;
  }

  public boolean getWaitConfigEnabled() {
    return getWaitConfig() != null;
  }

  public ExecuteWaitConfig getWaitConfig() {
    return waitConfig;
  }

  public boolean getAltEMConfigEnabled() {
    return getAltCTMConfig() != null;
  }

  public ExecuteRequestCTMConfig getAltCTMConfig() {
    return altCTMConfig;
  }

  public String getRequestType() {
    if (requestType == null
        || StringUtils.isEmpty(requestType)) {
      // When we get here Jenkins is loading our form values, so we can invalidate
      // this session's itemsCache.
      CTMItemCache.invalidateItemsCache();
    }

    return requestType;
  }

  public ExecuteSuite getRequest() {
    return request;
  }

  // public ExecuteTenant getExecuteTenant() {
  // return executeTenant;
  // }
  @DataBoundSetter
  public void setRequestType(@Nonnull String requestType) {
    System.out.println("\n----------- databound setter - requestType: " + requestType);
    this.requestType = requestType;
  }

  @DataBoundSetter
  public void setRequest(ExecuteSuite request) {
    System.out.println("\n----------- databound setter - suite: " + request.getName());
    this.request = request;
  }

  /*
   * @DataBoundSetter
   * public void setExecuteTenant (ExecuteTenant executeTenant) {
   * System.out.println("\n----------- databound setter - executeTenant: " +
   * executeTenant.getName());
   * this.executeTenant = executeTenant;
   * }
   */
  @DataBoundSetter
  public void setPostExecute(ExecuteRequestPostExecute postExecute) {
    this.postExecute = postExecute;
  }

  @DataBoundSetter
  public void setAltCTMConfig(ExecuteRequestCTMConfig altCTMConfig) {
    this.altCTMConfig = altCTMConfig;
  }

  @DataBoundSetter
  public void setWaitConfig(ExecuteWaitConfig waitConfig) {
    this.waitConfig = waitConfig;
  }

  @DataBoundSetter
  public void setExecParams(ExecuteRequestParameters execParams) {

    try {
      this.execParams = execParams;
    } catch (Exception e) {
      e.printStackTrace();
      log.severe("Unable to set exec parameters " + e);
      log.log(Level.WARNING, "ERROR-Unable to set exec parameters " + e, "");
    }
  }

  // Call from the jelly to determine whether radio block is checked
  public String emRequestTypeEquals(String given) {
    return String.valueOf((requestType != null) && (requestType.equals(given)));
  }
  /*
   * public boolean tenantSet() {
   * boolean result = executeTenant != null
   * && !StringUtils.isEmpty(executeTenant.name);
   * System.out.println("tenantSet ----- " + result);
   * return result;
   * }
   */

  @Symbol("execMan")
  @Extension
  public static final class ExecutionManagerBuilderDescriptor extends BuildStepDescriptor<Builder> {

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    @Override
    @Nonnull
    public String getDisplayName() {
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
          log.log(Level.WARNING, "tenantsForAuthenticatedUser - logged in", "");
          HashSet<WorksoftTenant> tenants = null;

          if (server.authenticatedUserInfo()) {
            log.log(Level.WARNING, "authenticatedUserInfo - invoked", "");
            tenants = server.Tenants();
          }
          if (tenants != null) {
            result.Tenants = tenants;
          } else {
            result.Error = true;
            result.DisplayErrorMessage = "*** ERROR with tenants ***";
            result.ErrorMessage = "ERROR: Couldn't retrieve Tenants from authenticated user";
            log.log(Level.WARNING, result.ErrorMessage, "");
          }
        } else {
          // couldn't login
          result.Error = true;
          result.DisplayErrorMessage = "*** ERROR during authentication ***";
          log.log(Level.WARNING, result.DisplayErrorMessage, "");
          result.ErrorMessage = "ERROR: Couldn't log in";
          log.log(Level.WARNING, result.ErrorMessage, "");
        }
      } catch (Exception ex) {
        log.log(Level.WARNING, "exception: " + ex.getMessage(), "");
        log.log(Level.WARNING, "exception: " + ex.getStackTrace(), "");
        result.Error = true;
        result.DisplayErrorMessage = "*** ERROR  *** " + ex.getMessage();
        result.ErrorMessage = "ERROR: Exception while logging in";
        log.log(Level.WARNING, result.ErrorMessage, "");
      }
    } else {
      // No CTM configuration
      result.DisplayErrorMessage = "*** ERROR No CTM Configuration ***";
      result.ErrorMessage = "ERROR: No CTM configuration";
      result.Error = true;
      log.log(Level.WARNING, result.ErrorMessage, "");
    }
    return result;
  }

  private static ConfigureAndAuth Authenticate(String portalUrl, String credentials) {

    ExecuteRequestCTMConfig ctmConfig = Configuration(portalUrl, credentials);
    return AuthenticateOnly(ctmConfig);

  }
  /*
   * public static ListBoxModel tenantsForAuthenticatedUser(String portalUrl,
   * String credentials) {
   * ListBoxModel items = new ListBoxModel();
   * 
   * ConfigureAndAuth authResult = Authenticate(portalUrl, credentials);
   * if(!authResult.Error) {
   * try {
   * items.add("-- Select a tenant --");
   * 
   * // Lookup all the Suites defined on the CTM and find the one specified
   * // by the user
   * 
   * for(WorksoftTenant tenant : authResult.Tenants) {
   * String name = tenant.TenantName;
   * items.add(name);
   * }
   * } catch (Exception ignored) {
   * // Bad JSON
   * items.add("*** ERROR with tenants ***", "ERROR: (tenants) Bad JSON");
   * items.get(items.size() - 1).selected = true;
   * }
   * }
   * else {
   * items.add(authResult.DisplayErrorMessage, authResult.ErrorMessage);
   * items.get(items.size() - 1).selected = true;
   * }
   * 
   * CTMItemCache.updateItemsCache("executeTenant", items);
   * 
   * return items;
   * }
   */

  // Used by doFillRequestItems
  public static ListBoxModel fillItems(String emRequestType, String executeTenant, String portalUrl,
      String credentials) {
    ListBoxModel items = new ListBoxModel();

    System.out.println("\n----------------------------------\nfillItems--------" + executeTenant);
    /*
     * if(emRequestType == null
     * || !emRequestType.equals("request")) {
     * items.add("*** Waiting for user to specify type ***",
     * "ERROR: Waiting for user to specify type");
     * items.get(items.size() - 1).selected = true;
     * return items;
     * }
     * if(executeTenant == null
     * || StringUtils.isEmpty(executeTenant)) {
     * items.add("*** Waiting for Tenant to be specified ***",
     * "ERROR: Waiting for Tenant to be specified");
     * items.get(items.size() - 1).selected = true;
     * return items;
     * }
     */
    System.out.println("\n--------------------fillItems ----------------------------\n");

    ConfigureAndAuth authResult = Authenticate(portalUrl, credentials);
    if (!authResult.Error) {
      try {
        // String tenantId = authResult.MatchingTenantId(executeTenant);
        // String tenantId = authResult.FirstTenant().TenantId;
        // HashSet<CTMSuite> suitesForTenant = authResult.Server.suites(tenantId);

        List<CTMSuite> suites = authResult.Server.suitesForAllTenants();
        if (suites != null) {
          try {
            items.add("-- Select a CTM Suite --"); // Add blank entry first

            // Lookup all the Suites defined on the CTM and find the one specified
            // by the user
            for (CTMSuite suite : suites) {
              String name = suite.Tenant.TenantName + " / " + suite.SuiteName;
              items.add(name, name);
            }
          } catch (Exception ignored) {
            // Bad JSON
            items.add("*** ERROR ***", "ERROR: Bad JSON");
            items.get(items.size() - 1).selected = true;
          }
        } else {
          log.log(Level.WARNING, "Error retrieving list of suites - ", "");
          items.add("*** ERROR ***", "ERROR: Couldn't retrieve Suite(s) from CTM (or none for tenant)");
          items.get(items.size() - 1).selected = true;
        }

      } catch (Exception ex) {
        log.log(Level.WARNING, "Error retrieving list of suites - " + ex.getMessage(), "");
        items.add("*** ERROR ***", "ERROR: Couldn't retrieve Suite(s) from CTM " + ex.getMessage());
        items.get(items.size() - 1).selected = true;
      }
    } else {
      items.add(authResult.DisplayErrorMessage, authResult.ErrorMessage);
      items.get(items.size() - 1).selected = true;
    }

    CTMItemCache.updateItemsCache("request", items);

    return items;
  }

  // New Method to filll the items inside dropdown
  public static ListBoxModel fillSuiteItems(String emRequestType, String executeTenant, String portalUrl,
      String credentials) {
    ListBoxModel items = new ListBoxModel();

    log.log(Level.WARNING, "Inside fillSuiteItems Method...", "");
    ConfigureAndAuth authResult = Authenticate(portalUrl, credentials);

    // New Code
    if (!authResult.Error) {
      try {
        log.log(Level.WARNING, "Add blank entry first", "");
        items.add("-- Select a CTM Suite --"); // Add blank entry first

        log.log(Level.WARNING, "Calling getSuitesForAllTenants from CTMServer class", "");
        items = authResult.Server.getSuitesForAllTenants(items);

      } catch (Exception ex) {
        log.log(Level.SEVERE, "Error retrieving list of suites - " + ex.getMessage());
        log.severe("Error retrieving list of suites - " + ex.getMessage());
        items.add("*** ERROR ***", "ERROR: Couldn't retrieve Suite(s) from CTM " + ex.getMessage());
        items.get(items.size() - 1).selected = true;
      }
    }

    CTMItemCache.updateItemsCache("request", items);

    return items;
  }

  // Process the user provided parameters by substituting Jenkins environment
  // variables referenced in a parameter's value.

  // private HashMap<String, String> processParameters () throws
  // InterruptedException, IOException {
  private List<ExecuteRequestParameter> processParameters(JobDetails details) throws InterruptedException, IOException {
    HashMap<String, String> ret = new HashMap<String, String>();
    ExecuteRequestParameter _executeRequestParameter = null;
    List<ExecuteRequestParameter> _paramList = new ArrayList<>();
    // EnvVars envVars = run.getEnvironment(listener);
    EnvVars envVars = details.run.getEnvironment(details.listener);
    if (execParams != null && execParams.getList() != null) {
      // StringBuilder expandedValue = new StringBuilder();
      for (ExecuteRequestParameter param : execParams.getList()) {
        String key = param.getKey();
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
          _executeRequestParameter = new ExecuteRequestParameter();
          _executeRequestParameter.key = key;
          _executeRequestParameter.value = expandedValue.toString();
          _paramList.add(_executeRequestParameter);
          ret.put(param.getKey(), expandedValue.toString());
        }
      }
    }

    return _paramList;
  }

  private void reportVerboseProcessInformation(ProcessAutomatedExecutionModel[] processes, JobDetails details) {
    if (processes == null
        || processes.length == 0)
      return;

    try {
      String logHeaderID = "";
      // Print the run's status to the build console
      details.consoleOut.println(
          "Name             Status              Log Header ID              Resource                            Last Error");
      details.consoleOut.println(
          "----- -------------------------- ------------------ ----------------------------------- -----------------------------------");
      for (int i = 0; i < processes.length; i++) {
        ProcessAutomatedExecutionModel p = processes[i];

        String name = p.CertifyProcessName;
        String executionStatus = p.CertifyResult;
        String resourceName = p.MachineId;
        String lastReportedError = p.ErrorMessage;
        logHeaderID = p.LogHeaderId;

        details.consoleOut.println(name + ":");
        details.consoleOut.println(String.format("      %-26.26s %-18s %35s %s",
            StringUtils.abbreviate(executionStatus, 26),
            StringUtils.abbreviate(logHeaderID, 18),
            StringUtils.abbreviate(resourceName, 35),
            lastReportedError));

        details.consoleOut.println("Log Header ID =  " + logHeaderID);
      }
    } catch (Exception ex) {
      details.consoleOut.println(ex.getMessage());
      details.consoleOut.println(ex.getStackTrace());
    }
  }

  private boolean MarkBuildStatusPassed(CTMExecutionResult lastResultInfo, JobDetails details) {
    boolean passed = false;
    if (lastResultInfo.ErrorMessage != null
        && StringUtils.isNotEmpty(lastResultInfo.ErrorMessage)) {
      passed = false;
      details.consoleOut
          .println("Automation Error acknowledged, marking Jenkins build as failed - " + lastResultInfo.ErrorMessage);
    } else if (StringUtils.isNotEmpty(lastResultInfo.Result)) {
      String result = lastResultInfo.Result.toUpperCase();
      if (result.contains("PASS")
          || result.contains("SUCCESS")) {
        passed = true;
      }
    } else {
      passed = false; // inconclusive
      details.consoleOut.println(
          "Not enough information in CTM response to indicate result information, marking Jenkins build as failed");
    }
    return passed;
  }

  private void OnExecutionComplete(CTMExecutionResult lastResultInfo, boolean aborted, String automationGuid,
      JobDetails details) {
    System.out.println("\nOnExecutionComplete - " + automationGuid);
    if (aborted) {
      // Tell the CTM to abort execution
      // TODO: implement this feature
      // CTMResult result = server.executionAbort(guid);
      // if (!result.is200() && !aborted) {
      // consoleOut.println("\n*** ERROR: Error aborting execution:");
      // consoleOut.printlnIndented("*** ERROR: ", result.dumpDebug());
      // }
    }

    if (lastResultInfo != null) {
      this.reportVerboseProcessInformation(lastResultInfo.CompletedExecutions, details);
      if (!aborted) {
        if (this.MarkBuildStatusPassed(lastResultInfo, details)) {
          details.run.setResult(Result.SUCCESS);
        } else {
          details.run.setResult(Result.FAILURE);
        }
      }
    } else {
      details.run.setResult(Result.FAILURE);
    }

    // Write the response JSON to a file so that it can be processed further by the
    // Jenkins job
    try {
      FilePath resFile = new FilePath(details.workspace.getChannel(), details.workspace + "/execMan-result.json");
      // File resFile = new File(workspace + "/execMan-result.json");
      if (lastResultInfo != null
          && StringUtils.isNotEmpty(lastResultInfo.FullResponse)) {
        resFile.write(lastResultInfo.FullResponse, null);
        // FileUtils.writeStringToFile(resFile, response.toString());
        details.consoleOut.println("\nResults written to " + resFile);
      }
    } catch (Exception e) {
      details.consoleOut.println("*** ERROR: in FilePath: " + details.workspace + "/execMan-result.json");
      details.consoleOut.println("\n*** ERROR: unexpected error while writing results");
      details.consoleOut.println("*** ERROR: exception: " + e);
      details.consoleOut.println("*** ERROR: exception: " + e.getMessage());
      details.consoleOut.println("*** ERROR: stack trace:  ");
      details.consoleOut.printlnIndented("*** ERROR:    ", e.getStackTrace());
    }

  }

  private void waitForCompletion(String guid, ConfigureAndAuth authResult, JobDetails details) {
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
      pollInterval = TimeUnit.MILLISECONDS.convert(20L, TimeUnit.SECONDS);
    }

    // Stuff for computing elapsed time
    long startTime = System.currentTimeMillis();
    long currentTime = startTime;
    long endTime = (startTime + maxRunTime);
    SimpleDateFormat elapsedFmt = new SimpleDateFormat("HH:mm:ss.SSS");
    elapsedFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
    String elapsedTime = elapsedFmt.format(new Date(currentTime - startTime));

    // loop until complete/aborted
    // consoleOut.println("Waiting for execution to complete...");
    details.consoleOut.println(details.run.number + ":  Waiting for execution to complete (" + guid + ")...");
    CTMExecutionResult lastResultInfo = null;
    int qtyApiFailures = 10;
    while (true) {
      try {
        Thread.sleep(pollInterval);
        currentTime = System.currentTimeMillis();
        elapsedTime = elapsedFmt.format(new Date(currentTime - startTime));
        if (maxRunTime != null && currentTime >= endTime) {
          if (aborted) {
            // We get here when it's taken too long for the EM to abort execution, so
            // we're abandoning our wait.
            details.consoleOut.println("\n*** ERROR: Abort timed out!!! - abandoning...");
            abortReason += " (abandoned!)";
            details.consoleOut.println("Abort Reason : -- " + abortReason);
          } else {
            details.consoleOut.println("\n*** ERROR: Execution timed out after " + elapsedTime + " - aborting...");

            aborted = true;
            abortReason = " due to max wait time exceeded";
            details.run.setResult(Result.ABORTED);
          }
          break;
        }

        CTMExecutionResult statusResult = authResult.Server.executionStatus(guid);
        if (statusResult == null
            || statusResult.ApiFailed) {
          qtyApiFailures--;
          if (qtyApiFailures < 0) {
            details.consoleOut.println("\n*** ERROR: CTM error while checking execution status:");
            details.consoleOut.printlnIndented("*** ERROR:   ", statusResult.ApiFailure);
            break;
          }
        } else {
          lastResultInfo = statusResult;
          details.consoleOut.println("\nElapsed time=" + elapsedTime + " - " +
              statusResult.MetricProgressInfo()
              + (aborted ? " *** ABORTING ***" : ""));
          if (!statusResult.StillExecuting()) {
            break;
          }
        }
      } catch (InterruptedException e) {
        if (!aborted) {
          details.consoleOut.println("\n*** ERROR: User requested abort of execution after " + elapsedTime);

          aborted = true;
          abortReason = " due to user request";

          details.run.setResult(Result.ABORTED);
          break;
        } else {
          // We'll get here if the user tries to abort an aborting execution, so flag it
          // as such and abandon our wait.
          details.consoleOut.println("\n*** ERROR: User requested abort of execution (again) after " + elapsedTime);
          aborted = true;
          abortReason += " (forced!)";
          break;
        }
      } catch (Exception ex) {
        details.consoleOut.printlnIndented("\n*** ERROR: CTM error :", ex.getMessage());
        details.consoleOut.printlnIndented("*** ERROR:   ", ex.getMessage());
        break;
      }
    }
    if (lastResultInfo == null
        && guid != null
        && StringUtils.isNotEmpty(guid)) {
      lastResultInfo = authResult.Server.executionStatus(guid);
    }
    this.OnExecutionComplete(lastResultInfo, aborted, guid, details);

    details.consoleOut.println("Execution Status: " + lastResultInfo.Result);
    String executionStatus = "";
    if (lastResultInfo.Result.equals("Passed")) {
      executionStatus = "SUCCESS";
    } else {
      executionStatus = "FAILURE";
    }
    details.consoleOut
        .println("\n\nExecution " + executionStatus + " after - " + elapsedTime + abortReason);
  }

  // This method is called by Jenkins to perform the build step. It sets up some
  // instance
  // variables, logs in to the EM and dispatches the execute to methods that
  // follow
  // using reflection.
  @Override
  @SuppressWarnings("UseSpecificCatch")
  public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
      @Nonnull TaskListener listener) throws InterruptedException, IOException {
    // Save perform parameters in instance variables for future reference.
    // this.run = run;
    // this.workspace = workspace;
    // this.launcher = launcher;
    // this.listener = listener;
    // this.consoleOut = new ConsoleStream(listener.getLogger());

    ConsoleStream consoleOut = new ConsoleStream(listener.getLogger());
    // Save perform parameters for future reference.
    JobDetails details = new JobDetails(run, workspace, launcher, listener, consoleOut);

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
        details.consoleOut.println("plugin version = " + globalConfig.ver());
        authResult = AuthenticateOnly(ctmConfig);

        if (authResult == null || authResult.Error) {
          details.consoleOut.println("Authentication result - Error: " + authResult.Error
              + " | ErrorMessage: " + authResult.ErrorMessage);
          return;
        } else {
          details.consoleOut.println("Authentication successful");
          details.consoleOut.println("\n---------------- begin execute suite--------\n");
          guid = this.execute_REQUEST(authResult, details);
        }
      } else {
        throw new RuntimeException("No CTM configuration within Jenkins");
      }
    } catch (Exception ex) {
      details.consoleOut.println("\n*** ERROR: Unexpected error while processing request type: " + requestType);
      details.consoleOut.println("*** ERROR: exception: " + ex);
      details.consoleOut.println("*** ERROR: exception: " + ex.getMessage());
      details.consoleOut.println("*** ERROR: stack trace:  ");
      details.consoleOut.printlnIndented("*** ERROR:   ", ex.getStackTrace());

      details.run.setResult(Result.FAILURE); // Fail this build step.
    }
    if (run.getResult() != Result.FAILURE) {
      if (guid == null
          || StringUtils.isEmpty(guid)) {
        details.consoleOut.println("\n*** ERROR: An unexpected error occurred while requesting execution!");
        details.run.setResult(Result.FAILURE); // Fail this build step.
      } else {
        waitForCompletion(guid, authResult, details);
      }
    }
  }

  // Called via reflection from the dispatcher above to execute a 'request'
  private WorksoftTenant FirstTenantFromServer(ConfigureAndAuth authResult) {
    WorksoftTenant firstTenant = null;
    HashSet<WorksoftTenant> allTenants = authResult.Server.Tenants();
    if (allTenants == null
        || allTenants.size() <= 0)
      throw new RuntimeException("No tenants in cache to identify first");
    for (WorksoftTenant t : allTenants) {
      firstTenant = t;
      break;
    }
    return firstTenant;
  }

  private WorksoftTenant MatchingTenantFromServer(ConfigureAndAuth authResult, String tenantName) {
    WorksoftTenant match = null;
    HashSet<WorksoftTenant> allTenants = authResult.Server.Tenants();
    if (allTenants == null
        || allTenants.size() <= 0) {
      log.log(Level.WARNING, "ERROR-No tenants in cache to identify first", "");
      throw new RuntimeException("No tenants in cache to identify first");
    }
    for (WorksoftTenant t : allTenants) {
      if (t.TenantName.equals(tenantName)) {
        match = t;
        break;
      }
    }
    return match;
  }

  private String MatchingSuiteIdentifier(String suiteName, String tenantId, ConfigureAndAuth authResult) {
    String suiteId = "";
    boolean found = false;

    HashSet<CTMSuite> suitesForTenant = authResult.Server.suites(tenantId, suiteName);
    if (suitesForTenant == null)
      throw new RuntimeException("No response for suites for tenant: " + tenantId);
    if (suitesForTenant.size() <= 0)
      throw new RuntimeException("No suites for tenant: " + tenantId);
    for (CTMSuite suite : suitesForTenant) {
      if (suite.SuiteName.equals(suiteName)) {
        suiteId = suite.SuiteId;
        found = true;
        break;
      }
    }
    if (!found)
      throw new RuntimeException("No Suite Named '" + suiteName + "' found for tenant, it may have been renamed.");

    return suiteId;
  }

  private TandS TenantAndSuite(String tenantAndSuiteName, JobDetails details) throws InterruptedException {
    details.consoleOut.println("Inside TenantAndSuite Method...");
    details.consoleOut.println("tenant And SuiteName ==> " + tenantAndSuiteName);
    if (!tenantAndSuiteName.contains("/")) {
      details.consoleOut.println("Condition ---> " + tenantAndSuiteName.contains("/"));
      details.consoleOut.println("Inside If Block");
      throw new RuntimeException("Expected delimiter of '/' for tenant and suite");
    }
    int i = tenantAndSuiteName.indexOf("/");
    TandS result = new TandS();
    result.TenantName = tenantAndSuiteName.substring(0, i).trim();
    result.SuiteName = tenantAndSuiteName.substring(i + 1).trim();
    details.consoleOut.println("Suite Name -> " + result.SuiteName + " | " + "Tenant Name -> " + result.TenantName);
    return result;
  }

  public String execute_REQUEST(ConfigureAndAuth authResult, JobDetails details)
      throws InterruptedException, IOException {
    details.consoleOut.println("\n---Wait for 10 seconds---\n");
    Thread.sleep(10000);
    System.out.println("\n-------------------execute_request\n");
    String guid = null;

    /*
     * if(StringUtils.isEmpty(executeTenant.getName())) {
     * consoleOut.println("\n*** ERROR: A tenant name or ID must be specified!");
     * run.setResult(Result.FAILURE); // Fail this build step.
     * }
     */
    if (StringUtils.isEmpty(request.getName())) {
      details.consoleOut.println("\n*** ERROR: A CTM suite name or ID must be specified!");
      details.run.setResult(Result.FAILURE); // Fail this build step.
    } else {
      details.consoleOut.println("Inside else block...");
      details.consoleOut.println("result ==> " + request.getName());
      TandS tenantAndSuitePair = TenantAndSuite(request.getName().trim(), details);
      String tenantName = tenantAndSuitePair.TenantName;
      String suiteName = tenantAndSuitePair.SuiteName;

      WorksoftTenant tenant = null;
      String tenantId = "";
      String suiteId = "";

      details.consoleOut.println("\n    Tenant: " + tenantName);
      details.consoleOut.println("\n    Suite: " + suiteName);

      try {
        tenant = MatchingTenantFromServer(authResult, tenantName);

        if (tenant == null) {
          log.log(Level.WARNING, "ERROR-Unable to find matching tenant. Tenant value = " + tenant, "");
          throw new RuntimeException("Unable to find matching tenant: " + tenantName);
        }
        tenantId = tenant.TenantId;
        details.consoleOut.println("\n   TenantId: " + tenantId);

        suiteId = MatchingSuiteIdentifier(suiteName, tenantId, authResult);

        details.consoleOut.println("Invoking execution of CTM Suite '" + suiteName + "'(id=" + suiteId + ")");
        details.consoleOut.println("   on Continuous Testing Manager @ " + ctmConfig.getUrl());
        details.consoleOut.println("\n");

        if (execParams == null) {
          guid = authResult.Server.executeSuite(suiteId);
          details.consoleOut.println("guid value from executeSuite method = " + guid);
        } else {
          guid = execute_RequestParamter(authResult, details, tenantId, tenantName, suiteId, suiteName);
          details.consoleOut.println("guid value from execute_RequestParamter method = " + guid);
        }

        if (guid == null || StringUtils.isEmpty(guid)) {
          log.log(Level.WARNING, "ERROR-No execution identifier returned, there was a failure in CTM. Guid value  = " + guid,
              "");
          throw new RuntimeException("No execution identifier returned, there was a failure in CTM");
        }

        details.consoleOut.println("\n    CTM Execution Result Identifier: " + guid);
      } catch (RuntimeException ex) {
        details.consoleOut.println("\n*** ERROR: (during execute suite) " + ex.getMessage());
        details.run.setResult(Result.FAILURE); // Fail this build step.
      }
    }
    return guid;
  }

  public String execute_RequestParamter(ConfigureAndAuth authResult, JobDetails details,
      String tenantId, String tenantName, String suiteId, String suiteName) {
    String guidId = null;
    try 
    {
        details.consoleOut.println("Inside execute_RequestParamter Method...");
        List<ExecuteRequestParameter> execParamsList = SanitizeExecutionParameter(authResult, details);
        if (execParamsList == null) 
        {
            details.consoleOut.println("List was empty");
            return guidId;
        }

      boolean SuiteWithSingleProcess = false;
      String processId = "";
      CTMProcess ctmProcess = null;
      Integer processIdCounter = 0;
      Map<String, CTMProcess> processMap = new HashMap<>();
      List<Integer> originalList = new ArrayList<>();
      details.consoleOut.println(" Result Attribute code started from here....");

      // Insert ProcessIds in to one list -> Suite may have one process or multiple
      // process
      for (ExecuteRequestParameter executeRequestParameter : execParamsList) {
        if (executeRequestParameter.key.equals("ProcessId")) {
          if (executeRequestParameter.value.isBlank() || executeRequestParameter.value.isEmpty()) {
            details.consoleOut.println("ProcessId was blank or empty.");
            return guidId;
          }
          executeRequestParameter.value = executeRequestParameter.value.replace(" ", "");
          originalList.add(Integer.parseInt(executeRequestParameter.value));
        }
      }

      // Get count of Original list
      int duplicateCount = originalList.size();

      // Create a HashSet from the original list
      Set<Integer> distinctSet = new HashSet<>(originalList);

      // Convert the Set back to a List if needed
      // Remove duplicate process ids if list was having
      List<Integer> distinctList = new ArrayList<>(distinctSet);

      int distinctCount = distinctList.size();

      /*
       * --> If suite was having one process then call
       * "ProcessResultAttributeSingleSuiteProcess" this method. This method will
       * handle one process with multiple result attributes
       * It means we are using here Map<String,String> attributeMap = new HashMap();
       * 
       * --> If suite was having more than one process then call
       * "ProcessResultAttributeWithMultipleSuiteProcess" this method. This method
       * will handle multiple process with multiple result attributes
       * // It means we are using here Map<String,Map<String,String>>
       * resultAttributeMap = new HashMap();
       * 
       * NOTE: Below code (968 - 1086) and above code (Line 911 - 929) should be
       * replace "Map<String, List<Map<String,String>>>" -- So in that way code can be
       * easily readable and maintainable
       */
      if (distinctCount > 1) {
        // This method will call when suite was having multiple process and multiple
        // results attribute
        processMap = ProcessResultAttributeWithMultipleSuiteProcess(execParamsList, details);
      } else {
        // This method will call when suite was having single process and multiple
        // results attribute
        SuiteWithSingleProcess = true;
        processMap = ProcessResultAttributeSingleSuiteProcess(execParamsList, details);
      }

      details.consoleOut.println(suiteId + ",---" + suiteName + ",---" + tenantId + ",---" + tenantName);
      for (CTMProcess processObj : processMap.values()) {
        if (processObj.GetProcessId().isBlank() || processObj.GetProcessId().isEmpty()) {
          processIdCounter = 0;
          details.consoleOut.println("Layout-->" + processObj.GetLayout());
        } else {
          ++processIdCounter;
        }
      }
      guidId = authResult.Server.executeSuite(suiteId, suiteName, tenantId, tenantName, processMap, details,
          processIdCounter, SuiteWithSingleProcess);
    } catch (IOException | InterruptedException ex) {
      details.consoleOut.println("\n*** ERROR: (during execute suite with execution parameter) " + ex.getMessage());
      details.run.setResult(Result.FAILURE);
    }
    return guidId;
  }

  public Map<String, CTMProcess> ProcessResultAttributeWithMultipleSuiteProcess(
      List<ExecuteRequestParameter> execParamsList, JobDetails details) {
    details.consoleOut.println("Inside ProcessResultAttributeWithMultipleSuiteProcess...");
    String processId = "";
    CTMProcess ctmProcess = null;
    Integer processIdCounter = 0;
    Map<String, CTMProcess> processMap = new HashMap<>();

    for (ExecuteRequestParameter executeRequestParameter : execParamsList) {
      if (executeRequestParameter.key.equals("ProcessId")) {
        processId = executeRequestParameter.value;
      }
      if (processId != null && !processMap.isEmpty() && processMap.containsKey(processId)) {
        ctmProcess = processMap.get(processId);
      } else {
        ctmProcess = new CTMProcess();
      }
      switch (executeRequestParameter.key) {
        case "ProcessId" -> {
          processId = executeRequestParameter.value;
          ctmProcess.SetProcessId(executeRequestParameter.value);
          processMap.put(processId, ctmProcess);
        }
        case "ProcessPath" -> ctmProcess.SetProcessPath(executeRequestParameter.value);
        case "Layout" -> ctmProcess.SetLayout(executeRequestParameter.value);
        case "Recordset" -> ctmProcess.SetRecordset(executeRequestParameter.value);
        case "RecordsetMode" -> ctmProcess.SetRecordsetMode(executeRequestParameter.value);
        case "MachineAttributes" -> ctmProcess.SetMachineAttributes(executeRequestParameter.value);
        default -> {
          // After MachineAttibutes any attribute would be added then it will be
          // considered as ResultAttribute (Source CSTR-1887)
          try {
            if (!(executeRequestParameter.key.equals("") || executeRequestParameter.value.equals(""))) {
              ctmProcess.SetAttributeName(executeRequestParameter.key); // Setter method for Attribute Map
              ctmProcess.SetAttributeValue(executeRequestParameter.value);
              details.consoleOut.println("Attribute Name - " + ctmProcess.GetAttributeName() + " | " +
                  "Attribute Value - " + ctmProcess.GetAttributeValue());
              ctmProcess.AddResultAttributes(ctmProcess.GetProcessId(), ctmProcess.GetAttributeName(),
                  ctmProcess.GetAttributeValue(), details);
              // passing Process Id,"key" and "value" pair to the attributeMap so it will
              // maintain many to many relationship
            } else {
              details.consoleOut.println("Inside Else Statement");
              continue;
            }
          } catch (Exception ex) {
            details.consoleOut.println("ERROR :- " + ex);
          }
        }
      }
    }

    return processMap;
  }

  public Map<String, CTMProcess> ProcessResultAttributeSingleSuiteProcess(List<ExecuteRequestParameter> execParamsList,
      JobDetails details) {
    details.consoleOut.println("Inside ProcessResultAttributeSingleSuiteProcess...");
    String processId = "";
    CTMProcess ctmProcess = null;
    Integer processIdCounter = 0;
    Map<String, CTMProcess> processMap = new HashMap<>();
    Map<String, String> attributeMap = new HashMap<>();

    for (ExecuteRequestParameter executeRequestParameter : execParamsList) {
      if (executeRequestParameter.key.equals("ProcessId")) {
        processId = executeRequestParameter.value;
      }
      if (processId != null && !processMap.isEmpty() && processMap.containsKey(processId)) {
        ctmProcess = processMap.get(processId);
      } else {
        ctmProcess = new CTMProcess();
      }
      switch (executeRequestParameter.key) {
        case "ProcessId" -> {
          processId = executeRequestParameter.value;
          ctmProcess.SetProcessId(executeRequestParameter.value);
          processMap.put(processId, ctmProcess);
        }
        case "ProcessPath" -> ctmProcess.SetProcessPath(executeRequestParameter.value);
        case "Layout" -> ctmProcess.SetLayout(executeRequestParameter.value);
        case "Recordset" -> ctmProcess.SetRecordset(executeRequestParameter.value);
        case "RecordsetMode" -> ctmProcess.SetRecordsetMode(executeRequestParameter.value);
        case "MachineAttributes" -> ctmProcess.SetMachineAttributes(executeRequestParameter.value);
        default -> {
          // After MachineAttibutes any attribute would be added then it will be
          // considered as ResultAttribute (Source CSTR-1887)
          try {
            if (!(executeRequestParameter.key.equals("") || executeRequestParameter.value.equals(""))) {
              details.consoleOut.println("Inside If Statement");
              ctmProcess.SetAttributeName(executeRequestParameter.key);
              ctmProcess.SetAttributeValue(executeRequestParameter.value);
              details.consoleOut.println("Attribute Name - " + ctmProcess.GetAttributeName() + " | " +
                  "Attribute Value - " + ctmProcess.GetAttributeValue());
              ctmProcess.AddAttributes(ctmProcess.GetAttributeName(), ctmProcess.GetAttributeValue());
              attributeMap.put(ctmProcess.GetAttributeName(), ctmProcess.GetAttributeValue());
              ctmProcess.SetAttributesMap(attributeMap);
              // passing "key" and "value" pair to the attributeMap so it will maintain one to
              // many relationship
            } else {
              details.consoleOut.println("Inside Else Statement");
              continue;
            }
          } catch (Exception ex) {
            details.consoleOut.println("ERROR :- " + ex);
          }
        }
      }
    }

    return processMap;
  }

  public List<ExecuteRequestParameter> SanitizeExecutionParameter(ConfigureAndAuth authResult, JobDetails details)
      throws InterruptedException, IOException {

    List<ExecuteRequestParameter> _paramList = new ArrayList<>();
    ExecuteRequestParameter _executeRequestParameter = null;
    try {
      _paramList = processParameters(details);

      for (ExecuteRequestParameter objectParam : _paramList) {
        String sanitizedValue = authResult.Server.sanitizeParameter(objectParam.value);

        String paramsKey = objectParam.key;
        String paramsValue = objectParam.value.trim();

        details.consoleOut.println("  " + paramsKey + "  " + paramsValue);

        _executeRequestParameter = new ExecuteRequestParameter();
        if (!objectParam.value.equals(sanitizedValue)) {
          paramsValue = sanitizedValue + " (sanitized from '" + paramsValue + "')";
        }
        _executeRequestParameter.key = paramsKey;
        _executeRequestParameter.value = paramsValue;

        if (_executeRequestParameter.key.equals("ProcessId"))
          _paramList.add(0, _executeRequestParameter);
        else
          _paramList.add(_executeRequestParameter);

      }
    } catch (Exception e) {
      details.consoleOut.println("ERROR--->" + e.getMessage());
    }

    return _paramList;
  }
}