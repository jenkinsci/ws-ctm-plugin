/*
 * Copyright (c) 2021 Worksoft, Inc.
 *
 * ExecuteRequestCertifyProcessList
 *
 * @author rrinehart
 */

package com.worksoft.jenkinsci.plugins.ctm;


import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

import java.util.List;


public class ExecuteRequestCertifyProcessList extends AbstractDescribableImpl<ExecuteRequestCertifyProcessList> {

  @Exported
  String database;
  @Exported
  String project;
  @Exported
  String folder;
  @Exported
  String requestName;
  @Exported
  List<ExecuteCertifyProcess> processes;

    @DataBoundConstructor
    public ExecuteRequestCertifyProcessList (String database, String project, List<ExecuteCertifyProcess> processes, String folder, String requestName) {
      this.database = database;
      this.project = project;
      this.processes = processes;
      this.folder = folder;
      this.requestName = requestName;
    }

    public List<ExecuteCertifyProcess> getProcesses () {
      return processes;
    }

    public String getDatabase () {
      return database;
    }

    public String getProject () {
      return project;
    }

    public String getFolder () {
      return folder;
    }

    public String getRequestName () {
      return requestName;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ExecuteRequestCertifyProcessList> {
      public String getDisplayName () {
        return "Certify Process Config";
      }


      public FormValidation doFolder (@QueryParameter String folder) {
      FormValidation ret = FormValidation.ok();
      if (StringUtils.isEmpty(folder)) {
        ret = FormValidation.error("A folder must be specified!");
      }
      return ret;
    }

    public FormValidation doCheckDatabase (@QueryParameter String database) {
      FormValidation ret = FormValidation.ok();
      if (StringUtils.isEmpty(database)) {
        ret = FormValidation.error("A database alias must be specified!");
      }
      return ret;
    }

    public FormValidation doCheckProject (@QueryParameter String project) {
      FormValidation ret = FormValidation.ok();
      if (StringUtils.isEmpty(project)) {
        ret = FormValidation.error("A project must be specified!");
      }
      return ret;
    }

    public FormValidation doCheckRequestName (@QueryParameter String requestName) {
      FormValidation ret = FormValidation.ok();
      if (StringUtils.isEmpty(requestName)) {
        ret = FormValidation.error("A request name must be specified!");
      }
      return ret;
    }
  }

}
