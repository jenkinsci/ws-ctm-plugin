/*
 * Copyright (c) 2022 Worksoft, Inc.
 *
 * ConfigureAndAuth
 *
 * @author ggillman
 */

package com.worksoft.jenkinsci.plugins.ctm.model;

import org.apache.commons.lang.StringUtils;

import java.util.HashSet;

public class ConfigureAndAuth {
  public CTMServer Server = null;
  public HashSet<WorksoftTenant> Tenants = null;
  public String DisplayErrorMessage = "";
  public String ErrorMessage = "";
  public boolean Error = false;

  public String MatchingTenantId(String tenantName) {
    String tenantID = "";

    for(WorksoftTenant t : Tenants) {
      if(t.TenantName.equals(tenantName)) {
        tenantID = t.TenantId;
        break;
      }
    }
    if(StringUtils.isEmpty(tenantID)) throw new RuntimeException("Failed to find matching tenant name for " + tenantName);
    return tenantID;
  }
  public WorksoftTenant FirstTenant() {
    WorksoftTenant firstTenant = null;
    for(WorksoftTenant t : Tenants) {
      firstTenant = t;
      break;
    }
    return firstTenant;
  }
}
