package com.worksoft.jenkinsci.plugins.ctm.model;

public class CTMProcess {
    private String ProcessId = "";
    private String ProcessPath = "";
    private String Layout = "";
    private String Recordset = "";
    private String RecordsetMode = "";
    private String MachineAttributes = "";

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
}
