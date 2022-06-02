/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * ExecuteSuite
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

import javax.annotation.Nonnull;

public final class ExecuteSuite extends AbstractDescribableImpl<ExecuteSuite> {

  @Exported
  public String name;

  @DataBoundConstructor
  public ExecuteSuite (String name) {
    this.name = name;
  }

  public String getName () {
    return name;
  }

  @Symbol("request")
  @Extension
  public static class DescriptorImpl extends Descriptor<ExecuteSuite> {
    @Nonnull
    public String getDisplayName () {
      return "Execute Suite";
    }

    public FormValidation doCheckName (@QueryParameter String name) {
      ListBoxModel listBox = CTMItemCache.getCachedItems("suite");
      FormValidation ret = FormValidation.ok();

      String msg = name;
      if (msg.startsWith("ERROR")
              || (listBox != null && (msg = listBox.get(0).value).startsWith("ERROR"))
          ) {
        ret = FormValidation.error("CTM error retrieving suites - " + msg.replace("ERROR: ", "") + "!");
      } else if (StringUtils.isEmpty(name)) {
        ret = FormValidation.error("A CTM Suite must be specified!  (1st step is specifying tenant)");
      }

      return ret;
    }

    // Called whenever emRequestType or alternative CTM config changes
    public ListBoxModel doFillNameItems (@RelativePath("..") @QueryParameter String requestType,
                                            @RelativePath("..") @QueryParameter String executeTenant,
                                            @RelativePath("../altCTMConfig") @QueryParameter String url,
                                            @RelativePath("../altCTMConfig") @QueryParameter String credentials) {
      if(!Jenkins.get().hasPermission(Jenkins.READ)) {
        return new ListBoxModel();
      }
      System.out.println("\n---------------------------------------\ndofillnameItems for suites.......");
      return CTMExecute.fillItems("request", executeTenant, url, credentials);
    }
  }
}