/*
 * Copyright (c) 2022 Worksoft, Inc.
 *
 * CTMTenantAndSuite
 *
 * @author ggillman
 */

package com.worksoft.jenkinsci.plugins.ctm.model;

import net.sf.json.JSONObject;

public class CTMTenantAndSuite {
  public String SuiteId = "";
  public String SuiteName = "";
  public String TenantId = "";
  public String TenantName = "";
  public String RecordCount = "0";
 
  public CTMTenantAndSuite(JSONObject jsonSuite) {
    if(jsonSuite.containsKey("SuiteId")) {
      this.SuiteId = jsonSuite.getString("SuiteId");
    }
    if(jsonSuite.containsKey("SuiteName")) {
      this.SuiteName = jsonSuite.getString("SuiteName");
    }
    if(jsonSuite.containsKey("TenantId")) {
      this.TenantId = jsonSuite.getString("TenantId");
    }
    if(jsonSuite.containsKey("TenantName")) {
      this.TenantName = jsonSuite.getString("TenantName");
    }
    if (jsonSuite.containsKey("RecordCount")) {
      this.RecordCount = jsonSuite.getString("RecordCount");
    }
  }
}
