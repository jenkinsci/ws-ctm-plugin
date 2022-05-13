/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * Auth
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm.model;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
//import sun.util.resources.CalendarData_en;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;


public class Auth {

  private String _userId = "";
  private String _userName = "";
  private String _email = "";
  private String _firstName = "";
  private String _lastName = "";
  private Map<String, String> _tenants;
  private Map<Keys, String> token;

  Auth () {
    token = new HashMap<>();
    _tenants = new HashMap<>();
  }
    boolean save (JSONObject auth) {

    for (Keys key : Keys.values()) {
      String v = auth.getString(key.getKV());

      if (v != null) {
        token.put(key, v);
      }
    }
    return true;
  }
  private String RemoveSlash(String content) {
    while(content.contains("/")) {
      content = content.replace("/", "");
    }
    return content;
  }
  HashSet<WorksoftTenant> Tenants() {
    System.out.println("\n-------------------------\nAuth.Tenants");
    HashSet<WorksoftTenant> tenants = new HashSet<WorksoftTenant>();
    if(this._tenants != null) {
      System.out.println("\n there are tenants");
      for(Map.Entry<String, String> entry : this._tenants.entrySet()) {
        WorksoftTenant t = new WorksoftTenant();
        t.TenantId = entry.getKey();
        t.TenantName = entry.getValue();
        if(t.TenantName != null
         && StringUtils.isNotEmpty(t.TenantName)) {
          // removing slash because this is the delimiter we are using in the
          // dropdown because in the limited time we did not have the bandwidth
          // to find a solution to have 2 dropdowns of tenant and suite
          // to make it work with client-side validation
          t.TenantName = this.RemoveSlash(t.TenantName);
        }
        System.out.println("tenant: " + t.TenantName);
        tenants.add(t);
      }
    }
    return tenants;
  }
  void acknowledgeUserDetails(JSONObject jsonUserDetails) {
    System.out.println("------------------------");
    System.out.println("\n-----------------------------\nacknowledgeUserDetails: " + jsonUserDetails);
    this._userName = JsonValue(jsonUserDetails, "Username");
    this._userId = JsonValue(jsonUserDetails, "UserId");
    this._email = JsonValue(jsonUserDetails, "Email");
    this._firstName = JsonValue(jsonUserDetails, "FirstName");
    this._lastName = JsonValue(jsonUserDetails, "LastName");
    System.out.println("\n\n--------------------------");
    System.out.println("\nEmail: " + this._email);
    System.out.println("\nFirst: " + this._firstName);

    if(jsonUserDetails.containsKey("Tenants")) {
      System.out.println("tenants found");
      JSONArray array = jsonUserDetails.getJSONArray("Tenants");
      if(array != null) {
        for(int i = 0; i < array.size(); i++) {
          JSONObject j = array.getJSONObject(i);
          this.AcknowledgeTenant(j);
        }
      }
    }
  }
  private void AcknowledgeTenant(JSONObject jsonTenant) {
    String tenantID = JsonValue(jsonTenant, "TenantId");
    String tenantName = JsonValue(jsonTenant, "Name");
    this._tenants.put(tenantID, tenantName);
    System.out.println("\nTenant: " + tenantName);
  }
  private String JsonValue(JSONObject json, String key) {
    String z = "";
    if(json.containsKey(key)) {
      z = json.getString((key));
    }
    return z;
  }



  String getAccess_token () {
    return token.get(Keys.access_token);
  }

  String getToken_type () {
    return token.get(Keys.token_type);
  }
  String getExpires_in () {
    String v = "";
    if(token.containsKey("expires_in")) {
      v = token.get(Keys.expires_in);
    }
    return v;
  }
  String getExpires () {
    return this.getExpires_in();
  }

  boolean isLoggedIn () {
    return getAccess_token()  != null;
  }
  @Override
  public String toString () {
    System.out.println("\n Auth to String() ----------------------------------------");

    return "Auth{" +
            "access_token='" + getAccess_token()+ '\'' +
            ", token_type='" + getToken_type() + '\'' +
            ", expires=''" + getExpires()+ '\'' +
            ", expires_in='" + getExpires_in() + '\'' +
            //", issued='" + getIssued()+ '\'' +
            '}';
  }

  static enum Keys {
    access_token("access_token"),
    token_type("token_type"),
    //expires(".expires_in"), // expires
    expires_in("expires_in");
    //issued(".issued");

    final String kv;

    Keys (String key) {
      this.kv = key;
    }

    String getKV () {
      return this.kv;
    }

  }
}
