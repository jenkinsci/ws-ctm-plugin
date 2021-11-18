/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * CTMExecute
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm;

import com.worksoft.jenkinsci.plugins.ctm.config.CTMConfig;
import com.worksoft.jenkinsci.plugins.ctm.model.CTMResult;
import com.worksoft.jenkinsci.plugins.ctm.model.CTMServer;
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
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
  private ExecuteBookmark bookmark;
  private ExecuteSuite request;
  private ExecuteRequestCertifyProcessList processList;
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
    return requestType;
  }

  public ExecuteBookmark getBookmark () {
    // When we get here Jenkins is loading our form values, so we can invalidate
    // this session's itemsCache.
    CTMItemCache.invalidateItemsCache();

    return bookmark;
  }

  public ExecuteSuite getRequest () {
    // When we get here Jenkins is loading our form values, so we can invalidate
    // this session's itemsCache.
    CTMItemCache.invalidateItemsCache();

    return request;
  }

  public ExecuteRequestCertifyProcessList getProcessList () {
    return processList;
  }

  @DataBoundSetter
  public void setRequestType (@Nonnull String requestType) {
    this.requestType = requestType;
  }

  @DataBoundSetter
  public void setBookmark (ExecuteBookmark bookmark) {
    this.bookmark = bookmark;
  }

  @DataBoundSetter
  public void setRequest (ExecuteSuite request) {
    this.request = request;
  }

  @DataBoundSetter
  public void setProcessList (ExecuteRequestCertifyProcessList processList) {
    this.processList = processList;
  }

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
      return "Run Execution Manager Request";
    }
  }

  // Used by doFillRequestItems and doFillBookmarkItems (See ExecuteRequestBookmark class)
  public static ListBoxModel fillItems (String emRequestType, String url, String credentials) {
    ListBoxModel items = new ListBoxModel();

    // Pick the right EM configuration
    CTMConfig globalConfig = GlobalConfiguration.all().get(CTMConfig.class);
    ExecuteRequestCTMConfig emConfig = globalConfig != null ? globalConfig.getCTMConfig() : null;
    ExecuteRequestCTMConfig altEMConfig = new ExecuteRequestCTMConfig(url, credentials);
    if (altEMConfig != null && altEMConfig.isValid()) {
      emConfig = altEMConfig;
    }

    if (emConfig != null) {
      CTMServer server = new CTMServer(emConfig.getUrl(), emConfig.lookupCredentials());
      try {
        if (server.login()) {
          JSONObject retrievedObjs;
          if (emRequestType.equals("request")) {
            retrievedObjs = server.requests();
          } else {
            retrievedObjs = server.bookmarks();
          }
          if (retrievedObjs != null) {
            try {
              items.add("-- Select a " + emRequestType + " --"); // Add blank entry first

              // Lookup all the requests defined on the EM and find the one specified
              // by the user
              JSONArray objs = retrievedObjs.getJSONArray("objects");
              for (int i = 0; i < objs.size(); i++) {
                JSONObject req = objs.getJSONObject(i);
                String name = req.getString("Name");
                items.add(name);
              }
            } catch (Exception ignored) {
              // Bad JSON
              items.add("*** ERROR ***", "ERROR: Bad JSON");
              items.get(items.size() - 1).selected = true;
            }
          } else {
            // couldn't get requests
            items.add("*** ERROR ***", "ERROR: Couldn't retrieve " + emRequestType + "s");
            items.get(items.size() - 1).selected = true;
          }
        } else {
          // Couldn't log in
          items.add("*** ERROR ***", "ERROR: Couldn't log in");
          items.get(items.size() - 1).selected = true;
        }
      } catch (Exception ex) {
        // Exception while logging in
        items.add("*** ERROR ***", "ERROR: Exception while logging in");
        items.get(items.size() - 1).selected = true;
      }
    } else {
      // No EM configuration
      items.add("*** ERROR ***", "ERROR: No EM configuration");
      items.get(items.size() - 1).selected = true;
    }

    CTMItemCache.updateItemsCache(emRequestType, items);

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

  private void waitForCompletion (String guid) {
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

    JSONArray prevTasks = null;
    JSONObject response = null;

    // loop until complete/aborted
    consoleOut.println("Waiting for execution to complete...");
    while (true) {
      CTMResult statusResult = server.executionStatus(guid);

      if (!statusResult.is200()) {
        consoleOut.println("\n*** ERROR: EM error while checking execution status:");
        consoleOut.printlnIndented("*** ERROR:   ", statusResult.dumpDebug());
        break;
      }

      response = statusResult.getJsonData();
      try {
        String jobStatus = response.getString("Status");
        String jobExecutionStatus = response.getString("ExecutionStatus");
        consoleOut.println("\nElapsed time=" + elapsedTime + " - " + jobStatus + "," + jobExecutionStatus + (aborted ? " *** ABORTING ***" : ""));
        if (response.containsKey("Tasks")) { // Check for "Tasks" before proceeding
          JSONArray tasks = response.getJSONArray("Tasks");
          if (prevTasks == null || !prevTasks.equals(tasks)) {
            // Print the run's status to the build console
            consoleOut.println("Name  Status                     Resource        Last Error");
            consoleOut.println("----- -------------------------- --------------- -----------------------------------");
            for (int i = 0; i < tasks.size(); i++) {
              JSONObject task = tasks.getJSONObject(i);
              String name = task.getString("Name");
              String executionStatus = task.getString("ExecutionStatus");
              String resourceName = task.getString("ResourceName");
              String lastReportedError = task.getString("LastReportedError");
              String status = task.getString("Status");
              if (StringUtils.isNotEmpty(status) && StringUtils.isNotEmpty(executionStatus)) {
                status += ",";
              }
              status += executionStatus;

              consoleOut.println(name + ":");
              consoleOut.println(String.format("      %-26.26s %-15.15s %s",
                      StringUtils.abbreviate(status, 26),
                      StringUtils.abbreviate(resourceName, 15),
                      lastReportedError));

              prevTasks = tasks;
            }
          }
        }

        // Check for completion
        if (jobStatus.toUpperCase().equals("COMPLETED")) {
          if (!aborted && jobExecutionStatus.toUpperCase().equals("FAILED")) {
            run.setResult(Result.FAILURE);
          } else if (jobExecutionStatus.toUpperCase().equals("PASSED")) {
            run.setResult(Result.SUCCESS);
          }
          break;
        }
      } catch (JSONException e) {
        // JSON badness
        consoleOut.println("\n*** ERROR: unexpected error while processing status");
        consoleOut.println("*** ERROR: exception: " + e);
        consoleOut.println("*** ERROR: exception: " + e.getMessage());
        consoleOut.println("*** ERROR: stack trace:  ");
        consoleOut.printlnIndented("*** ERROR:    ", e.getStackTrace());
      }
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
            break;
          } else {
            consoleOut.println("\n*** ERROR: Execution timed out after " + elapsedTime + " - aborting...");

            abortReason = " due to max wait time exceeded";

            CTMResult result = server.executionAbort(guid);
            if (!result.is200()) {
              consoleOut.println("\n*** ERROR: EM error aborting execution:");
              consoleOut.printlnIndented("*** ERROR:   ", result.dumpDebug());
            }

            run.setResult(Result.ABORTED);
          }
        }
      } catch (InterruptedException e) {
        if (!aborted) {
          consoleOut.println("\n*** ERROR: User requested abort of execution after " + elapsedTime);

          abortReason = " due to user request";

          run.setResult(Result.ABORTED);
        } else {
          // We'll get here if the user tries to abort an aborting execution, so flag it
          // as such and abandon our wait.
          consoleOut.println("\n*** ERROR: User requested abort of execution (again) after " + elapsedTime);
          abortReason += " (forced!)";
          break;
        }
      }
      if (run.getResult() == Result.ABORTED) {
        // Tell the EM to abort execution
        CTMResult result = server.executionAbort(guid);
        if (!result.is200() && !aborted) {
          consoleOut.println("\n*** ERROR: Error aborting execution:");
          consoleOut.printlnIndented("*** ERROR:   ", result.dumpDebug());
        }
        if (!aborted) {
          // Once we tell the EM to abort we'll wait for up to 120 seconds. We're reducing the
          // poll interval to give faster feedback to the user.
          pollInterval = TimeUnit.MILLISECONDS.convert(5L, TimeUnit.SECONDS);
          aborted = true;

          // Set the max run time to one minute from now in order to wait for EM to complete
          // the abort.
          maxRunTime = TimeUnit.MILLISECONDS.convert(120L, TimeUnit.SECONDS);
          endTime = (currentTime + maxRunTime);
        }
      }
    }

    // Write the response JSON to a file so that it can be processed further by the Jenkins job
    try {
      FilePath resFile = new FilePath(workspace.getChannel(), workspace + "/execMan-result.json");
      //File resFile = new File(workspace + "/execMan-result.json");
      if (response != null) {
        resFile.write(response.toString(), null);
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

    String guid = null;

    try {
      if (ctmConfig != null && ctmConfig.isValid()) {
        server = new CTMServer(ctmConfig.getUrl(), ctmConfig.lookupCredentials());
        if (server.login()) {
          // Dispatch to one of the methods below
          try {
            String methName = "execute_" + requestType.toUpperCase().trim();
            Method meth = this.getClass().getDeclaredMethod(methName);
            guid = (String) meth.invoke(this);
          } catch (NoSuchMethodException ex) {
            consoleOut.println("\n*** ERROR: Don't know how to execute '" + requestType + "'");
            run.setResult(Result.FAILURE); // Fail this build step.
          } catch (IllegalAccessException ex) {
            consoleOut.println("\n*** ERROR: Couldn't execute '" + requestType + "'");
            consoleOut.println("*** ERROR: unexpected error while processing request: " + requestType);
            consoleOut.println("*** ERROR: exception: " + ex);
            consoleOut.println("*** ERROR: exception: " + ex.getMessage());
            consoleOut.println("*** ERROR: stack trace:  ");
            consoleOut.printlnIndented("*** ERROR:   ", ex.getStackTrace());

            run.setResult(Result.FAILURE); // Fail this build step.
          } catch (InvocationTargetException ex) {
            consoleOut.println("*** ERROR: Exception thrown while executing '" + requestType + "'");
            run.setResult(Result.FAILURE); // Fail this build step.
          }
        } else {
          CTMResult result = server.getLastCTMResult();
          consoleOut.println("\n*** ERROR: Can't log in to '" + ctmConfig.getUrl() + "':");
          consoleOut.printlnIndented("*** ERROR:   ", result.getResponseData());
          run.setResult(Result.FAILURE); // Fail this build step.
        }
      } else {
        consoleOut.println("\n*** ERROR: A valid Execution Manager configuration must be specified!");
        run.setResult(Result.FAILURE); // Fail this build step.
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
      if (guid == null) {
        consoleOut.println("\n*** ERROR: An unexpected error occurred while requesting execution!");
        run.setResult(Result.FAILURE); // Fail this build step.
      } else {
        waitForCompletion(guid);
      }
    }
  }

  // Called via reflection from the dispatcher above to execute a 'request'
  public String execute_REQUEST () throws InterruptedException, IOException {
    String guid = null;
    if (StringUtils.isEmpty(request.getName())) {
      consoleOut.println("\n*** ERROR: A request name or ID must be specified!");
      run.setResult(Result.FAILURE); // Fail this build step.
    } else {
      String reqID = null;
      String theReq = request.getName().trim();
      JSONObject reqs;

      if ((reqs = server.requests()) != null) {
        try {
          // Lookup all the requests defined on the EM and find the one specified
          // by the user
          JSONArray objs = reqs.getJSONArray("objects");
          for (int i = 0; i < objs.size(); i++) {
            JSONObject req;
            if ((req = objs.getJSONObject(i)).getString("Name").equals(theReq) ||
                    req.getString("RequestID").equals(theReq)) {
              reqID = req.getString("RequestID");
              break;
            }
          }
        } catch (Exception ex) {
          consoleOut.println("\n*** ERROR: unexpected error during execute_REQUEST:");
          consoleOut.println("*** ERROR: unexpected error while processing request: " + requestType);
          consoleOut.println("*** ERROR: exception: " + ex);
          consoleOut.println("*** ERROR: exception: " + ex.getMessage());
          consoleOut.println("*** ERROR: stack trace:  ");
          consoleOut.printlnIndented("*** ERROR:   ", ex.getStackTrace());
          run.setResult(Result.FAILURE); // Fail this build step.
        }
      }
      if (reqID == null) {
        consoleOut.println("\n*** ERROR: No such request '" + theReq + "'!");
        run.setResult(Result.FAILURE); // Fail this build step.
      } else {
        consoleOut.println("Requesting execution of request '" + theReq + "'(id=" + reqID + ")");
        consoleOut.println("   on Execution Manager @ " + ctmConfig.getUrl());

        // Use TreeMap so that the keys are sorted
        Map<String, String> params = new TreeMap<String, String>(processParameters());
        if (params.keySet().size() > 0) {
          consoleOut.println("   with parameters (key=value):");
          for (String key : params.keySet()) {
            String value = params.get(key);
            String sanitizedValue = server.sanitizeParameter(value);
            if (!value.equals(sanitizedValue)) {
              value = sanitizedValue + " (sanitized from '" + value + "')";
            }
            consoleOut.println("      " + key + "=" + value);
          }
        }
        consoleOut.println("\n");
        guid = server.executeRequest(reqID, params);
        if (guid == null) {
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
        }
      }
    }
    return guid;
  }

  // Called via reflection from the dispatcher above to execute a 'bookmark'
  public String execute_BOOKMARK () throws InterruptedException, IOException {
    String guid = null;
    if (bookmark == null || StringUtils.isEmpty(bookmark.getName())) {
      consoleOut.println("\n*** ERROR: A bookmark name or ID must be specified!");
      run.setResult(Result.FAILURE); // Fail this build step.
    } else {
      String bmarkID = null;
      String theBmark = bookmark.getName().trim();
      JSONObject bmarks;

      if ((bmarks = server.bookmarks()) != null) {
        try {
          // Lookup all the bookmarks defined on the EM and find the one specified
          // by the user.
          JSONArray objs = bmarks.getJSONArray("objects");
          for (int i = 0; i < objs.size(); i++) {
            JSONObject bmark;
            if ((bmark = objs.getJSONObject(i)).getString("Name").equals(theBmark) ||
                    bmark.getString("Id").equals(theBmark)) {
              bmarkID = bmark.getString("Id");
              break;
            }
          }
        } catch (Exception ex) {
          consoleOut.println("\n*** ERROR: unexpected error during execute_REQUEST:");
          consoleOut.println("*** ERROR: unexpected error while processing request: " + requestType);
          consoleOut.println("*** ERROR: exception: " + ex);
          consoleOut.println("*** ERROR: exception: " + ex.getMessage());
          consoleOut.println("*** ERROR: stack trace:  ");
          consoleOut.printlnIndented("*** ERROR:   ", ex.getStackTrace());
          run.setResult(Result.FAILURE); // Fail this build step.
        }
      }
      if (bmarkID == null) {
        consoleOut.println("\n*** ERROR: No such bookmark '" + theBmark + "'!");
        run.setResult(Result.FAILURE); // Fail this build step.
      } else {
        consoleOut.println("Requesting execution of bookmark '" + theBmark + "'(id=" + bmarkID + ")");
        consoleOut.println("   on Execution Manager @ " + ctmConfig.getUrl());
        if (StringUtils.isNotEmpty(bookmark.getFolder())) {
          consoleOut.println("   with results folder='" + bookmark.getFolder() + "'");
        }

        // Use TreeMap so that the keys are sorted
        Map<String, String> params = new TreeMap<String, String>(processParameters());
        if (params.keySet().size() > 0) {
          consoleOut.println("   with parameters (key=value):");
          for (String key : params.keySet()) {
            String value = params.get(key);
            String sanitizedValue = server.sanitizeParameter(value);
            if (!value.equals(sanitizedValue)) {
              value = sanitizedValue + " (sanitized from '" + value + "')";
            }
            consoleOut.println("      " + key + "=" + value);
          }
        }
        consoleOut.println("\n");
        guid = server.executeBookmark(bmarkID, bookmark.getFolder(), params);
        if (guid == null) {
          CTMResult result = server.getLastCTMResult();
          String err = result.dumpDebug();
          if (result.getJsonData() != null) {
            try {
              err = result.getJsonData().getString("Message");
            } catch (Exception ignored) {
            }
          }
          consoleOut.println("\n*** ERROR: Request to execute bookmark failed:");
          consoleOut.printlnIndented("   ", err);
        }
      }
    }
    return guid;
  }

  // Called via reflection from the dispatcher above to execute a 'process list'
  private String execute_PROCESSLIST () throws InterruptedException, IOException {
    JSONObject processes = new JSONObject();
    JSONArray processList = new JSONArray();

    String guid = null;
    consoleOut.println("Requesting execution of Certify processes");
    consoleOut.println("   on Execution Manager @ " + ctmConfig.getUrl());
    consoleOut.println("   with database alias='" + getProcessList().getDatabase() + "'");
    processes.put("CertifyDatabaseAlias", getProcessList().getDatabase());
    consoleOut.println("   with project name='" + getProcessList().getProject() + "'");
    processes.put("ProjectName", getProcessList().getProject());
    consoleOut.println("   with request name='" + getProcessList().getRequestName() + "'");
    processes.put("RequestName", getProcessList().getRequestName());
    if (StringUtils.isNotEmpty(getProcessList().getFolder())) {
      consoleOut.println("   with results folder='" + getProcessList().getFolder() + "'");
      processes.put("ResultsFolder", getProcessList().getFolder());
    }
    consoleOut.println("   with process path(s):");
    for (ExecuteCertifyProcess proc : getProcessList().getProcesses()) {
      if (StringUtils.isNotEmpty(proc.getProcessPath())) {
        consoleOut.println("      " + proc.getProcessPath());
        processList.add(proc.getProcessPath());
      }
    }
    processes.put("Processes", processList);

    // Use TreeMap so that the keys are sorted
    Map<String, String> params = new TreeMap<String, String>(processParameters());
    if (params.keySet().size() > 0) {
      consoleOut.println("   with parameters (key=value):");
      for (String key : params.keySet()) {
        String value = params.get(key);
        String sanitizedValue = server.sanitizeParameter(value);
        if (!value.equals(sanitizedValue)) {
          value = sanitizedValue + " (sanitized from '" + value + "')";
        }
        consoleOut.println("      " + key + "=" + value);
      }
    }
    consoleOut.println("\n");

    //consoleOut.println(JSONUtils.valueToString(processes, 4, 0));

    guid = server.executeProcesses(processes, params);
    if (guid == null) {
      CTMResult result = server.getLastCTMResult();
      String err = result.dumpDebug();
      if (result.getJsonData() != null) {
        try {
          err = result.getJsonData().getString("Message");
        } catch (Exception ignored) {
        }
      }
      consoleOut.println("\n*** ERROR: Request to execute Certify process(es) failed:");
      consoleOut.printlnIndented("   ", err);
    }
    return guid;
  }
}
