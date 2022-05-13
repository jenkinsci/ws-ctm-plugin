/*
 * Copyright (c) 2022 Worksoft, Inc.
 *
 * CTMExecutionResult
 *
 * @author ggillman
 */

package com.worksoft.jenkinsci.plugins.ctm.model;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.util.Date;

public class CTMExecutionResult {
  public String FullResponse = "";
  public String ExecutionResultId = "";
  public boolean ApiFailed = false;
  public String ApiFailure = "";
  public String SuiteId = "";
  public int SuiteExecutionResultStatusTypeId = 0;
  public String Result = "";  // Passed
  public String ErrorMessage = "";
  public String CreatedDate;
  public String StartedDate;
  public String CompletedDate;
  public ProcessAutomatedExecutionModel[] PendingExecutions;
  public ProcessAutomatedExecutionModel[] ActiveExecutions;
  public ProcessAutomatedExecutionModel[] CompletedExecutions;

  public CTMExecutionResult(JSONObject json) {
    if(json != null) {
      if (json.containsKey("SuiteId")) {
        this.SuiteId = json.getString("SuiteId");
      }
      if (json.containsKey("SuiteExecutionResultStatusTypeId")) {
        this.SuiteExecutionResultStatusTypeId = json.getInt("SuiteExecutionResultStatusTypeId");
      }
      if (json.containsKey("Result")) {
        this.Result = json.getString("Result");
      }
      if (json.containsKey("ErrorMessage")) {
        this.ErrorMessage = json.getString("ErrorMessage");
        if(this.ErrorMessage != null
           && this.ErrorMessage.equals("null")) {
          this.ErrorMessage = "";
        }
      }
      if (json.containsKey("CreatedDate")) {
        this.CreatedDate = json.getString("CreatedDate");
      }
      if (json.containsKey("StartedDate")) {
        this.StartedDate = json.getString("StartedDate");
      }
      if (json.containsKey("CompletedDate")) {
        this.CompletedDate = json.getString("CompletedDate");
      }
      this.PendingExecutions = this.ProcessDetails(json, "PendingExecutions");
      this.ActiveExecutions = this.ProcessDetails(json, "ActiveExecutions");
      this.CompletedExecutions = this.ProcessDetails(json, "CompletedExecutions");
    }
  }
  public boolean StillExecuting() {
    boolean executing = false;
    int qtyPending = 0;
    int qtyExecuting = 0;
    if(this.PendingExecutions != null) {
      qtyPending = this.PendingExecutions.length;
    }
    if(this.ActiveExecutions != null) {
      qtyExecuting = this.ActiveExecutions.length;
    }
    if(this.ErrorMessage != null
      && StringUtils.isNotEmpty(this.ErrorMessage)) {
      executing = false;
    }
    //else if(StringUtils.isNotEmpty(this.Result)) {
    //}
    else {
      if(qtyPending <= 0
        && qtyExecuting <= 0) {
        executing = false;
      } else {
        executing = true;
      }
    }
    return executing;
  }
  public String MetricProgressInfo() {
    String z = "";
    String err = "";
    if(!StringUtils.isEmpty(this.Result)) {
      z += this.Result + " - ";
    }
    int qtyPending = 0;
    int qtyExecuting = 0;
    int qtyCompleted = 0;
    if(this.PendingExecutions != null) {
      qtyPending = this.PendingExecutions.length;
    }
    if(this.ActiveExecutions != null) {
      qtyExecuting = this.ActiveExecutions.length;
    }
    if(this.CompletedExecutions != null) {
      qtyCompleted = this.CompletedExecutions.length;
    }
    if(this.ErrorMessage != null
      && StringUtils.isNotEmpty(this.ErrorMessage)) {
      err = "Error: " + this.ErrorMessage;
    }
    return z + qtyPending + " Pending, " +
            qtyExecuting + " Executing, " +
            qtyCompleted + " Completed" + " " +
            err;
  }
  private ProcessAutomatedExecutionModel[] ProcessDetails(JSONObject json, String key) {
    ProcessAutomatedExecutionModel[] processes = null;
    if(json.containsKey(key)) {
      JSONArray list = json.getJSONArray(key);
      if(list != null
        && list.size() > 0) {
        processes = new ProcessAutomatedExecutionModel[list.size()];
        for(int i = 0; i < list.size(); i++) {
          JSONObject jsonProcess = list.getJSONObject(i);
          if(jsonProcess != null) {
            ProcessAutomatedExecutionModel model = new ProcessAutomatedExecutionModel(jsonProcess);
            processes[i] = model;
          }
        }
      }
    }
    return processes;
  }

}
