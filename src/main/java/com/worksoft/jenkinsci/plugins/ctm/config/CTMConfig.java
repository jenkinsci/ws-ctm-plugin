/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * CTMConfig
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm.config;

import com.worksoft.jenkinsci.plugins.ctm.ExecuteRequestCTMConfig;
import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

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

  @Override
  public boolean configure (StaplerRequest req, JSONObject json) throws FormException {
    req.bindJSON(this, json.getJSONObject("execution manager"));
    save();
    return true;
  }
}
