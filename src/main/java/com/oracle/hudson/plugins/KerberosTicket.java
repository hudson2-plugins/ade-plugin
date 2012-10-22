package com.oracle.hudson.plugins;

import java.io.Serializable;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class KerberosTicket implements Serializable {
	private static final long serialVersionUID = 1L;
	@Exported
	Boolean isValid = false;
	Boolean isValid() {
		return isValid;
	}
	public void setValid() {
		this.isValid = true;
	}
	@Override
	public String toString() {
		return (isValid?"valid":"not valid");
	}
}
