/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * CTMConfig
 *
 * @author rrinehart
 */

 package com.worksoft.jenkinsci.plugins.ctm.config;

 import org.kohsuke.stapler.QueryParameter;
 import org.kohsuke.stapler.StaplerRequest;

 import com.worksoft.jenkinsci.plugins.ctm.ExecuteRequestCTMConfig;

 import hudson.Extension;
 import hudson.util.FormValidation;
 import jenkins.model.GlobalConfiguration;
 import net.sf.json.JSONObject;
 
 @Extension
 public class CTMConfig extends GlobalConfiguration {
 
   public ExecuteRequestCTMConfig ctmConfig;
 
   public CTMConfig () {
     load();
   }
 
   public ExecuteRequestCTMConfig getCTMConfig () {
     return ctmConfig;
   }
 
   public void setCtmConfig (ExecuteRequestCTMConfig ctmConfig) {
     this.ctmConfig = ctmConfig;
   }
 
   /**
    * Checks if the provided values are valid.
    *
    * @param altConfig The alternate EM server config to validate
    *
    * @return true if config is valid, false otherwise
    */
   public FormValidation doValidate (@QueryParameter ExecuteRequestCTMConfig altConfig) {
       return FormValidation.ok("Success");
   }
 
   // when an administrator clicks on save - this is what will persist the url and credentials
   @Override
   public boolean configure (StaplerRequest req, JSONObject json) throws FormException {
 
     req.bindJSON(this, json.getJSONObject("ctm"));
     save();
     return true;
   }
   public String ver()
   {
     return getPlugin().getVersion();
   }
 }
 