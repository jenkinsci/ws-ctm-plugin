/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * ExecuteCertifyProcess
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


public class ExecuteCertifyProcess extends AbstractDescribableImpl<ExecuteCertifyProcess> {

  @Exported
  public String processPath;

  @DataBoundConstructor
  public ExecuteCertifyProcess (String processPath) {
    this.processPath = processPath;
  }

  public String getProcessPath () {
    return processPath;
  }

  @Symbol("certifyProcess")
  @Extension
  public static class DescriptorImpl extends Descriptor<ExecuteCertifyProcess> {
    public String getDisplayName () {
      return "Certify Process Path";
    }


    public FormValidation doCheckProcessPath (@QueryParameter String processPath) {
      FormValidation ret = FormValidation.ok();
      if (StringUtils.isEmpty(processPath)) {
        ret = FormValidation.error("A process path must be specified!");
      }
      return ret;
    }
  }
}
