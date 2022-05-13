/*
 * Copyright (c) 2022 Worksoft, Inc.
 *
 * ProcessAutomatedExecutionModel
 *
 * @author ggillman
 */

package com.worksoft.jenkinsci.plugins.ctm.model;

import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.util.Date;

public class ProcessAutomatedExecutionModel {
  public String CompletedExecutionId ;
  public String MachineId ;
  public String MachineCredentialsId ;
  public String SuiteId ;
  public String SuiteItemId ;
  public String SuiteExecutionResultId ;
  public int CertifyProcessId;
  public String CertifyProcessName;
  public String CertifyResult ; //": "passed",
  public String CertifyDatabaseId ;
  public String LogHeaderId ;
  public String CompletedExecutionResult ;
  public int CompletedExecutionResultTypeId ;
  public String ErrorMessage ;
  public String CreatedDate ;
  public String ExecutionCreatedDate ;
  public String CompletedDate ;
  public String Title ; // result title
  public String ResultsFolder;
  public String StartTime ;
  public String EndTime ;
  public String ElapsedTime ;
  public String ProcessCount ;
  public String TestStepAbortCount;
  public String TestStepCount ;
  public String TestStepFailedCount;
  public String TestStepPassedCount;
  public String TestStepSkippedCount;

  public ProcessAutomatedExecutionModel(JSONObject json) {
    this.CompletedExecutionId = this.StringVal(json, "CompletedExecutionId");
    this.MachineId = this.StringVal(json, "MachineId");
    this.MachineCredentialsId = this.StringVal(json, "MachineCredentialsId");
    this.SuiteId = this.StringVal(json, "SuiteId");
    this.SuiteItemId = this.StringVal(json, "SuiteItemId");
    this.SuiteExecutionResultId = this.StringVal(json, "SuiteExecutionResultId");
    if(json.containsKey("CertifyProcessId")) {
      this.CertifyProcessId = json.getInt("CertifyProcessId");
    }
    this.CertifyResult = this.StringVal(json, "CertifyResult");
    this.CertifyDatabaseId = this.StringVal(json, "CertifyDatabaseId");
    this.LogHeaderId = this.StringVal(json, "LogHeaderId");
    this.CompletedExecutionResult = this.StringVal(json, "CompletedExecutionResult");
    if(json.containsKey("CompletedExecutionResultTypeId")) {
      this.CompletedExecutionResultTypeId = json.getInt("CompletedExecutionResultTypeId");
    }
    this.CertifyProcessName = this.StringVal(json, "CertifyProcessName");
    this.ErrorMessage = this.StringVal(json, "ErrorMessage");
    this.CreatedDate = this.StringVal(json, "CreatedDate");
    this.ExecutionCreatedDate = this.StringVal(json, "ExecutionCreatedDate");
    this.CompletedDate = this.StringVal(json, "CompletedDate");
    this.Title = this.StringVal(json, "Title");
    this.ResultsFolder = this.StringVal(json, "ResultsFolder");
    this.StartTime = this.StringVal(json, "StartTime");
    this.EndTime = this.StringVal(json, "EndTime");
    this.ElapsedTime = this.StringVal(json, "ElapsedTime");
    this.ProcessCount = this.StringVal(json, "ProcessCount");
    this.TestStepAbortCount = this.StringVal(json, "TestStepAbortCount");
    this.TestStepCount = this.StringVal(json, "TestStepCount");
    this.TestStepFailedCount = this.StringVal(json, "TestStepFailedCount");
    this.TestStepPassedCount = this.StringVal(json, "TestStepPassedCount");
    this.TestStepSkippedCount = this.StringVal(json, "TestStepSkippedCount");
  }
  private String StringVal(JSONObject json, String key) {
    String z = "";
    if(json.containsKey(key)) {
      z = json.getString(key);
      if(z != null
        && StringUtils.isNotEmpty(z)
        && z.equals("null")) {
        z = "";
      }
    }
    return z;
  }
}
