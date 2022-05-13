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
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

import javax.annotation.Nonnull;

public final class ExecuteTenant extends AbstractDescribableImpl<ExecuteTenant> {

  @Exported
  public String name;

  @DataBoundConstructor
  public ExecuteTenant (String name) {
    this.name = name;
  }

  public String getName () {
    return name;
  }

  @Symbol("executeTenant")
  @Extension
  public static class DescriptorImpl extends Descriptor<ExecuteTenant> {
    @Nonnull
    public String getDisplayName () {
      return "Specify Tenant";
    }

    public FormValidation doCheckName (@QueryParameter String name) {
      ListBoxModel listBox = CTMItemCache.getCachedItems("executeTenant");
      FormValidation ret = FormValidation.ok();

      String msg = name;
      if (msg.startsWith("ERROR")
              || (listBox != null && (msg = listBox.get(0).value).startsWith("ERROR"))
      ) {
        ret = FormValidation.error("CTM error tenants - " + msg.replace("ERROR: ", "") + "!");
      } else if (StringUtils.isEmpty(name)) {
        ret = FormValidation.error("A tenant  must be specified!");
      }

      return ret;
    }

    // Called whenever emRequestType or alternative CTM config changes
    public ListBoxModel doFillNameItems (@RelativePath("..") @QueryParameter String requestType,
                                         @RelativePath("../altCTMConfig") @QueryParameter String url,
                                         @RelativePath("../altCTMConfig") @QueryParameter String credentials) {
      // if we are able to implement client-side validation then we can uncomment this and other features
      //return CTMExecute.tenantsForAuthenticatedUser(url, credentials);
      return null;
    }
  }
}