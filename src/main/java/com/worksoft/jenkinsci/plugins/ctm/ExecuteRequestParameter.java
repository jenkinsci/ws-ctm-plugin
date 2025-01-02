/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * ExecuteRequestParameter
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

public final class ExecuteRequestParameter extends AbstractDescribableImpl<ExecuteRequestParameter> {

  @Exported
  public String key;
  @Exported
  public String value;

  @DataBoundConstructor
  public ExecuteRequestParameter(String key, String value) {
    this.key = key;
    this.value = value;
  }

  public static ExecuteRequestParameter[] getSomeDefaults() {
    return new ExecuteRequestParameter[] { new ExecuteRequestParameter("valueA", "valueB") };
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  @Symbol("emParam")
  @Extension
  public static class DescriptorImpl extends Descriptor<ExecuteRequestParameter> {
    public String getDisplayName() {
      return "Execution Request Parameter";
    }

    public FormValidation doCheckKey(@QueryParameter String key) {
      FormValidation ret = FormValidation.ok();
      if (StringUtils.isEmpty(key)) {
        ret = FormValidation.error("A key must be specified!");
      }
      return ret;
    }

    public FormValidation doCheckValue(@QueryParameter String value) {
      FormValidation ret = FormValidation.ok();
      if (StringUtils.isEmpty(value)) {
        ret = FormValidation.error("A value must be specified!");
      }
      Integer valueLenth = value.length();
      System.out.println("value length--->" + valueLenth);
      if (valueLenth > 255) {
        ret = FormValidation.error("A value must can not be greater than 255 characters");
      }

      return ret;
    }
  }
}