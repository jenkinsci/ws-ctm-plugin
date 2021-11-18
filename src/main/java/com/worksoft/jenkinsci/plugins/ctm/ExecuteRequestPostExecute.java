/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * ExecuteRequestPostExecute
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ComboBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import javax.annotation.Nonnull;

public final class ExecuteRequestPostExecute extends AbstractDescribableImpl<ExecuteRequestPostExecute> {
  @Exported
  public String action;
  @Exported
  public String params;

  @DataBoundConstructor
  public ExecuteRequestPostExecute (String action, String params) {
    this.action = action;
    this.params = params;
  }

  public String getAction () {
    return action;
  }

  public String getParams () {
    return params;
  }

  @Extension
  public static class DescriptorImpl extends Descriptor<ExecuteRequestPostExecute> {
    @Nonnull
    public String getDisplayName () {
      return "Post Execute Action";
    }

    public ComboBoxModel doFillActionItems () {
      return new ComboBoxModel("BPP Report");
    }
  }
}
