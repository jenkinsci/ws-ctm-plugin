/*
 * Copyright (c) 2022 Worksoft, Inc.
 *
 * WorksoftProduct
 *
 * @author ggillman
 */

package com.worksoft.jenkinsci.plugins.ctm.model;

import net.sf.json.JSONObject;

public class WorksoftProduct {

  public int ProductId = 0;
  public String ProductName = "";
  public String BaseUrl = "";

  public WorksoftProduct(JSONObject json) {
    if(json.containsKey("ProductId")) {
      this.ProductId = json.getInt("ProductId");
    }
    if(json.containsKey("ProductName")) {
      this.ProductName = json.getString("ProductName");
      System.out.println("Product - " + this.ProductName);
    }
    if(json.containsKey("BaseUrl")) {
      this.BaseUrl = json.getString("BaseUrl");
    }

  }
}
