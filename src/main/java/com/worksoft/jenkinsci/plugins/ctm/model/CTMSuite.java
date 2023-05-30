/*
 * Copyright (c) 2022 Worksoft, Inc.
 *
 * CTMSuite
 *
 * @author ggillman
 */

package com.worksoft.jenkinsci.plugins.ctm.model;

import net.sf.json.JSONObject;

public class CTMSuite {
  public String SuiteId = "";
  public String SuiteName = "";
  public String RecordCount = "0";
  public WorksoftTenant Tenant = null;

  public CTMSuite(JSONObject jsonSuite) {
    if(jsonSuite.containsKey("SuiteId")) {
      this.SuiteId = jsonSuite.getString("SuiteId");
    }
    if(jsonSuite.containsKey("SuiteName")) {
      this.SuiteName = jsonSuite.getString("SuiteName");
    }
    if (jsonSuite.containsKey("RecordCount")) {
      this.RecordCount = jsonSuite.getString("RecordCount");
    }
  }
}
