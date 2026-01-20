/*
 * Copyright (c) 2025 Worksoft, Inc.
 *
 * CTMTenantAndSuite
 *
 * @author arana
 */

package com.worksoft.jenkinsci.plugins.ctm.model;

import com.worksoft.jenkinsci.plugins.ctm.CTMExecute.JobDetails;
import java.util.HashMap;
import java.util.Map;

public class CTMProcess {
    private String ProcessId = "";
    private String ProcessPath = "";
    private String Layout = "";
    private String Recordset = "";
    private String RecordsetMode = "";
    private String MachineAttributes = "";
    private String AttName = "";
    private String AttValue = "";

    private Map<String, String> AttributeMap = new HashMap<>();
    private Map<String, Map<String, String>> ResultAttributeMap = new HashMap<>();
    private Map<String, String> innerMap = new HashMap<>();

    public String GetProcessId() {
        return ProcessId;
    }

    public void SetProcessId(String _processId) {
        this.ProcessId = _processId;
    }

    public String GetProcessPath() {
        return ProcessPath;
    }

    public void SetProcessPath(String _processPath) {
        this.ProcessPath = _processPath;
    }

    public String GetLayout() {
        return Layout;
    }

    public void SetLayout(String _layout) {
        this.Layout = _layout;
    }

    public String GetRecordset() {
        return Recordset;
    }

    public void SetRecordset(String _recordset) {
        this.Recordset = _recordset;
    }

    public String GetRecordsetMode() {
        return RecordsetMode;
    }

    public void SetRecordsetMode(String _recordsetMode) {
        this.RecordsetMode = _recordsetMode;
    }

    public String GetMachineAttributes() {
        return MachineAttributes;
    }

    public void SetMachineAttributes(String _machineAttributes) {
        this.MachineAttributes = _machineAttributes;
    }

    public String GetAttributeName() {
        return AttName;
    }
    
    public void SetAttributeName(String _attname) {
        this.AttName = _attname;
    }

    public String GetAttributeValue() {
        return AttValue;
    }
    
    public void SetAttributeValue(String _attvalue) {
        this.AttValue = _attvalue;
    }

     // Getter
    public Map<String, String> GetAttributesMap() {
        return this.AttributeMap;
    }

     public Map<String, Map<String, String>> GetResultAttributesMap() {
        return this.ResultAttributeMap;
    }

    // Setter
    public void SetAttributesMap(Map<String, String> attributeMap) {
        this.AttributeMap = attributeMap;
    }

     public void SetResultAttributesMap(Map<String, Map<String, String>> resultAttributeMap) {
        this.ResultAttributeMap = resultAttributeMap;
    }

    // Add single process
    public void AddAttributes(String name, String value) {
        this.AttributeMap.put(name, value);
    }

    // Map ProcessId to Result Attributes
     public void AddResultAttributes(String processId,String name, String value,JobDetails details) {
       
        innerMap.put(name,value);
        details.consoleOut.println("process Id ==> " + processId);
        this.ResultAttributeMap.put(processId, innerMap);
        this.SetResultAttributesMap(this.ResultAttributeMap);
        details.consoleOut.println("Inner Key ---> " + name + " |  Inner Value ---> " + value);
        details.consoleOut.println("Result Attribute Map Count ---> " + this.ResultAttributeMap.size());
    }
}

